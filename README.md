# ConvertType 🚀

**ConvertType**은 Java Reflection과 Jackson을 결합한 하이브리드 타입 변환 라이브러리입니다.  
단순한 필드 복사를 넘어, **JPA/Hibernate 엔티티의 지연 로딩(Lazy Loading) 제어**와 복잡한 데이터 구조를 안전하고 스마트하게 변환합니다.

---

## ✨ 주요 특징

- **전략적 지연 로딩 (Hibernate)**: 초기화되지 않은 프록시를 무시하거나(`from`), 강제로 로드(`fromFull`)할 수 있습니다.
- **하이브리드 변환 엔진**: 일반 객체는 정밀한 리플렉션으로, 인터페이스와 `java.time`은 Jackson으로 처리합니다.
- **복잡한 타입 대응**: `List`, `Set`, `Map`은 물론, 일반 배열(`Array`)의 타입 변환까지 완벽하게 지원합니다.
- **순환 참조 방지**: 내장된 `LIMIT_DEPTH` 설정을 통해 엔티티 간 양방향 참조 시 발생하는 무한 루프를 차단합니다.

---

## 📦 설치 방법

### Maven

```xml

<dependency>
    <groupId>com.forestfull</groupId>
    <artifactId>convert-type</artifactId>
    <version>1.1.0</version>
</dependency>
```

### Gradle (Groovy)

```groovy
implementation 'com.forestfull:convert-type:1.1.0'
```

## 🚀 사용 가이드

### 1. JPA 엔티티 변환 (Lazy Loading 제어)

이 라이브러리의 가장 강력한 기능입니다. 지연 로딩된 데이터를 어떻게 처리할지 한 줄로 결정합니다.

```java
// 방법 A. 일반 변환 (Default)
// 초기화되지 않은 프록시 객체는 null로 변환되어 LazyInitializationException을 방지합니다.
UserDTO dto = ConvertType.from(userEntity).to(UserDTO.class);

// 방법 B. 전체 변환 (Full Search)
// 지연 로딩된 필드까지 모두 강제로 초기화(Proxy Initialize)하여 데이터를 꽉 채워 변환합니다.
UserDTO fullDto = ConvertType.fromFull(userEntity).to(UserDTO.class);
```

### 2. 컬렉션 및 배열 자동 변환

중첩된 리스트나 맵, 배열 내부의 엔티티들도 재귀적으로 탐색하여 DTO로 변환합니다.

```java
class Source {
    List<ItemEntity> items;      // 엔티티 리스트
    Map<String, TagEntity> tags; // 엔티티 맵
    String[] roles;              // 배열
}

class Target {
    List<ItemDTO> items;         // DTO 리스트로 자동 변환
    Map<String, TagDTO> tags;    // Value만 DTO로 자동 변환
    String[] roles;              // 배열 복사
}

Target result = ConvertType.from(source).to(Target.class);
```

### 3. JSON 변환 (ConvertedMap)

객체를 Map 구조로 변환한 뒤, 즉시 JSON 문자열로 출력할 수 있습니다.

```
ConvertedMap map = ConvertType.from(source).toMap();
System.out.println(map.toJsonString()); // Jackson 기반의 정제된 JSON 출력
```

### ⚙️ 내부 메커니즘

- UnProxy: 하이버네이트 프록시 및 컬렉션 래퍼를 감지하고 설정에 따라 실제 데이터를 추출합니다.
- Recursive Mapping: 필드 타입을 분석하여 Iterable, Map, Array일 경우 내부 요소까지 다시 변환 프로세스를 태웁니다.
- Hybrid Logic: 직접 구현하기 까다로운 인터페이스나 추상 클래스, 날짜/시간 타입은 사전에 설정된 Jackson 엔진에게 위임하여 정확도를 높입니다.
- Depth Control: 최대 50단계(기본값)까지의 깊이를 허용하며, 이를 초과하는 순환 참조는 안전하게 차단합니다.

### 📄 라이선스
- Apache License, Version 2.0
- 개발자: @vigfoot
- 공식 리포지토리: forestfull/convert-type