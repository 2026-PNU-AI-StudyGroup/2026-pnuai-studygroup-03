# WakeBook 백엔드 팀 공통 설정

> 이 문서는 WakeBook 백엔드 개발을 시작하기 전에 백엔드 팀원 2명이 함께 지킬 기술 설정, Git 규칙, API 규칙과 서비스 정책을 정리한 문서입니다.

## 1. 최종 권장 설정

| 항목 | 팀 공통 설정 |
|---|---|
| Java | **JDK 21** |
| Spring Boot | **4.1.0** |
| 빌드 도구 | **Gradle Groovy DSL** |
| Gradle | **9.5.1** (프로젝트의 Gradle Wrapper로 고정) |
| Group / Package | `com.wakebook` |
| Artifact / 프로젝트명 | `wakebook` |
| 패키징 | `Jar` |
| DBMS | **MySQL 8.x** |
| 데이터베이스명 | `wakebook` |
| DB 스키마 관리 | **Flyway** |
| 서버 포트 | `8080` |
| API 기본 경로 | `/api` |
| 프론트엔드 주소 | `http://localhost:5173` |
| 문자 인코딩 | `UTF-8` |
| 서비스 날짜 기준 | `Asia/Seoul` |
| API 시간 형식 | ISO 8601 |
| Lombok | 사용함
| DTO | Java `record` 우선 사용 |
| 테스트 | JUnit 5 + Spring Boot Test |

2026년 7월 26일 공식 Spring Initializr의 stable, non-SNAPSHOT 버전으로 기본 프로젝트를 생성했습니다.

- Spring Boot 버전은 `build.gradle`의 `org.springframework.boot` 플러그인에 고정되어 있습니다.
- Gradle 버전은 `gradle/wrapper/gradle-wrapper.properties`의 `distributionUrl`에 고정되어 있습니다.
- 다른 팀원은 별도로 Spring Boot 프로젝트를 만들거나 시스템 Gradle을 설치하지 않고 같은 저장소를 받아 Wrapper로 실행합니다.
- 버전을 변경할 때는 두 백엔드 팀원이 합의한 별도 PR에서 빌드와 테스트를 확인한 뒤 변경합니다.

## 2. Java 버전 확인

Windows PowerShell에서 다음 명령을 실행합니다.

```powershell
java -version
javac -version
where.exe java
Get-Command java | Select-Object -ExpandProperty Source
$env:JAVA_HOME
```

### 확인 방법

- `java -version`에 `21`이 표시되고 `javac -version`도 `21`이면 준비 완료입니다.
- `java`는 실행되지만 `javac`가 없으면 JRE만 설치됐거나 JDK 경로가 연결되지 않은 상태일 수 있습니다.
- `where.exe java`는 컴퓨터가 어떤 Java 실행 파일을 사용하고 있는지 보여줍니다.
- `$env:JAVA_HOME`이 비어 있거나 다른 버전을 가리키면 환경변수 설정을 확인해야 합니다.

예시:

```text
openjdk version "21.x.x"
javac 21.x.x
```

프로젝트를 받은 뒤에는 다음 명령도 확인합니다.

```powershell
cd backend
.\gradlew.bat --version
```

출력의 `JVM` 항목이 `21`이어야 합니다.

## 3. 다른 Java 버전이 이미 설치된 경우

기존 Java를 삭제하지 않고 **JDK 21을 추가로 설치해도 됩니다.** 여러 Java 버전은 한 컴퓨터에 함께 설치할 수 있습니다.

권장 방식:

1. 현재 버전을 위 명령으로 확인합니다.
2. 21이 아니면 **Eclipse Temurin JDK 21 LTS**와 같은 JDK 21 배포판을 추가로 설치합니다.
3. WakeBook 프로젝트에서는 JDK 21을 선택합니다.
4. 기존 Java를 사용하는 다른 프로젝트는 기존 버전을 계속 사용합니다.

PowerShell 한 창에서만 임시로 JDK 21을 선택하려면 다음처럼 설정할 수 있습니다.

```powershell
$env:JAVA_HOME = "<JDK 21 설치 경로>"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

java -version
javac -version
```

