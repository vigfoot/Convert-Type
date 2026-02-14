# ConvertType 🚀

[![Maven Central](https://img.shields.io/maven-central/v/com.forestfull/convert-type.svg?label=Maven%20Central)](https://mvnrepository.com/artifact/com.forestfull/convert-type)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**ConvertType**은 Java Reflection과 Jackson을 결합한 **고성능 하이브리드 타입 변환 라이브러리**입니다.  
단순한 필드 복사를 넘어, **JPA/Hibernate 엔티티의 지연 로딩(Lazy Loading) 제어**와 복잡한 데이터 구조를 안전하고 스마트하게 변환합니다.

---

## ✨ 주요 특징

- **🚀 고성능 직접 매핑**: 중간 객체(`Map`) 생성 없이 소스 객체에서 타겟 객체로 값을 직접 주입하여 성능을 극대화했습니다.
- **🛡️ JDK 9+ 모듈 시스템 대응**: `InaccessibleObjectException` 등 최신 JDK 환경에서의 접근 제어 이슈를 안전하게 처리합니다.
- **🎯 정밀한 필드 제어**: `@ConvertField` 어노테이션을 통해 필드명 변경(`mapping`)이나 변환 제외(`ignore`)를 손쉽게 설정할 수 있습니다.
- **📦 컬렉션 완벽 지원**: `List`, `Set`, `Map`은 물론, `Arrays.asList()`로 생성된 불변 리스트도 수정 가능한 표준 컬렉션(`ArrayList` 등)으로 자동 변환합니다.
- **🔄 객체 덮어쓰기 (Overwrite)**: 기존 객체를 기반으로 새로운 객체를 생성하고, 다른 객체의 값(null이 아닌 값)을 덮어쓰는 overwrite 기능을 제공합니다.
- **Lazy Loading 제어**: 초기화되지 않은 Hibernate 프록시를 무시하거나(`from`), 강제로 로드(`fromFull`)할 수 있습니다.
- **안전한 예외 처리**: 런타임 에러 발생 시 애플리케이션이 중단되지 않도록 로그를 남기고 `null`을 반환하거나 기본값으로 초기화합니다.

---

## 📦 설치 방법

### Maven

```xml
<dependency>
    <groupId>com.forestfull</groupId>
    <artifactId>convert-type</artifactId>
    <version>1.3.2</version>
</dependency>
```

### Gradle (Groovy)

```groovy
implementation 'com.forestfull:convert-type:1.3.2'
```

---

## 🚀 사용 가이드

### 1. 기본 객체 변환 (Entity -> DTO)

가장 기본적인 사용법입니다. 필드명이 일치하면 자동으로 값이 복사됩니다.
**주의:** 변환 대상 클래스(DTO)에는 반드시 **기본 생성자(No-args constructor)**가 있어야 합니다.

```java
// Source Object (Entity)
UserEntity entity = new UserEntity("user1", "John Doe", 30);

// Target Class (DTO)
UserDto dto = ConvertType.from(entity).to(UserDto.class);

System.out.println(dto.getUsername()); // "user1"
```

### 2. 필드 매핑 제어 (@ConvertField)

필드명이 다르거나 특정 필드를 제외하고 싶을 때 어노테이션을 사용합니다.

```java
public class UserDto {
    @ConvertField(mapping = "user_name") // 소스 객체의 "user_name" 값을 이 필드에 매핑
    private String loginId;

    @ConvertField(ignore = true) // 이 필드는 변환하지 않음
    private String internalData;
}
```

### 3. 객체 덮어쓰기 (Overwrite)

기존 객체의 값을 유지하면서, 다른 객체의 값(null이 아닌 값)만 덮어쓴 **새로운 객체**를 생성합니다.

```java
User original = new User("user1", "Old Name");
User update = new User(null, "New Name"); // username은 null

// original을 복제한 후, update의 값 중 null이 아닌 값만 덮어씀
User result = ConvertType.from(original).overwrite(update);

// result.getUsername() -> "user1" (유지됨)
// result.getFullName() -> "New Name" (덮어써짐)
```

### 4. JPA 엔티티 변환 (Lazy Loading 제어)

지연 로딩된 데이터를 어떻게 처리할지 한 줄로 결정합니다.

```java
// 방법 A. 일반 변환 (Default)
// 초기화되지 않은 프록시 객체는 null로 변환되어 LazyInitializationException을 방지합니다.
UserDto dto = ConvertType.from(userEntity).to(UserDto.class);

// 방법 B. 전체 변환 (Full Search)
// 지연 로딩된 필드까지 모두 강제로 초기화(Proxy Initialize)하여 데이터를 꽉 채워 변환합니다.
UserDto fullDto = ConvertType.fromFull(userEntity).to(UserDto.class);
```

### 5. 컬렉션 및 중첩 객체 변환

리스트나 맵, 그리고 그 안에 포함된 객체들까지 재귀적으로 변환합니다.

```java
class CategoryEntity {
    String name;
    List<ProductEntity> products; // Entity 리스트
}

class CategoryDto {
    String name;
    List<ProductDto> products;    // DTO 리스트로 자동 변환
}

// 자동으로 List<ProductEntity> -> List<ProductDto> 변환 수행
CategoryDto dto = ConvertType.from(categoryEntity).to(CategoryDto.class);
```

### 6. Map 변환 (ConvertedMap)

객체를 `Map` 구조로 변환하거나, `Map`을 객체로 변환할 수 있습니다.

```java
// Object -> Map
ConvertedMap map = ConvertType.from(userEntity).toMap();
System.out.println(map.toJsonString()); // JSON 문자열 출력

// Map -> Object
ConvertedMap sourceMap = new ConvertedMap();
sourceMap.put("username","test");

UserDto dto = ConvertType.from(sourceMap).to(UserDto.class);
```

---

## ⚙️ 내부 메커니즘

1.  **Direct Field Access**: 리플렉션을 사용하여 필드에 직접 접근하되, `setAccessible(true)` 실패 시 안전하게 건너뜁니다.
2.  **Caching**: 클래스의 필드 정보와 생성자 정보를 캐싱(`ConcurrentHashMap`)하여 반복적인 리플렉션 비용을 최소화했습니다.
3.  **Hybrid Logic**:
    *   일반 객체: 리플렉션을 통한 고속 직접 매핑
    *   인터페이스/추상클래스/`java.time`/`String`: Jackson에게 위임하여 안정성 확보
4.  **Deep Copy for Collections**: 컬렉션 타입은 항상 새로운 인스턴스(`ArrayList`, `LinkedHashSet`, `HashMap`)를 생성하여 원본 객체와의 의존성을 끊고 수정 가능하도록 만듭니다.
5.  **Circular Reference Protection**: 최대 50단계(기본값)까지의 깊이를 허용하며, 이를 초과하는 순환 참조는 안전하게 차단합니다.

---

## 📄 라이선스

-   **Apache License, Version 2.0**
-   개발자: **@vigfoot**
