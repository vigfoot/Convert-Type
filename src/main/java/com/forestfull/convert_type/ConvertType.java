package com.forestfull.convert_type;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Java Reflection과 Jackson을 결합한 고성능 하이브리드 타입 변환 라이브러리입니다.
 * <p>
 * 단순한 필드 복사를 넘어, JPA/Hibernate 엔티티의 지연 로딩(Lazy Loading) 제어와 복잡한 데이터 구조를 안전하고 스마트하게 변환합니다.
 *
 * <hr>
 * <p>
 * A high-performance hybrid type conversion library that combines Java Reflection and Jackson.
 * <p>
 * It goes beyond simple field copying to safely and intelligently handle lazy loading of JPA/Hibernate entities and complex data structures.
 *
 * @author vigfoot
 */
public class ConvertType {
    static class Cache {
        static class Clazz {
            private static final Map<Class<?>, List<Field>> FIELD_LIST = new ConcurrentHashMap<>();
            private static final Map<Class<?>, Map<String, Field>> FIELD_MAPS = new ConcurrentHashMap<>();
            private static final Map<Class<?>, Optional<Constructor<?>>> CONSTRUCTORS = new ConcurrentHashMap<>();
        }

        static class Handle {
            private static final Map<Field, MethodHandle> GETTERS = new ConcurrentHashMap<>();
            private static final Map<Field, MethodHandle> SETTERS = new ConcurrentHashMap<>();
        }

        static class Hibernate {
            private static final Class<?> PROXY_CLASS;
            private static final MethodHandle GET_LAZY_INITIALIZER;
            private static final MethodHandle IS_UNINITIALIZED;
            private static final MethodHandle INITIALIZE;
            private static final MethodHandle GET_IMPLEMENTATION;

            static {
                Class<?> proxy = null;
                MethodHandle getLazy = null, isUninit = null, init = null, getImpl = null;
                try {
                    proxy = Class.forName("org.hibernate.proxy.HibernateProxy");
                    Class<?> lazyInit = Class.forName("org.hibernate.proxy.LazyInitializer");

                    // 하이버네이트 로직도 MethodHandle로 미리 구워둠 (성능 핵심)
                    getLazy = LOOKUP.unreflect(proxy.getMethod("getHibernateLazyInitializer"));
                    isUninit = LOOKUP.unreflect(lazyInit.getMethod("isUninitialized"));
                    init = LOOKUP.unreflect(lazyInit.getMethod("initialize"));
                    getImpl = LOOKUP.unreflect(lazyInit.getMethod("getImplementation"));
                } catch (Throwable ignored) {
                }

                PROXY_CLASS = proxy;
                GET_LAZY_INITIALIZER = getLazy;
                IS_UNINITIALIZED = isUninit;
                INITIALIZE = init;
                GET_IMPLEMENTATION = getImpl;
            }
        }
    }

    public static final ObjectMapper jackson = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final int LIMIT_DEPTH = 50;


    /**
     * 변환을 시작하는 {@link ValueObject}를 생성합니다.
     * <p>
     * 이 메서드는 지연 로딩(Lazy Loading)된 프록시 객체를 강제로 초기화하여 모든 필드를 포함시킵니다.
     *
     * <hr>
     * <p>
     * Creates a {@link ValueObject} to start the conversion process.
     * <p>
     * This method forces the initialization of lazy-loaded proxy objects to include all fields.
     *
     * @param instance The source object to convert.
     * @return A {@link ValueObject} instance for full conversion.
     */
    public static <C> ValueObject<C> fromFull(C instance) {
        return new ValueObject<C>(instance, true);
    }

    /**
     * 변환을 시작하는 {@link ValueObject}를 생성합니다.
     * <p>
     * 이 메서드는 초기화되지 않은 지연 로딩(Lazy Loading) 프록시 객체를 {@code null}로 처리하여 {@code LazyInitializationException}을 방지합니다.
     *
     * <hr>
     * <p>
     * Creates a {@link ValueObject} to start the conversion process.
     * <p>
     * This method treats uninitialized lazy-loaded proxy objects as {@code null} to prevent {@code LazyInitializationException}.
     *
     * @param instance The source object to convert.
     * @return A {@link ValueObject} instance for default conversion.
     */
    public static <C> ValueObject<C> from(C instance) {
        return new ValueObject<C>(instance, false);
    }

