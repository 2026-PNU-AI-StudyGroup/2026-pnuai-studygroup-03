# WakeBook 백엔드 팀 합의사항

백엔드 개발자가 공통으로 지켜야 하는 기술·구현 규칙입니다.

## 1. 개발 환경

| 항목 | 합의 |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.0 |
| Build | Gradle 9.5.1 · Groovy DSL · Wrapper 사용 |
| Package | `com.wakebook` |
| Database | MySQL 8.4 · Flyway |
| API | `http://localhost:8080/api` |
| Frontend | `http://localhost:5173` |
| Encoding / Time zone | UTF-8 / `Asia/Seoul` |
| DTO / Test | Java `record` 우선 / JUnit 5 |

Spring Boot와 Gradle 버전은 각각 `build.gradle`, Gradle Wrapper에 고정합니다.
버전 또는 공통 의존성 변경은 팀 합의 후 별도 PR로 진행합니다.

## 2. 코드 구조

기능 중심 패키지(`auth`, `user`, `book`, `bookshelf`, `recommendation`, `curation`,
`external`, `common`)를 사용하며 기본 흐름은 다음과 같습니다.

```text
controller → service → repository
                ↓
           domain / dto
```

- Controller에는 요청 검증과 응답 변환만 둡니다.
- 비즈니스 로직과 트랜잭션은 Service에서 처리합니다.
- Entity를 API 응답으로 직접 반환하지 않습니다.
- Java는 `camelCase`/`PascalCase`, DB는 `snake_case`를 사용합니다.
- API 계약 변경 시 같은 PR에서 `docs/API명세.md`를 수정합니다.

## 3. API 규칙

`docs/API명세.md`를 API 계약의 기준으로 사용합니다.

```json
{ "success": true, "message": "요청이 완료되었습니다.", "data": {} }
```

```json
{ "success": false, "code": "AUTH_001", "message": "로그인이 필요합니다.", "data": null }
```

- 모든 응답에 `message`를 포함합니다.
- 생성 성공은 `201`, 그 외 성공은 기본적으로 `200`을 사용합니다.
- 요청 오류 `400`, 인증 실패 `401`, 권한 없음 `403`, 없음 `404`, 중복 `409`를 사용합니다.
- 외부 페이지는 1부터 시작하고 내부에서 `page - 1`로 변환합니다.
- 페이지 기본 크기는 12, 최대 크기는 50입니다.
- ISBN은 `String`으로 처리합니다.

## 4. 인증과 권한

| 항목 | 합의 |
|---|---|
| 인증 | JWT Bearer Token |
| 구현 | Spring Security · Nimbus JOSE JWT |
| 서명 | HS256, `JWT_SECRET` 32바이트 이상 |
| Claim | `sub`: 사용자 ID, `role`: 사용자 역할 |
| 비밀번호 | BCrypt |
| Access Token | 1시간 |
| Refresh Token | MVP 제외 |

- 공개: 회원가입, 로그인, 도서 탐색 API
- 로그인 필요: AI 추천·비교, 책장
- 사서 전용: 대시보드, 큐레이션 관리
- 사용자 ID는 요청값이 아닌 JWT 인증 정보에서 가져옵니다.
- 로그인 실패 시 이메일 존재 여부를 구분하지 않는 동일한 응답을 반환합니다.

## 5. DB와 설정

- DB 스키마는 Flyway로만 변경합니다.
- 마이그레이션은 `VyyyyMMddHHmm__설명.sql` 형식을 사용하며 번호 생성을 팀에 공유합니다.
- 설정 파일에는 환경변수 참조만 두고 실제 비밀번호·비밀키·API 키를 커밋하지 않습니다.
- 현재 공통 환경변수는 다음과 같습니다.

```env
DB_URL=jdbc:mysql://localhost:3306/wakebook
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=
JWT_EXPIRATION=3600000
```

외부 API와 프론트엔드 설정은 해당 기능을 구현할 때 환경변수로 추가합니다.

## 6. 테스트와 외부 연동

- Service와 Controller 동작은 단위 테스트로 검증합니다.
- 공통 흐름은 H2 통합 테스트로 검증하고, MySQL 전용 동작은 Testcontainers를 우선합니다.
- 외부 API는 Controller에서 직접 호출하지 않고 Provider/Adapter로 분리합니다.
- 테스트에서는 외부 API를 실제 호출하지 않고 Fake 또는 Mock을 사용합니다.
