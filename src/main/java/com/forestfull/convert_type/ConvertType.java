package com.forestfull.convert_type;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConvertType 개체·객체 변환과 JSON 정보 보유가 가능한 기능을 자본적으로 지정하고 만들어진 라이브러리입니다.
 * <p>
 * ValueObject 가상 하여 여러 클래스 간의 변환과 보유가 가능\uud55c to(), toMap()과 JSON 인스턴스를 toJsonString() 방식으로 자동 합치할 수 있습니다.
 *
 * <p><strong>목적</strong>: 자바 리플렉션을 활용하여 클래스가 다른 객체로 자동 변환되고, 변환 결과가 JSON 형식으로 변경되고, 반환되게 만들어 줍니다.
 *
 * <p><strong>예시</strong>:
 * <pre>{@code
 * class A { int[] arr = {1, 2}; String s = "hi"; }
 * class B { int[] arr; String s; }
 *
 * B b = ConvertType.from(new A()).to(B.class);
 * System.out.println(Arrays.toString(b.arr)); // [1, 2]
 * System.out.println(b.s); // "hi"
 *
 * ConvertedMap map = ConvertType.from(new A()).toMap();
 * System.out.println(map.toJsonString());
 * // {
 * //   "arr": [1, 2],
 * //   "s": "hi"
 * // }
 * }</pre>
 *
 * @author vigfoot
 */
public class ConvertType {

    public static final ObjectMapper jackson = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Cache for class fields (List for iteration, Map for lookup)
    private static final Map<Class<?>, List<Field>> FIELD_LIST_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> FIELD_MAP_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    // Hibernate Reflection Cache
    private static final Class<?> HIBERNATE_PROXY_CLASS;
    private static final Method HIBERNATE_GET_LAZY_INITIALIZER;
    private static final Method HIBERNATE_IS_UNINITIALIZED;
    private static final Method HIBERNATE_INITIALIZE;
    private static final Method HIBERNATE_GET_IMPLEMENTATION;

    static {
        Class<?> proxyClass = null;
        Method getLazy = null;
        Method isUninit = null;
        Method init = null;
        Method getImpl = null;

        try {
            proxyClass = Class.forName("org.hibernate.proxy.HibernateProxy");
            Class<?> lazyInitializerClass = Class.forName("org.hibernate.proxy.LazyInitializer");
            getLazy = proxyClass.getMethod("getHibernateLazyInitializer");
            isUninit = lazyInitializerClass.getMethod("isUninitialized");
            init = lazyInitializerClass.getMethod("initialize");
            getImpl = lazyInitializerClass.getMethod("getImplementation");
        } catch (Throwable ignored) {
            proxyClass = null;
        }

        HIBERNATE_PROXY_CLASS = proxyClass;
        HIBERNATE_GET_LAZY_INITIALIZER = getLazy;
        HIBERNATE_IS_UNINITIALIZED = isUninit;
        HIBERNATE_INITIALIZE = init;
        HIBERNATE_GET_IMPLEMENTATION = getImpl;
    }

    /**
     * 여러 DTO 객체 각에 대해 ValueObject 바인드를 생성합니다.
     *
     * @param instance 변환 대상 객체
     * @return ValueObject 인스턴스
     */
    public static ValueObject fromFull(Object instance) {
        return new ValueObject(instance, true);
    }

    public static ValueObject from(Object instance) {
        return new ValueObject(instance, false);
    }

    /**
     * 자동 클래스 객체를 변환할 수 있는 ValueObject 클래스
     */
    public static class ValueObject {
        private static final int LIMIT_DEPTH = 50;
        private final Object instance;
        private final boolean isFullSearchHibernate;

        protected ValueObject(Object instance, boolean isFullSearchHibernate) {
            this.instance = instance;
            this.isFullSearchHibernate = isFullSearchHibernate;
        }