    /**
     * 객체 변환 작업을 수행하는 내부 헬퍼 클래스입니다.
     * <p>
     * {@link ConvertType#from(Object)} 또는 {@link ConvertType#fromFull(Object)}를 통해 생성됩니다.
     *
     * <hr>
     * <p>
     * An inner helper class that performs object conversion tasks.
     * <p>
     * It is created via {@link ConvertType#from(Object)} or {@link ConvertType#fromFull(Object)}.
     */
    public static class ValueObject<C> {
        private final C instance;
        private final boolean isFullSearchHibernate;

        protected ValueObject(C instance, boolean isFullSearchHibernate) {
            this.instance = instance;
            this.isFullSearchHibernate = isFullSearchHibernate;
        }

        private static List<Field> getCachedFieldList(Class<?> clazz) {
            return Cache.Clazz.FIELD_LIST.computeIfAbsent(clazz, k -> {
                List<Field> fields = new ArrayList<>();
                Class<?> current = k;
                Set<String> names = new HashSet<>();
                while (current != null && current != Object.class) {
                    for (Field field : current.getDeclaredFields()) {
                        if (names.add(field.getName())) {
                            try {
                                // JDK 9+ 모듈 시스템 대응: 접근 불가능한 필드는 건너뜀
                                field.setAccessible(true);
                                fields.add(field);
                            } catch (Throwable ignored) {
                                // InaccessibleObjectException 등 발생 시 해당 필드는 매핑에서 제외
                                System.err.println("[ConvertType] Warning: Could not access field '" + field.getName() + "' in class '" + current.getName() + "'. It will be ignored.");
                            }
                        }
                    }
                    current = current.getSuperclass();
                }
                return fields;
            });
        }

        private static Map<String, Field> getCachedFieldMap(Class<?> clazz) {
            return Cache.Clazz.FIELD_MAPS.computeIfAbsent(clazz, k -> {
                Map<String, Field> map = new HashMap<>();
                for (Field field : getCachedFieldList(k)) {
                    map.put(field.getName(), field);
                }
                return map;
            });
        }

        private static Constructor<?> getCachedConstructor(Class<?> clazz) {
            return Cache.Clazz.CONSTRUCTORS.computeIfAbsent(clazz, k -> {
                try {
                    Constructor<?> c = k.getDeclaredConstructor();
                    try {
                        c.setAccessible(true);
                    } catch (Throwable ignored) {
                        // 생성자 접근 불가 시 Jackson 위임 등을 위해 null 반환 가능성 열어둠
                        System.err.println("[ConvertType] Warning: Could not access constructor for '" + clazz.getName() + "'.");
                        return Optional.empty();
                    }
                    return Optional.of(c);
                } catch (NoSuchMethodException e) {
                    return Optional.empty();
                }
            }).orElse(null);
        }

        private Object unProxy(Object value) {
            if (value == null || Cache.Hibernate.PROXY_CLASS == null) return value;

            if (Cache.Hibernate.PROXY_CLASS.isAssignableFrom(value.getClass())) {
                try {
                    Object initializer = Cache.Hibernate.GET_LAZY_INITIALIZER.invoke(value);
                    boolean uninitialized = (boolean) Cache.Hibernate.IS_UNINITIALIZED.invoke(initializer);

                    if (uninitialized) {
                        if (!this.isFullSearchHibernate) return null;
                        Cache.Hibernate.INITIALIZE.invoke(initializer);
                    }
                    return Cache.Hibernate.GET_IMPLEMENTATION.invoke(initializer);
                } catch (Throwable t) {
                    System.err.println("[ConvertType] Failed to unProxy Hibernate object: " + t.getMessage());
                    return null;
                }
            }

            // Hibernate Collection handling
            String className = value.getClass().getName();
            if (className.startsWith("org.hibernate.collection")) {
                try {
                    Method wasInitialized = value.getClass().getMethod("wasInitialized");
                    boolean initialized = (boolean) wasInitialized.invoke(value);

                    if (!initialized) {
                        if (!this.isFullSearchHibernate) return null;
                        Method sizeMethod = value.getClass().getMethod("size");
                        sizeMethod.invoke(value);
                    }
                } catch (Exception ignored) {
                }
            }

            return value;
        }

