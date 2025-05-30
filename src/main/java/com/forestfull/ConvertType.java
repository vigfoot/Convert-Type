package com.forestfull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
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

        private static final Function<Object, ConvertedMap> toMapFunction = instance -> {
            final ConvertedMap map = new ConvertedMap();
            for (Field field : instance.getClass().getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    map.put(field.getName(), field.get(instance));
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
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

            } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                e.printStackTrace();
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

        public ConvertedMap putOver(String key, Object value) {
            super.put(key, value);
            return this;
        }
    }
}
