package com.forestfull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * com.forestfull
 *
 * @author vigfoot
 * @version 2025-05-27
 */
public class ConvertType {

    public static ValueObject from(Object instance) {
        return new ValueObject(instance);
    }

    public static class ValueObject {

        private final Object instance;

        private static final Function<Object, ConvertedMap> toMapFunction = i -> {
            final ConvertedMap map = new ConvertedMap();
            for (Field field : i.getClass().getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    map.put(field.getName(), field.get(i));
                    field.setAccessible(false);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
            return map;
        };

        private ValueObject(Object instance) {
            this.instance = instance;
        }

        public <T> T to(Class<T> clazz) {
            Constructor<T> constructor = null;
            T newInstance = null;
            try {
                constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                newInstance = constructor.newInstance();

                final Map<String, Object> instanceMap = toMapFunction.apply(instance);

                for (Field field : clazz.getDeclaredFields()) {
                    final String name = field.getName();
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

        public ConvertedMap toMap() {
            return toMapFunction.apply(instance);
        }
    }

    public static class ConvertedMap extends LinkedHashMap<String, Object> {
        private static Predicate<Object> isToArray = o -> {
            if (o == null) return false;
            if ("".equals(o)) return false;
            if (String.valueOf(o).trim().isEmpty()) return false;
            if (o instanceof List || o.getClass().isArray()) return true;

            return false;
        };

        public ConvertedMap putOver(String key, Object value) {
            super.put(key, value);
            return this;
        }

        public String toJsonString() {
            return toJsonString(super.entrySet());
        }

        private String toJsonString(Set<Map.Entry<String, Object>> map) {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            for (Map.Entry<String, Object> entry : super.entrySet()) {
                if (isToArray.test(entry.getValue())) {
                    // 재귀 필요
                } else {

                }
            }
            builder.append("}");

            return builder.toString();
        }
    }
}