        /**
         * 현재 보유한 객체(A)를 기반으로 새로운 객체를 생성한 후,
         * 인자로 받은 객체(B)의 필드 값 중 null이 아닌 값들을 덮어씌워 반환합니다.
         * <p>
         * <strong>중요:</strong> 이 메서드를 사용하려면 대상 클래스에 반드시 **인자 없는 기본 생성자(No-args constructor)**가 있어야 합니다.
         * 기본 생성자가 없으면 객체 복제에 실패하여 {@code null}을 반환할 수 있습니다.
         *
         * <p><strong>동작 방식:</strong>
         * <ol>
         *     <li>현재 객체(A)와 인자 객체(B)가 동일한 클래스인지 확인합니다. (다르면 {@code IllegalArgumentException} 발생)</li>
         *     <li>현재 객체(A)를 복제하여 새로운 인스턴스(C)를 생성합니다.</li>
         *     <li>인자 객체(B)의 필드를 순회하며, 값이 null이 아닌 경우에만 C의 해당 필드에 덮어씁니다.</li>
         *     <li>덮어쓰기가 완료된 새로운 객체(C)를 반환합니다.</li>
         * </ol>
         *
         * <p><strong>사용 예시:</strong>
         * <pre>{@code
         * class User {
         *     private String username;
         *     private String fullName;
         *
         *     public User(){}
         *
         *     public User(String username, String fullName){
         *         this.username = username;
         *         this.fullName = fullName;
         *     }
         * }
         *
         * User original = new User("user1", "Old Name");
         * User update = new User(null, "New Name"); // username은 null
         *
         * // original을 복제한 새 객체에 update의 필드 중 null이 아닌 값("New Name")만 덮어씀
         * User result = ConvertType.from(original).overwrite(update);
         *
         * // result.getUsername() -> "user1" (유지됨)
         * // result.getFullName() -> "New Name" (덮어써짐)
         * }</pre>
         *
         * <p><strong>주의사항:</strong>
         * <ul>
         *     <li>원본 객체 A와 인자 객체 B는 수정되지 않습니다. (불변성 유지)</li>
         *     <li>{@link Collection}, {@link Map} 등 컨테이너 타입은 지원하지 않으며, 커스텀 클래스(POJO)만 가능합니다.</li>
         * </ul>
         *
         * <hr>
         * <p>
         * Creates a new object based on the current object (A), then overwrites its fields
         * with non-null values from the given source object (B).
         * <p>
         * <strong>IMPORTANT:</strong> The target class must have a **no-arguments constructor** for this method to work.
         * If a no-args constructor is not available, object cloning will fail, and this method may return {@code null}.
         *
         * <p><strong>How it works:</strong>
         * <ol>
         *     <li>Checks if the current object (A) and the source object (B) are of the same class. (Throws {@code IllegalArgumentException} if not)</li>
         *     <li>Creates a new instance (C) by cloning the current object (A).</li>
         *     <li>Iterates through the fields of the source object (B) and overwrites the corresponding fields in C only if the value is not null.</li>
         *     <li>Returns the new object (C) with the overwritten values.</li>
         * </ol>
         *
         * <p><strong>Notes:</strong>
         * <ul>
         *     <li>The original object A and the source object B are not modified (immutability is maintained).</li>
         *     <li>Container types like {@link Collection} and {@link Map} are not supported; only custom classes (POJOs) are allowed.</li>
         * </ul>
         *
         * @param source The source object (B) containing the values to overwrite.
         * @param <T>    The type of the object.
         * @return A new object (C) with the merged values, or {@code null} on failure.
         * @throws IllegalArgumentException if the source and target objects are not of the same class.
         */
        @SuppressWarnings("unchecked")
        public <T> T overwrite(T source) {
            if (instance == null || source == null) return null;

            Class<?> clazz = instance.getClass();
            if (!clazz.equals(source.getClass())) {
                throw new IllegalArgumentException("Overwrite failed: Source and target must be of the same class. Target: " + clazz.getName() + ", Source: " + source.getClass().getName());
            }

            if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException("[ConvertType] Overwrite is not supported for Collection or Map types.");
            }

