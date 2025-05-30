package com.forestfull;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        private static final Predicate<Object> isArray = o -> o instanceof List || o.getClass().isArray();
        private static final Predicate<Object> isEmptyString = o -> "".equals(o) || String.valueOf(o).trim().isEmpty();

        public ConvertedMap putOver(String key, Object value) {
            super.put(key, value);
            return this;
        }

        public String toJsonString() {
            return toJsonString(super.entrySet());
        }

        private String toJsonString(Object listValue) {
            return "["
                    + Stream.of(listValue)
                    .map(obj -> {
                        final StringBuilder builder = new StringBuilder();

                        if (obj == null) {
                            builder.append("null");

                        } else if (isArray.test(obj)) {
                            builder.append(toJsonString(obj));

                        } else {
                            builder.append("\"")
                                    .append(obj)
                                    .append("\"");
                        }

                        return builder.toString();
                    })
                    .collect(Collectors.joining(","))
                    + "]";
        }

        private String toJsonString(Set<Map.Entry<String, Object>> entrySet) {
            return "{"
                    + entrySet.stream()
                    .map(e -> {
                        final StringBuilder builder = new StringBuilder();
                        builder.append("\"")
                                .append(e.getKey())
                                .append("\"")
                                .append(":");

                        if (e.getValue() == null) {
                            builder.append("null");

                        } else if (isArray.test(e.getValue())) {
                            builder.append(toJsonString(e.getValue()));

                        } else if (e.getValue() instanceof Integer
                                || e.getValue() instanceof Long
                                || e.getValue() instanceof Float
                                || e.getValue() instanceof Double
                                || e.getValue() instanceof Byte
                                || e.getValue() instanceof Boolean
                        ) {
                            builder.append(e.getValue());

                        } else if (e.getValue() instanceof Map) {
                            builder.append("\"")
                                    .append(e.getKey())
                                    .append("\"")
                                    .append(":")
                                    .append(toJsonString(((Map) e.getValue()).entrySet()));

                        } else {
                            builder.append("\"")
                                    .append(e.getValue())
                                    .append("\"");
                        }

                        return builder.toString();
                    })
                    .collect(Collectors.joining(","))
                    + "}"
                    ;
        }
    }
}