        private static List<Field> getCachedFieldList(Class<?> clazz) {
            return FIELD_LIST_CACHE.computeIfAbsent(clazz, k -> {
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
            return FIELD_MAP_CACHE.computeIfAbsent(clazz, k -> {
                Map<String, Field> map = new HashMap<>();
                for (Field field : getCachedFieldList(k)) {
                    map.put(field.getName(), field);
                }
                return map;
            });
        }

        private static Constructor<?> getCachedConstructor(Class<?> clazz) {
            return CONSTRUCTOR_CACHE.computeIfAbsent(clazz, k -> {
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
            if (value == null) return null;

            // Hibernate Proxy handling
            if (HIBERNATE_PROXY_CLASS != null && HIBERNATE_PROXY_CLASS.isAssignableFrom(value.getClass())) {
                try {
                    Object initializer = HIBERNATE_GET_LAZY_INITIALIZER.invoke(value);
                    boolean uninitialized = (boolean) HIBERNATE_IS_UNINITIALIZED.invoke(initializer);

                    if (uninitialized) {
                        if (!this.isFullSearchHibernate) {
                            return null;
                        }
                        HIBERNATE_INITIALIZE.invoke(initializer);
                    }
                    return HIBERNATE_GET_IMPLEMENTATION.invoke(initializer);
                } catch (Exception e) {
                    System.err.println("[ConvertType] Failed to unproxy Hibernate object: " + e.getMessage());
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
         * 현재 보유한 객체 정보를 바탕으로 지정된 클래스 타입의 새로운 인스턴스를 생성하고 반환합니다.
         * <p>
         * 이 메서드는 중간 Map 변환 과정 없이 소스 객체에서 타겟 객체로 직접 값을 매핑합니다.
         * {@link ConvertField} 어노테이션을 사용하여 필드명 변경(mapping)이나 제외(ignore)를 처리할 수 있습니다.
         *
         * <p><strong>주의사항:</strong>
         * <ul>
         *     <li>{@link Collection}이나 {@link Map} 인터페이스를 상속받은 클래스는 직접 변환 대상으로 지정할 수 없습니다.</li>
         *     <li>변환 중 오류가 발생하면 예외를 던지는 대신 로그를 남기고 {@code null}을 반환합니다.</li>
         * </ul>
         *
         * @param clazz 변환할 대상 클래스 (DTO, VO 등)
         * @param <T>   반환될 객체의 타입
         * @return 변환된 객체 인스턴스, 또는 변환 실패 시 {@code null}
         */
        public <T> T to(Class<T> clazz) {
            return to(clazz, LIMIT_DEPTH);
        }

        @SuppressWarnings("unchecked")
        private <T> T to(Class<T> clazz, int depth) {
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
                                
                                if (Set.class.isAssignableFrom(fieldType)) targetCol = new LinkedHashSet<>();
                                else if (List.class.isAssignableFrom(fieldType) || fieldType.isInterface()) targetCol = new ArrayList<>();
                                else {
                                    try {
                                        targetCol = (Collection<Object>) fieldType.getDeclaredConstructor().newInstance();
                                    } catch (Exception e) {
                                        targetCol = new ArrayList<>();
                                    }
                                }

                                for (Object item : sourceIterable) {
                                    if (item != null) {
                                        targetCol.add(new ValueObject(item, this.isFullSearchHibernate).to(targetItemClass, depth));
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
                                        targetMap.put(entry.getKey(), new ValueObject(mapValue, this.isFullSearchHibernate).to(valueClass, depth));
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
                            targetField.set(newInstance, new ValueObject(value, this.isFullSearchHibernate).to(fieldType, depth));
                        } catch (Exception e) {
                            try {
                                targetField.set(newInstance, jackson.convertValue(value, fieldType));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[ConvertType] Error converting object to " + clazz.getName() + ": " + e.getMessage());
                return null;
            }

            return newInstance;
        }

        /**
         * 변환된 객체를 Map 구조로 바꾸어 주고, JSON 형식으로도 이여진 수 있게합니다.
         * <p>
         * 이 메서드는 현재 객체(instance)가 이미 Map 형태라면 그대로 복사하여 반환하고,
         * 일반 객체라면 리플렉션을 통해 필드 값을 추출하여 Map으로 변환합니다.
         *
         * @return ConvertedMap 형식의 데이터
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