            // 1. 현재 객체(A)를 복제하여 새로운 인스턴스(C) 생성
            T newInstance = (T) to(clazz);
            if (newInstance == null) return null;

            // 2. 소스 객체(B)의 필드를 순회하며 null이 아닌 값 덮어쓰기
            List<Field> fields = getCachedFieldList(clazz);
            for (Field field : fields) {
                try {
                    Object sourceValue = unProxy(field.get(source));

                    if (sourceValue != null) {
                        field.set(newInstance, sourceValue);
                    }
                } catch (Exception e) {
                    System.err.println("[ConvertType] Failed to overwrite field: " + field.getName());
                }
            }

            return newInstance;
        }

        /**
         * 현재 보유한 객체 정보를 바탕으로 지정된 클래스 타입의 새로운 인스턴스를 생성하고 필드 값을 복사합니다.
         * <p>
         * <strong>중요:</strong> 이 메서드의 리플렉션 기반 변환을 안정적으로 사용하려면, 대상 클래스 {@code clazz}에
         * 반드시 **인자 없는 기본 생성자(No-args constructor)**가 있어야 합니다.
         * <p>
         * 기본 생성자가 없는 경우, 내부적으로 {@link ObjectMapper#convertValue(Object, Class)}를 호출하여
         * Jackson 라이브러리에 변환을 위임합니다. 이 경우, 대상 클래스에 public 필드나 public getter/setter가 없으면
         * 변환에 실패할 수 있습니다. ({@code FAIL_ON_EMPTY_BEANS} 에러 발생 가능)
         *
         * <hr>
         * <p>
         * Creates a new instance of the specified class and copies field values from the current object.
         * <p>
         * <strong>IMPORTANT:</strong> For stable reflection-based conversion, the target class {@code clazz}
         * must have a **no-arguments constructor**.
         * <p>
         * If a no-args constructor is not found, this method delegates the conversion to the Jackson library
         * by calling {@link ObjectMapper#convertValue(Object, Class)}. In this case, the conversion may fail
         * if the target class has no public fields or public getters/setters (potentially causing a {@code FAIL_ON_EMPTY_BEANS} error).
         *
         * @param clazz The target class to convert to (e.g., DTO, VO).
         * @param <T>   The type of the returned object.
         * @return A new instance of the target class with copied values, or {@code null} on failure.
         */
        public <T> T to(Class<T> clazz) {
            return to(clazz, null, LIMIT_DEPTH);
        }

        public <T> T to(Class<T> clazz, BiConsumer<C, T> peek) {
            return to(clazz, peek, LIMIT_DEPTH);
        }

        @SuppressWarnings("unchecked")
        private <T> T to(Class<T> clazz, BiConsumer<C, T> peek, int depth) {
            if (depth <= 0) {
                System.err.println("[ConvertType] Too many nested objects. Please check for circular references in your class: " + clazz.getName());
                return null;
            }

            if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
                System.err.println("[ConvertType] Direct conversion to Collection or Map is not supported. Please use a wrapper class or DTO. Target class: " + clazz.getName());
                return null;
            }

            depth--;
            T newInstance;

            try {
                // Delegate to Jackson for Interfaces, Abstracts, Java Time, and String
                if (clazz.isInterface() ||
                        java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()) ||
                        clazz.getName().startsWith("java.time") ||
                        clazz == String.class) { // String도 Jackson에게 위임
                    return jackson.convertValue(instance, clazz);
                } else {
                    Constructor<?> constructor = getCachedConstructor(clazz);
                    if (constructor != null) {
                        newInstance = (T) constructor.newInstance();
                    } else {
                        return jackson.convertValue(instance, clazz);
                    }
                }

                // 소스 객체가 Map인 경우와 일반 객체인 경우를 분리하여 처리
                boolean isSourceMap = instance instanceof Map;
                Map<String, Object> sourceMap = null;
                if (isSourceMap) {
                    // Unchecked cast for Map source
                    sourceMap = (Map<String, Object>) instance;
                }
                Map<String, Field> sourceFields = isSourceMap ? null : getCachedFieldMap(instance.getClass());