`<JDK 21 설치 경로>`에는 실제 설치 폴더를 입력합니다. 새 PowerShell을 열면 임시 설정은 사라집니다.

### VS Code에서 선택

1. `Ctrl + Shift + P`
2. `Java: Configure Java Runtime` 실행
3. WakeBook 프로젝트의 JDK로 21 선택

### IntelliJ에서 선택

1. `File → Project Structure → Project SDK`
2. JDK 21 선택
3. `Settings → Build Tools → Gradle → Gradle JVM`
4. JDK 21 선택

### 프로젝트에서도 Java 21 강제

`backend/build.gradle`에는 다음 설정을 사용합니다.

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

이 설정으로 개인 컴퓨터의 기본 Java와 관계없이 프로젝트가 Java 21을 요구하도록 만듭니다.

## 4. 최초 프로젝트 생성 규칙

두 사람이 각각 Spring Boot 프로젝트를 만들면 `build.gradle`, 패키지 구조와 설정 파일이 충돌합니다.

기본 프로젝트 생성은 완료되었습니다. 아래 규칙은 프로젝트를 다시 생성하라는 뜻이 아니라, 현재 부트스트랩 변경을 검토하고 병합하기 위한 절차입니다.

따라서 다음 순서로 진행합니다.

1. 한 명이 `feature/backend-bootstrap` 브랜치를 만듭니다.
2. 그 브랜치에서만 `backend/` Spring Boot 프로젝트를 생성합니다.
3. 실행과 테스트를 확인합니다.
4. `backend` 통합 브랜치로 Pull Request를 올립니다.
5. 병합 후 두 사람 모두 최신 `backend` 브랜치를 받습니다.
6. 각자 기능 브랜치를 만들어 작업합니다.

최초 의존성은 최소한으로 시작합니다.

- Spring Web
- Validation
- Lombok
- Spring Boot Test

다음 기능을 구현할 때 필요한 의존성을 단계적으로 추가합니다.

- Spring Data JPA
- MySQL Driver
- Flyway
- Spring Security
- JWT 구현에 필요한 라이브러리

정보나루와 OpenAI 관련 코드는 기본 프로젝트가 실행된 후 추가합니다.

### 현재 고정 파일

다음 파일은 기본 프로젝트와 버전을 재현하는 파일이므로 임의로 다시 만들지 않습니다.

```text
backend/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
└── gradle/wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties
```

실행할 때는 운영체제에 맞는 Wrapper를 사용합니다.

```powershell
# Windows PowerShell
.\gradlew.bat test
.\gradlew.bat bootRun
```

```bash
# macOS / Linux
./gradlew test
./gradlew bootRun
```

## 5. 팀 합의가 아직 필요한 항목

아래 항목은 구현 전에 두 백엔드 팀원이 확인해야 합니다. 합의 전에는 한쪽이 단독으로 구조를 확정하지 않습니다.

- [ ] Spring Boot 4.1을 그대로 사용할지 최종 확인
- [ ] DB, JPA, Flyway를 최초 PR에 포함할지 기능 PR에서 추가할지 결정
- [ ] JWT 라이브러리와 토큰의 서명 알고리즘 결정
- [ ] 패키지 구성을 기능형 패키지로 유지할지 결정
- [ ] 로컬 환경변수 주입 방식 결정 (`application-local.yml`, IDE 설정 등)
- [ ] 테스트 DB와 통합 테스트 방식 결정
- [ ] API 응답 래퍼와 오류 코드 규칙 최종 확인
- [ ] Flyway 파일 번호 예약 방식 결정

Spring Boot 4.1은 현재 공식 안정판이지만 새 메이저 계열이므로, 추가할 라이브러리가 4.1과 호환되는지 먼저 확인합니다. 호환성 때문에 3.x 계열을 선택한다면 기능 구현이 시작되기 전에 한 번만 변경하고 다시 고정합니다.

## 6. Git 브랜치 규칙

권장 흐름:

```text
feature/* → backend → develop → main
```

브랜치 예시:

```text
feature/backend-bootstrap
feature/common-api
feature/auth
feature/books
feature/bookshelf
feature/recommendation
feature/curation
```

규칙:

