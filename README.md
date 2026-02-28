# ConvertType 🚀

[![Maven Central](https://img.shields.io/maven-central/v/com.forestfull/convert-type.svg?label=Maven%20Central)](https://mvnrepository.com/artifact/com.forestfull/convert-type)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**ConvertType**은 Java Reflection과 Jackson을 결합한 **고성능 하이브리드 타입 변환 라이브러리**입니다.  
단순한 필드 복사를 넘어, **JPA/Hibernate 엔티티의 지연 로딩(Lazy Loading) 제어**와 복잡한 데이터 구조를 안전하고 스마트하게 변환합니다.

---

## ✨ 주요 특징

- **🚀 고성능 직접 매핑**: MethodHandle 캐싱 기술을 적용하여 일반 Reflection보다 빠른 속도로 값을 직접 주입합니다.
- **🛡️ 완전한 타입 안전성 (New)**: 제네릭을 통해 변환 후 콜백(peek)에서 별도의 캐스팅 없이 원본 객체와 타겟 객체에 접근할 수 있습니다.
- **🎯 정밀한 필드 제어**: @ConvertField 어노테이션으로 필드 매핑(mapping) 및 제외(ignore)를 설정합니다.
- **📦 컬렉션 완벽 지원**: List, Set, Map 및 배열을 재귀적으로 탐색하여 타겟 타입에 맞는 표준 컬렉션으로 자동 변환합니다.
- **🔄 객체 덮어쓰기 (Overwrite)**: 원본을 유지하며 특정 객체의 null이 아닌 값만 골라 담은 새로운 객체를 생성합니다.
- **❄️ Hibernate 지연 로딩 제어**: 프록시 객체의 강제 초기화(fromFull) 또는 안전한 null 처리(from)를 선택할 수 있습니다.
- **🌀 순환 참조 방어**: 최대 50단계의 깊이 제한을 통해 무한 루프 및 StackOverflowError를 원천 차단합니다.

---

## 📦 설치 방법

### Maven
```xml
<dependency>
    <groupId>com.forestfull</groupId>
    <artifactId>convert-type</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Gradle (Groovy)

```groovy
implementation 'com.forestfull:convert-type:2.0.0'
```

---

## 🚀 사용 가이드

### 1. 타입 안전한 후처리 (Type-Safe Peek) 🌟 **New**
변환 직후, 데이터를 가공해야 할 때 사용합니다.  
제네릭 지원으로 강제 형변환(Casting) 없이 안전하게 코딩할 수 있으며 IDE의 자동완성 기능을 100% 활용합니다.
```java
// UserEntity -> UserDto 변환 후 특정 필드 조합
UserDto dto = ConvertType.from(userEntity)
                .to(UserDto.class, (src, target) -> {
                // src는 자동으로 UserEntity, target은 UserDto 타입으로 추론됩니다.
                target.setDisplayName(src.getFirstName() + " " + src.getLastName());
                target.setProcessedAt(LocalDateTime.now());
});
```

### 2. 기본 객체 변환 (Entity -> DTO)
필드명이 일치하면 자동으로 값이 복사됩니다.  
(대상 클래스에 기본 생성자 필요)

```java
UserDto dto = ConvertType.from(entity).to(UserDto.class);
```

### 3. 필드 매핑 제어 (@ConvertField)
```java
public class UserDto {
    @ConvertField(mapping = "user_id") // 소스의 user_id 필드값을 이 필드에 매핑
    private String loginId;

    @ConvertField(ignore = true) // 변환 대상에서 제외
    private String internalToken;
}
```

### 4. 객체 덮어쓰기 (Overwrite)
기존 객체를 복제한 후, 다른 객체의 null이 아닌 값만 덮어쓴 새 객체를 반환합니다.
```java
// original의 값은 유지하되, update의 값이 존재하면 해당 값으로 교체된 새 객체 생성
User result = ConvertType.from(original).overwrite(update);
```

### 5. JPA 엔티티 지연 로딩 제어
```java
// 초기화되지 않은 프록시는 null 처리 (LazyInitializationException 방지)
UserDto dto = ConvertType.from(userEntity).to(UserDto.class);

// 지연 로딩된 필드까지 강제로 Fetch 하여 데이터 로드
UserDto fullDto = ConvertType.fromFull(userEntity).to(UserDto.class);
```

### 6. Map 변환 (ConvertedMap)
객체를 Map 구조로 변환하거나, Map을 객체로 변환할 수 있습니다.
```java
// Object -> Map
ConvertedMap map = ConvertType.from(userEntity).toMap();

// Map -> Object
UserDto dto = ConvertType.from(sourceMap).to(UserDto.class);
```

---

## ⚙️ 내부 메커니즘

1. MethodHandle Caching: java.lang.invoke.MethodHandle을 사용하여 리플렉션의 오버헤드를 극적으로 줄여 성능을 최적화했습니다.
2. Hybrid Mapping Strategy:
    - POJO: 리플렉션을 통한 고속 직접 필드 주입
    - Interface/Abstract/Time/String: Jackson 엔진 위임을 통한 데이터 정합성 확보
3. Deep Copy for Collections: 원본 컬렉션과의 참조를 완전히 끊고 타겟 타입에 맞는 가변(Mutable) 표준 컬렉션 인스턴스를 새로 생성합니다.
4. JDK 9+ Friendly: 최신 JDK의 모듈 시스템 환경에서도 접근 제어 이슈 발생 시 안전한 폴백(Fallback) 로직을 수행합니다.

---

## 📄 라이선스

- Apache License, Version 2.0
- 개발자: @vigfoot (Busan, Korea)