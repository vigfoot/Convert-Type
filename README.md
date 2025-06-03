# ConvertType

**ConvertType**은 Java Reflection 기반의 경량 타입 변환 유틸리티 라이브러리입니다.  
DTO 간 자동 필드 매핑, JSON 포맷 변환 기능을 제공합니다.

---

## ✨ 주요 특징

- 클래스 간 **자동 필드 매핑** (필드명 기준)
- **중첩 구조, 배열, 리스트** 지원
- JSON 직렬화를 위한 `ConvertedMap` 제공
- 최소 의존성, 간단한 API

---

## 📦 설치 방법

### Maven

```xml
<dependency>
  <groupId>com.forestfull</groupId>
  <artifactId>convert-type</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("com.forestfull:convert-type:1.0.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'com.forestfull:convert-type:1.0.0'
```

> ☑️ Maven Central 등록 완료  
> 🔗 [View on Maven Central](https://central.sonatype.com/artifact/com.forestfull/convert-type)

---

## 🚀 사용 예시

### 클래스 간 변환

```java
class Source {
    int[] numbers = {1, 2};
    String name = "sample";
}

class Target {
    int[] numbers;
    String name;
}

Target target = ConvertType.from(new Source()).to(Target.class);
System.out.println(Arrays.toString(target.numbers)); // [1, 2]
System.out.println(target.name);                     // sample
```

### JSON 변환

```java
ConvertedMap map = ConvertType.from(new Source()).toMap();
System.out.println(map.toJsonString());

// 출력 예:
// {
//   "numbers": [1, 2],
//   "name": "sample"
// }
```

---

## ⚙️ 내부 구조

- `ValueObject`: 리플렉션 기반 필드 추출 및 매핑 처리
- `ConvertedMap`: 내부 값을 Map 구조로 감싸 JSON 직렬화 지원
- 기본형, 참조형, 배열, 리스트 모두 지원

---

## 🛠 기여 방법

1. 이슈 등록 또는 Pull Request
2. 테스트 코드 필수 작성
3. 코드 스타일 통일 부탁드립니다

---

## 📄 라이선스

Apache License, Version 2.0

---

> **개발자**: [@vigfoot](https://github.com/vigfoot)  
> **오픈소스 작업 공간**: https://github.com/vigfoot/vigfoot
