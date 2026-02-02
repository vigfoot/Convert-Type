package com.forestfull.convert_type;

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

    /**
     * 여러 DTO 객체 각에 대해 ValueObject 바인드를 생성합니다.
     *
     * @param instance 변환 대상 객체
     * @return ValueObject 인스턴스
     */
    public static ValueObject from(Object instance) {
        return new ValueObject(instance);
    }

    /**
     * 자동 클래스 객체를 변환할 수 있는 ValueObject 클래스
     */
    public static class ValueObject {

        private final Object instance;

        private static final Function<Object, ConvertedMap> toMapFunction = i -> {
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

        protected ValueObject(Object instance) {
            this.instance = instance;
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

        private static Object unProxy(Object value) {
            if (value == null) return null;

            String className = value.getClass().getName();
            if (className.contains("hibernate") && className.contains("Proxy")) {
                try {
                    Method getLazyInitializer = value.getClass().getMethod("getHibernateLazyInitializer");
                    Object initializer = getLazyInitializer.invoke(value);

                    Method isUninitialized = initializer.getClass().getMethod("isUninitialized");
                    if ((boolean) isUninitialized.invoke(initializer)) {
                        Method initialize = initializer.getClass().getMethod("initialize");
                        initialize.invoke(initializer);
                    }

                    Method getImplementation = initializer.getClass().getMethod("getImplementation");
                    return getImplementation.invoke(initializer);

                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    return null;
                }
            }

            if (className.startsWith("org.hibernate.collection")) {
                try {
                    Method sizeMethod = value.getClass().getMethod("size");
                    sizeMethod.invoke(value);
                } catch (Exception ignored) {}
            }

            return value;
        }

        /**
         * 변환 클래스를 지정하고, 해당 클래스 인스턴스로 변환합니다.
         *
         * @param clazz 변환 발생 클래스
         * @return 변환된 객체
         * @param <T> 변환 클래의 파이프
         */
        public <T> T to(Class<T> clazz) {
            Constructor<T> constructor = null;
            T newInstance = null;
            try {
                constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                newInstance = constructor.newInstance();

                final ConvertedMap instanceMap = instance instanceof ConvertedMap ? (ConvertedMap) instance : toMapFunction.apply(instance);

                List<Field> allFields = getAllFields(clazz);

                for (Field field : allFields) {
                    final String name = field.getName();
                    if (!instanceMap.containsKey(name)) continue;

                    field.setAccessible(true);
                    field.set(newInstance, instanceMap.get(name));
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