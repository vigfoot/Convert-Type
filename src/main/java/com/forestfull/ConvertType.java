package com.forestfull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;

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

        private ValueObject(Object instance) {
            this.instance = instance;
        }

        public <T> T to(Class<T> clazz) {
            Constructor<T> constructor = null;
            T instance = null;
            try {
                constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                instance = constructor.newInstance();
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                //TODO: 보충 필요
            } finally {
                if (constructor != null) constructor.setAccessible(false);

            }

            return instance;
        }

        public ConvertedMap toMap() {
            return new ConvertedMap();
        }

    }

    public static class ConvertedMap extends LinkedHashMap<String, Object> {

        public ConvertedMap putOver(String key, Object value) {
            super.put(key, value);
            return this;
        }
    }
}