                // 타겟 클래스의 모든 필드를 순회하며 값을 채움
                for (Field targetField : getCachedFieldList(clazz)) {
                    // 1. @ConvertField(ignore = true) 체크
                    ConvertField annotation = targetField.getAnnotation(ConvertField.class);
                    if (annotation != null && annotation.ignore()) {
                        continue;
                    }

                    // 2. 매핑할 소스 필드명 결정 (mapping 지원)
                    String sourceFieldName = targetField.getName();
                    if (annotation != null && !annotation.mapping().isEmpty()) {
                        sourceFieldName = annotation.mapping();
                    }

                    // 3. 소스 값 가져오기
                    Object value = null;
                    if (isSourceMap) {
                        if (!sourceMap.containsKey(sourceFieldName)) continue;
                        value = sourceMap.get(sourceFieldName);
                    } else {
                        Field sourceField = sourceFields.get(sourceFieldName);
                        if (sourceField == null) continue; // 매핑되는 소스 필드가 없음
                        try {
                            value = sourceField.get(instance);
                        } catch (Exception e) {
                            System.err.println("[ConvertType] Failed to get value from source field: " + sourceFieldName);
                            continue;
                        }
                    }

                    // Hibernate Proxy 해제
                    value = unProxy(value);

                    // 4. 값 주입 (기본형 초기화 및 타입 변환)
                    final Class<?> fieldType = targetField.getType();

                    if (value == null) {
                        if (fieldType.isPrimitive()) {
                            if (fieldType == boolean.class) targetField.setBoolean(newInstance, false);
                            else if (fieldType == char.class) targetField.setChar(newInstance, '\u0000');
                            else targetField.set(newInstance, 0);
                        }
                        continue;
                    }

                    // Collection이나 Map인 경우, 무조건 새로운 인스턴스를 생성하여 깊은 복사(Deep Copy)를 수행하도록 순서 변경
                    if (value instanceof Iterable && (Collection.class.isAssignableFrom(fieldType) || fieldType.isArray())) {
                        // Collection 처리 로직
                        Iterable<?> sourceIterable = (Iterable<?>) value;
                        java.lang.reflect.Type genericType = targetField.getGenericType();

                        if (genericType instanceof java.lang.reflect.ParameterizedType) {
                            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
                            java.lang.reflect.Type[] typeArguments = pt.getActualTypeArguments();

                            if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                                Class<?> targetItemClass = (Class<?>) typeArguments[0];
                                Collection<Object> targetCol;

                                if (Set.class.isAssignableFrom(fieldType)) {
                                    targetCol = new LinkedHashSet<>();
                                } else if (List.class.isAssignableFrom(fieldType) || fieldType.isInterface()) {
                                    targetCol = new ArrayList<>();
                                } else {
                                    try {
                                        targetCol = (Collection<Object>) fieldType.getDeclaredConstructor().newInstance();
                                    } catch (Exception e) {
                                        targetCol = new ArrayList<>();
                                    }
                                }

                                for (Object item : sourceIterable) {
                                    if (item != null) {
                                        final Object valueObject = this.isFullSearchHibernate
                                                ? ConvertType.fromFull(item).to(targetItemClass, null, depth)
                                                : ConvertType.from(item).to(targetItemClass, null, depth);

                                        targetCol.add(valueObject);
                                    } else {
                                        targetCol.add(null);
                                    }
                                }

                                if (fieldType.isArray()) {
                                    Object array = java.lang.reflect.Array.newInstance(targetItemClass, targetCol.size());
                                    int index = 0;
                                    for (Object item : targetCol) {
                                        java.lang.reflect.Array.set(array, index++, item);
                                    }
                                    targetField.set(newInstance, array);
                                } else {
                                    targetField.set(newInstance, targetCol);
                                }
                            } else {
                                targetField.set(newInstance, jackson.convertValue(value, jackson.getTypeFactory().constructType(genericType)));
                            }
                        } else {
                            try {
                                targetField.set(newInstance, value);
                            } catch (IllegalArgumentException e) {
                                targetField.set(newInstance, jackson.convertValue(value, fieldType));
                            }
                        }
                    } else if (value instanceof Map && Map.class.isAssignableFrom(fieldType)) {
                        // Map 처리 로직
                        Map<?, ?> sourceMapVal = (Map<?, ?>) value;
                        Map<Object, Object> targetMap = new HashMap<>();
                        java.lang.reflect.Type genericType = targetField.getGenericType();

                        if (genericType instanceof java.lang.reflect.ParameterizedType) {
                            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
                            java.lang.reflect.Type[] typeArguments = pt.getActualTypeArguments();

                            if (typeArguments.length > 1 && typeArguments[1] instanceof Class) {
                                Class<?> valueClass = (Class<?>) typeArguments[1];
                                for (Map.Entry<?, ?> entry : sourceMapVal.entrySet()) {
                                    Object mapValue = entry.getValue();
                                    if (mapValue != null) {
                                        final Object valueObject = this.isFullSearchHibernate
                                                ? ConvertType.fromFull(mapValue).to(valueClass, null, depth)
                                                : ConvertType.from(mapValue).to(valueClass, null, depth);

                                        targetMap.put(entry.getKey(), valueObject);
                                    } else {
                                        targetMap.put(entry.getKey(), null);
                                    }
                                }
                                targetField.set(newInstance, targetMap);
                            } else {
                                targetField.set(newInstance, jackson.convertValue(value, jackson.getTypeFactory().constructType(genericType)));
                            }
                        } else {
                            try {
                                targetField.set(newInstance, value);
                            } catch (IllegalArgumentException e) {
                                targetField.set(newInstance, jackson.convertValue(value, fieldType));
                            }
                        }
                    } else if (fieldType.isAssignableFrom(value.getClass())) {
                        // 일반 객체나 단순 타입인 경우 그대로 할당
                        targetField.set(newInstance, value);
                    } else {
                        // 재귀 변환 시도
                        try {
                            final Object valueObject = this.isFullSearchHibernate
                                    ? ConvertType.fromFull(value).to(fieldType, null, depth)
                                    : ConvertType.from(value).to(fieldType, null, depth);

                            targetField.set(newInstance, valueObject);
                        } catch (Exception e) {
                            try {
                                targetField.set(newInstance, jackson.convertValue(value, fieldType));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                if (peek != null) peek.accept((C) instance, newInstance);

            } catch (Exception e) {
                System.err.println("[ConvertType] Error converting object to " + clazz.getName() + ": " + e.getMessage());
                return null;
            }

            return newInstance;
        }

        /**
         * 현재 객체를 {@link ConvertedMap}으로 변환합니다.
         * <p>
         * 현재 객체가 이미 {@link Map}이라면, 그 내용을 복사하여 새로운 {@link ConvertedMap}을 생성합니다.
         * 일반 객체라면, 리플렉션을 통해 필드 값을 추출하여 {@link ConvertedMap}으로 변환합니다.
         *
         * <hr>
         * <p>
         * Converts the current object to a {@link ConvertedMap}.
         * <p>
         * If the current object is already a {@link Map}, its contents are copied to a new {@link ConvertedMap}.
         * If it is a regular object, its field values are extracted via reflection to create a {@link ConvertedMap}.
         *
         * @return A {@link ConvertedMap} representation of the object.
         */
        public ConvertedMap toMap() {
            ConvertedMap map = new ConvertedMap();
            if (instance == null) return map;

            // Map인 경우 그대로 복사 (putAll 사용)
            if (instance instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sourceMap = (Map<String, Object>) instance;
                    map.putAll(sourceMap);
                } catch (Exception e) {
                    System.err.println("[ConvertType] Failed to copy Map content: " + e.getMessage());
                }
                return map;
            }

            // Collection인 경우 (List, Set 등) -> 리스트 형태로 반환할 수 없으므로,
            // "data"라는 키에 리스트를 담아서 반환하거나, 에러를 뱉어야 함.
            if (instance instanceof Collection) {
                System.err.println("[ConvertType] Warning: Collection type cannot be converted to Map directly. Returning empty map.");
                return map;
            }

            // 일반 객체인 경우 필드 순회
            for (Field field : getCachedFieldList(instance.getClass())) {
                ConvertField annotation = field.getAnnotation(ConvertField.class);
                if (annotation != null && annotation.ignore()) {
                    continue;
                }

                try {
                    Object value = unProxy(field.get(instance));
                    map.put(field.getName(), value);
                } catch (Exception e) {
                    System.err.println("[ConvertType] Failed to access field: " + field.getName());
                }
            }
            return map;
        }
    }
}