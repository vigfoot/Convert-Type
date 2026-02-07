package com.forestfull.convert_type;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

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
        private final Integer LIMIT_DEPTH = 50;
        private final Object instance;
        private final boolean isFullSearchHibernate;

        private final Function<Object, ConvertedMap> toMapFunction = i -> {
            final ConvertedMap map = new ConvertedMap();
            Class<?> currentClass = i.getClass();

            while (currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (map.containsKey(field.getName())) continue;

                    try {
                        field.setAccessible(true);
                        map.put(field.getName(), unProxy(field.get(i)));
                        field.setAccessible(false);
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            return map;
        };

        protected ValueObject(Object instance, boolean isFullSearchHibernate) {
            this.instance = instance;
            this.isFullSearchHibernate = isFullSearchHibernate;
        }

        private static List<Field> getAllFields(Class<?> clazz) {
            final List<Field> fieldList = new ArrayList<>();
            getAllFields(clazz, fieldList, new HashSet<>());
            return fieldList;
        }

        private static void getAllFields(Class<?> clazz, List<Field> fieldList, Set<String> nameSet) {
            if (clazz == null || clazz == Object.class) return;

            for (Field field : clazz.getDeclaredFields()) {
                boolean isSuccess = nameSet.add(field.getName());
                if (isSuccess) fieldList.add(field);
            }

            getAllFields(clazz.getSuperclass(), fieldList, nameSet);
        }

        private Object unProxy(Object value) {
            if (value == null) return null;

            final String className = value.getClass().getName();

            if (className.contains("hibernate") && className.contains("Proxy")) {
                try {
                    final Method getLazyInitializer = value.getClass().getMethod("getHibernateLazyInitializer");
                    final Object initializer = getLazyInitializer.invoke(value);

                    final Method isUninitialized = initializer.getClass().getMethod("isUninitialized");
                    boolean uninitialized = (boolean) isUninitialized.invoke(initializer);

                    if (uninitialized) {
                        if (!this.isFullSearchHibernate) {
                            return null;
                        }

                        final Method initialize = initializer.getClass().getMethod("initialize");
                        initialize.invoke(initializer);
                    }

                    final Method getImplementation = initializer.getClass().getMethod("getImplementation");
                    return getImplementation.invoke(initializer);

                } catch (Exception e) {
                    return null;
                }
            }

            if (className.startsWith("org.hibernate.collection")) {
                try {
                    final Method wasInitialized = value.getClass().getMethod("wasInitialized");
                    final boolean initialized = (boolean) wasInitialized.invoke(value);

                    if (!initialized) {
                        if (!this.isFullSearchHibernate) return null;

                        final Method sizeMethod = value.getClass().getMethod("size");
                        sizeMethod.invoke(value);
                    }
                } catch (Exception ignored) {
                }
            }

            return value;
        }

        /**
         * 변환 클래스를 지정하고, 해당 클래스 인스턴스로 변환합니다.
         *
         * @param clazz 변환 발생 클래스
         * @param <T>   변환 클래의 파이프
         * @return 변환된 객체
         */
        public <T> T to(Class<T> clazz) {
            return to(clazz, LIMIT_DEPTH);
        }

        private <T> T to(Class<T> clazz, int depth) {
            if (depth <= 0) {
                System.err.println("Too many nested objects. Please check for circular references in your class: " + clazz.getName() + ", depth: " + depth);
                return null;
            }

            depth--;
            Constructor<T> constructor = null;
            T newInstance = null;

            try {
                if (clazz.isInterface() ||
                        java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()) ||
                        clazz.getName().startsWith("java.time")) {
                    return jackson.convertValue(instance, clazz);
                } else {
                    // 일반 클래스인 경우 기존처럼 생성자 호출
                    constructor = clazz.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    newInstance = constructor.newInstance();
                }
                final ConvertedMap instanceMap = instance instanceof ConvertedMap ? (ConvertedMap) instance : toMapFunction.apply(instance);

                final List<Field> allFields = getAllFields(clazz);

                for (Field field : allFields) {
                    final String name = field.getName();
                    if (!instanceMap.containsKey(name)) continue;

                    final Object value = instanceMap.get(name);
                    if (value == null) continue;

                    field.setAccessible(true);
                    final Class<?> fieldType = field.getType();

                    if (fieldType.isAssignableFrom(value.getClass())) {
                        field.set(newInstance, value);
                    } else if (value instanceof Iterable && (Collection.class.isAssignableFrom(fieldType) || fieldType.isArray())) {
                        Iterable<?> sourceIterable = (Iterable<?>) value;

                        java.lang.reflect.Type genericType = field.getGenericType();
                        if (genericType instanceof java.lang.reflect.ParameterizedType) {
                            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
                            Class<?> targetItemClass = (Class<?>) pt.getActualTypeArguments()[0];

                            Collection<Object> targetCol;
                            if (Set.class.isAssignableFrom(fieldType)) {
                                targetCol = new HashSet<>();
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
                                field.set(newInstance, array);
                            } else {
                                field.set(newInstance, targetCol);
                            }
                        }

                    } else if (value instanceof Map && Map.class.isAssignableFrom(fieldType)) {
                        Map<?, ?> sourceMap = (Map<?, ?>) value;
                        Map<Object, Object> targetMap = new HashMap<>();

                        java.lang.reflect.Type genericType = field.getGenericType();
                        if (genericType instanceof java.lang.reflect.ParameterizedType) {
                            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
                            Class<?> valueClass = (Class<?>) pt.getActualTypeArguments()[1];

                            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                                Object mapValue = entry.getValue();
                                if (mapValue != null) {
                                    targetMap.put(entry.getKey(), new ValueObject(mapValue, this.isFullSearchHibernate).to(valueClass, depth));
                                } else {
                                    targetMap.put(entry.getKey(), null);
                                }
                            }
                            field.set(newInstance, targetMap);
                        }
                    } else {
                        field.set(newInstance, new ValueObject(value, this.isFullSearchHibernate).to(fieldType, depth));
                    }
                    field.setAccessible(false);
                }

            } catch (Exception e) {
                e.printStackTrace(System.err);
            } finally {
                if (constructor != null) constructor.setAccessible(false);
            }

            return newInstance;
        }

        /**
         * 변환된 객체를 Map 구조로 바꾸어 주고, JSON 형식으로도 이여진 수 있게합니다.
         *
         * @return ConvertedMap 형식의 데이터
         */
        public ConvertedMap toMap() {
            return toMapFunction.apply(instance);
        }
    }
}