- `main`, `develop`, `backend`에는 직접 커밋하지 않습니다.
- 기능 하나당 브랜치와 PR 하나를 사용합니다.
- 병합 전에 `.\gradlew.bat test`를 실행합니다.
- `commit`, `push`, PR 생성은 변경 내용을 직접 확인한 뒤 수행합니다.

커밋 예시:

```text
chore: initialize Spring Boot backend
feat: implement user signup
feat: implement popular book API
fix: handle duplicate email signup
test: add login integration tests
docs: clarify bookshelf API contract
```

## 7. 패키지와 코드 규칙

```text
com.wakebook
├── common
│   ├── config
│   ├── exception
│   └── response
├── auth
├── user
├── book
├── bookshelf
├── recommendation
├── curation
└── external
    ├── library
    └── openai
```

각 기능 내부는 필요한 범위에서 다음과 같이 나눕니다.

```text
controller
service
repository
domain
dto
```

역할:

- `Controller`: HTTP 요청과 응답 처리
- `Service`: 실제 기능과 비즈니스 규칙 처리
- `Repository`: 데이터베이스 접근
- `domain`: Entity와 Enum
- `dto`: API 요청·응답 데이터

명명 규칙:

- Java 클래스: `PascalCase`
- Java 변수와 필드: `camelCase`
- DB 테이블과 컬럼: `snake_case`
- 요청 DTO: `SignupRequest`
- 응답 DTO: `SignupResponse`
- Entity를 API 응답으로 직접 반환하지 않고 DTO로 변환

## 8. API 공통 규칙

### 성공 응답

```json
{
  "success": true,
  "message": "요청이 완료되었습니다.",
  "data": {}
}
```

### 실패 응답

```json
{
  "success": false,
  "code": "AUTH_001",
  "message": "로그인이 필요합니다.",
  "data": null
}
```

`message`는 모든 성공·실패 응답에 포함합니다.

### HTTP 상태 코드

| 상황 | 상태 코드 |
|---|---:|
| 조회·수정·삭제 성공 | `200` |
| 생성 성공 | `201` |
| 요청값 오류 | `400` |
| 로그인 필요 | `401` |
| 권한 없음 | `403` |
| 데이터 없음 | `404` |
| 중복 데이터 | `409` |
| 서버 오류 | `500` |

삭제 API도 공통 JSON 응답을 사용하기 위해 MVP에서는 `200`으로 통일합니다.

### 페이지 번호

- 외부 API 요청과 응답: 1부터 시작
- Spring 내부 페이지: 0부터 시작
- Controller 또는 Service에서 `page - 1`로 변환
- `page < 1`은 `400 Bad Request`
- `size`의 기본값은 12, 최대값은 50

## 9. 인증과 권한 규칙

| 항목 | 결정 |
|---|---|
| 인증 방식 | JWT Bearer Token |
| 비밀번호 암호화 | BCrypt |
| Access Token 만료 | 1시간 |
| Refresh Token | MVP에서는 제외 |
| 회원가입·로그인 | 비로그인 허용 |
| 인기·검색·상세·오늘·랜덤 도서 | 비로그인 허용 |
| AI 키워드·추천·비교 | 로그인 필요 |
| 책장 | 로그인 필요 |
| 사서 기능 | `LIBRARIAN` 권한 필요 |
| 사서 가입 검증 | 환경변수 기반 초대코드 |

사서 회원가입 요청에서 초대코드를 함께 받고 서버에서 다음 환경변수와 비교합니다.

```text
LIBRARIAN_INVITE_CODE
```

클라이언트가 보낸 사용자 ID를 신뢰하지 않고, 로그인한 사용자 ID를 JWT 인증 정보에서 가져옵니다.

## 10. 환경변수

```env
DB_URL=jdbc:mysql://localhost:3306/wakebook
DB_USERNAME=
DB_PASSWORD=

JWT_SECRET=
JWT_EXPIRATION=3600000
LIBRARIAN_INVITE_CODE=

DATA4LIBRARY_API_KEY=
OPENAI_API_KEY=

FRONTEND_ORIGIN=http://localhost:5173
```

규칙:

