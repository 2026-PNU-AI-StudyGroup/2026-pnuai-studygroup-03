# WakeBook 백엔드 팀 합의사항

> 백엔드 팀원 2명이 개발 전에 확인하고 공통으로 지킬 최소 규칙입니다.

## 1. 개발 환경

| 항목 | 공통 설정 |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.0 |
| 빌드 | Gradle 9.5.1 · Groovy DSL |
| Gradle 실행 | 프로젝트의 Gradle Wrapper만 사용 |
| Group / Package | `com.wakebook` |
| Artifact | `wakebook` |
| DB | MySQL 8.4 |
| DB 스키마 관리 | Flyway |
| 서버 주소 | `http://localhost:8080` |
| API Prefix | `/api` |
| 프론트엔드 주소 | `http://localhost:5173` |
| 인코딩 / 날짜 기준 | UTF-8 / `Asia/Seoul` |
| Lombok | 사용 |
| DTO | Java `record` 우선 |
| 테스트 | JUnit 5 + Spring Boot Test |

- Spring Boot 버전은 `backend/build.gradle`에 고정합니다.
- Gradle 버전은 `backend/gradle/wrapper/gradle-wrapper.properties`에 고정합니다.
- 버전 변경은 별도 PR에서 두 사람이 합의한 경우에만 진행합니다.
- Spring Boot 4.1에 새로운 라이브러리를 추가할 때는 호환성을 먼저 확인합니다.

### 각자 최초 확인

```powershell
java -version
javac -version

cd backend
.\gradlew.bat --version
.\gradlew.bat test
```

`java`, `javac`, Gradle 출력의 JVM이 모두 21이어야 합니다.


## 2. 코드 구조와 작성 규칙

기능 중심 패키지를 사용합니다.

```text
com.wakebook
├── common
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

각 기능은 필요한 범위에서 다음 구조를 사용합니다.

```text
controller → service → repository
                ↓
           domain / dto
```

공통 규칙:

- Controller에는 복잡한 비즈니스 로직을 작성하지 않습니다.
- Entity를 API 응답으로 직접 반환하지 않고 응답 DTO로 변환합니다.
- 요청 DTO는 `SignupRequest`, 응답 DTO는 `SignupResponse`처럼 명명합니다.
- Java는 `camelCase`, 클래스는 `PascalCase`, DB는 `snake_case`를 사용합니다.
- API 계약을 변경하면 같은 PR에서 `docs/API명세.md`도 수정합니다.

## 3. API 공통 규칙

### 성공

```json
{
  "success": true,
  "message": "요청이 완료되었습니다.",
  "data": {}
}
```

### 실패

```json
{
  "success": false,
  "code": "AUTH_001",
  "message": "로그인이 필요합니다.",
  "data": null
}
```

- `message`는 모든 응답에 포함합니다.
- 조회·수정·삭제 성공은 `200`, 생성 성공은 `201`을 사용합니다.
- 요청 오류 `400`, 인증 필요 `401`, 권한 없음 `403`, 데이터 없음 `404`, 중복 `409`를 사용합니다.
- 삭제도 공통 JSON을 반환하므로 MVP에서는 `200`을 사용합니다.
- 외부 페이지는 1부터 시작하고, Spring 내부에서 `page - 1`로 변환합니다.
- 페이지 기본 크기는 12, 최대 크기는 50입니다.
- ISBN은 숫자가 아닌 `String`으로 처리합니다.

## 4. 인증과 권한

| 항목 | 결정 |
|---|---|
| 인증 | JWT Bearer Token |
| 구현 | Spring Security + Nimbus JOSE JWT |
| 서명 | HS256 |
| 비밀번호 | BCrypt |
| Access Token | 1시간 |
| Refresh Token | MVP 제외 |
| 사서 검증 | 환경변수 기반 초대코드 |

접근 범위:

- 공개: 회원가입, 로그인, 인기·검색·상세·오늘·랜덤 도서
- 로그인 필요: AI 키워드·추천·비교, 책장
- 사서 전용: 대시보드와 큐레이션 관리

사용자 ID는 요청값으로 신뢰하지 않고 JWT 인증 정보에서 가져옵니다.

## 5. DB와 환경변수

- DB/JPA/Flyway는 부트스트랩 PR과 분리해 `feature/db-setup`에서 추가합니다.
- Flyway 파일명은 `VyyyyMMddHHmm__설명.sql` 형식을 사용합니다.
- 같은 마이그레이션 번호를 만들지 않도록 생성 전에 상대방에게 알립니다.
- Repository 통합 테스트는 MySQL Testcontainers 사용을 우선합니다.

공통 환경변수:

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

- 실제 비밀번호와 API 키는 Git에 올리지 않습니다.
- `application.yml`에는 환경변수 참조만 작성합니다.
- 로컬 전용 설정 파일은 Git에서 제외하고 안전한 예시 파일만 공유합니다.
- 테스트에서 실제 정보나루·OpenAI API를 호출하지 않습니다.

## 6. WakeBook MVP 기준

| 영역 | 합의 기준 |
|---|---|
| 잠자는 도서 | 최근 12개월 부산 지역, 동일 분야 대출량 하위 30% |
| 후보 제외 | 인기 상위 100위, 출간 6개월 이내, 필수 정보 누락 도서 |
| 책장 | 사용자 컬렉션 |
| 읽기 상태 | `WISH`, `READING`, `COMPLETED`, `REVISIT` |
| 중복 저장 | 같은 책장에 같은 ISBN 한 번만 허용 |
| 추천 점수 | 백엔드의 결정적 계산식으로 처리 |
| AI 역할 | 키워드·추천 이유·비교 설명·큐레이션 문구 생성 |

- 정보나루에서 실제로 확보할 수 없는 필드는 추측하지 않고 API 명세를 조정합니다.
- 외부 API는 Controller에서 직접 호출하지 않고 Provider/Adapter로 분리합니다.
- 실제 연동 전 Fake Provider로 API와 테스트를 먼저 완성합니다.


