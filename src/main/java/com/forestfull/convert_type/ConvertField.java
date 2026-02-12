package com.forestfull.convert_type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ConvertType 변환 시 필드 매핑 규칙을 정의하는 어노테이션입니다.
 * <p>
 * 타겟 클래스(DTO)의 필드에 적용하여 소스 객체의 필드명과 다를 경우 이름을 지정하거나,
 * 변환에서 제외할 수 있습니다.
 *
 * <p><strong>사용 예시:</strong>
 * <pre>{@code
 * public class UserDto {
 *     @ConvertField(mapping = "user_name") // 소스 객체의 "user_name" 값을 이 필드에 매핑
 *     private String userName;
 *
 *     @ConvertField(ignore = true) // 이 필드는 변환하지 않음
 *     private String internalData;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConvertField {

    /**
     * 매핑할 소스 객체의 필드(또는 Map의 Key) 이름을 지정합니다.
     * <p>
     * 값이 비어있으면 타겟 필드의 이름을 그대로 사용합니다.
     *
     * @return 매핑할 소스 필드명
     */
    String mapping() default "";

    /**
     * 이 필드를 변환 대상에서 제외할지 여부를 설정합니다.
     *
     * @return true일 경우 변환을 건너뜁니다.
     */
    boolean ignore() default false;
}