- 실제 값이 들어간 `.env`와 로컬 설정 파일은 Git에서 제외합니다.
- `.env.example`에는 변수 이름과 안전한 예시만 작성합니다.
- API 키와 비밀번호를 코드, GitHub, 이슈, PR, 단체 채팅에 올리지 않습니다.
- 테스트와 CI에서는 실제 정보나루·OpenAI API를 호출하지 않습니다.

## 11. WakeBook 서비스 정책

### 잠자는 도서의 MVP 기준

다음 기준으로 먼저 구현하고 데이터 확보 가능성에 따라 조정합니다.

- 최근 12개월 부산 지역 대출 데이터를 기준으로 사용
- 동일 분야 내 대출량 하위 30% 도서
- 인기 도서 상위 100위는 후보에서 제외
- 출간 후 6개월이 지나지 않은 신간은 제외
- ISBN, 제목, 저자, 표지, 설명이 있는 도서만 포함
- 발견 가치는 동일 분야 내 대출량의 역순 백분위로 0~100 정규화

### 책장과 독서 상태

- 책장: 사용자가 만드는 컬렉션
- 책장 도서: `BookshelfItem` 연결 엔티티
- 읽기 상태: `WISH`, `READING`, `COMPLETED`, `REVISIT`
- 같은 책은 같은 책장에 한 번만 저장 가능
- URL의 `{bookId}`는 혼동을 막기 위해 `{shelfBookId}` 또는 `{itemId}`로 변경 권장

### AI의 역할

서버의 일반 코드가 담당:

- 잠자는 도서 후보 필터링
- 추천 가중치 계산
- 최종 추천 점수와 순위 계산

AI가 담당:

- 도서 핵심 키워드 생성
- 추천 이유 작성
- 두 책의 공통점과 차이 설명
- 사서 큐레이션 제목·소개·해시태그 초안 작성

AI 결과는 캐시하고, 실패해도 기본 도서 조회 기능이 작동하도록 분리합니다.

### 정보나루 연동

- Controller에서 정보나루를 직접 호출하지 않습니다.
- `BookProvider` 인터페이스를 만들고 가짜 구현으로 먼저 테스트합니다.
- 실제 연동은 `Data4LibraryBookProvider`와 같은 Adapter로 구현합니다.
- 외부 응답 DTO와 WakeBook API 응답 DTO를 분리합니다.
- 연결·읽기 시간 제한과 오류 변환을 설정합니다.
- 실제 API를 호출하지 않는 테스트용 Fake 또는 Mock Server를 사용합니다.

충돌이 자주 발생하는 다음 파일은 수정 전에 서로 알립니다.

- `build.gradle`
- `application.yml`
- `SecurityConfig`
- `.gitignore`
- Flyway 마이그레이션 파일
- 공통 응답 및 예외 클래스

Flyway 파일 번호가 겹치지 않도록 한 명이 마이그레이션 번호를 관리하거나 기능별 번호를 먼저 예약합니다.

## 13. PR 완료 조건

PR을 올리기 전에 다음을 모두 확인합니다.

- [ ] 요청한 기능 범위만 구현
- [ ] API 명세와 요청·응답 일치
- [ ] 필요한 테스트 작성
- [ ] `.\gradlew.bat test` 성공
- [ ] `git diff --check` 성공
- [ ] API 키·비밀번호·JWT secret이 포함되지 않음
- [ ] API 변경 시 `docs/API명세.md`도 함께 수정
- [ ] 변경 파일과 실행 방법을 PR 본문에 작성
- [ ] 다른 팀원의 담당 파일과 충돌 여부 확인

## 14. 최초 진행 순서

1. 두 팀원 모두 Java 버전을 확인합니다.
2. JDK 21이 없다면 추가 설치하고 프로젝트 JDK를 21로 설정합니다.
3. `backend` 브랜치의 역할과 PR 대상을 확정합니다.
4. 현재 부트스트랩 변경을 `feature/backend-bootstrap`에서 검토합니다.
5. 다음 작은 PR에서 `/api/health`를 구현합니다.
6. 테스트 통과 후 `backend` 브랜치로 PR을 올립니다.
7. 병합된 코드를 두 사람이 함께 받습니다.
8. 이후 공통 API, 가짜 도서 API, DB, 인증 순서로 작은 PR을 반복합니다.
