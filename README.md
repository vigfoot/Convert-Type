# ConvertType

ConvertType은 Java Reflection 기반의 경량 타입 변환 유틸리티 라이브러리입니다. DTO 간 자동 필드 매핑, JSON 포맷 변환 기능을 제공합니다.

## ✨ 주요 특징

* 클래스 간 **자동 필드 매핑** (이름 기반)
* 중첩 구조 및 배열, 리스트 지원
* **JSON 직렬화**를 위한 `ConvertedMap` 제공
* 간단한 API로 빠르게 사용 가능

## 📦 설치 방법

Maven/Gradle 저장소 배포 전이라면 소스 코드 직접 포함 또는 JAR 빌드 후 로컬 추가

```bash
# 예시: 로컬 빌드
./gradlew build
```

## 🚀 사용 예시

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
// {
//   "numbers": [1, 2],
//   "name": "sample"
// }
```

## 🧠 내부 동작

* `ValueObject`가 리플렉션으로 필드를 추출 후 매핑
* `ConvertedMap`은 필드 정보를 `Map`으로 래핑 + JSON 문자열 출력
* 기본형, 참조형, 배열, 리스트 모두 지원

## 🛠️ 기여 방법

1. 이슈 등록 또는 풀 리퀘스트
2. 테스트 코드 필수 작성
3. 코드 스타일 통일 부탁드립니다

## 📄 라이선스

MIT License

---

> 개발자: **@vigfoot**
> lightweight conversion for Java developers
