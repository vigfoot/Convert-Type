package com.forestfull.convert_type;

import java.lang.reflect.Array;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ConvertedMap 은 변환된 클래스 객체의 필드를 JSON 형식으로 변환할 수 있게 해주는 다수중 호출 가능 디자인이 가능한 Map 패키지 파일입니다.
 *
 * <p><strong>기능</strong>:
 * <ul>
 *   <li>{@link #putOver(String, Object)}: 키가 있을 경우 값 변경</li>
 *   <li>{@link #toJsonString()}: JSON 형식의 문자열 발생</li>
 * </ul>
 *
 * <p><strong>예시</strong>:
 * <pre>{@code
 * ConvertedMap map = new ConvertedMap();
 * map.putOver("name", "John");
 * map.putOver("age", 30);
 * System.out.println(map.toJsonString());
 * // {
 * //   "name": "John",
 * //   "age": 30
 * // }
 * }</pre>
 *
 * @author vigfoot
 */
public class ConvertedMap extends LinkedHashMap<String, Object> {
    private static final Predicate<Object> isArray = o -> o instanceof List || o.getClass().isArray();

    public ConvertedMap putOver(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public <T> T to(Class<T> clazz) {
        return new ConvertType.ValueObject(this).to(clazz);
    }

    public String toJsonString() {
        return toJsonString(super.entrySet());
    }

    private String getCommonData(Object value) {
        final StringBuilder builder = new StringBuilder();

        if (value == null) {
            builder.append("null");

        } else if (isArray.test(value)) {
            try {
                for (Object o : (List) value) builder.append(toJsonString(o));
            } catch (ClassCastException e) {
                for (int i = 0; i < Array.getLength(value); i++)
                    builder.append(toJsonString(Array.get(value, i)));
            }

        } else if (value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof Byte
                || value instanceof Boolean
        ) {
            builder.append(value);

        } else if (value instanceof Map) {
            builder.append(toJsonString(((ConvertedMap) value).entrySet()));

        } else if (!value.getClass().getPackage().getName().startsWith("java.")) {
            builder.append(toJsonString((ConvertType.from(value).toMap()).entrySet()));

        } else {
            builder.append("\"")
                    .append(value)
                    .append("\"");
        }

        return builder.toString();
    }

    private String toJsonString(Object listValue) {
        return "["
                + Stream.of(listValue)
                .map(this::getCommonData)
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

                    } else if (e.getValue() instanceof Map) {
                        builder.append("\"")
                                .append(e.getKey())
                                .append("\"")
                                .append(":")
                                .append(toJsonString(((Map) e.getValue()).entrySet()));

                    } else if (e.getValue().getClass().getPackage() != null && !e.getValue().getClass().getPackage().getName().startsWith("java.")) {
                        builder.append("\"")
                                .append(e.getKey())
                                .append("\"")
                                .append(":")
                                .append(toJsonString((ConvertType.from(e.getValue()).toMap()).entrySet()));

                    } else {
                        builder.append(getCommonData(e.getValue()));

                    }

                    return builder.toString();
                })
                .collect(Collectors.joining(","))
                + "}"
                ;
    }
}