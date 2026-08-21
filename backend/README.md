# WakeBook Backend

WakeBook의 Spring Boot REST API 서버입니다.

## 기술 스택

| 항목 | 설정 |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.0 |
| Gradle | 9.5.1 · Groovy DSL |
| Database | MySQL 8.4 · Flyway |
| 인증 | JWT Bearer · HS256 · Access Token 1시간 |
| API Base URL | `http://localhost:8080/api` |

Gradle은 별도로 설치하지 않고 저장소의 Wrapper를 사용합니다.

## 실행 준비

JDK 21과 MySQL 8.4가 필요합니다. Windows에서는 프로젝트를 한글과 공백이 없는
경로에 두는 것을 권장합니다.

### 데이터베이스

MySQL 관리자 계정으로 다음 SQL을 한 번 실행합니다.

```sql
CREATE DATABASE IF NOT EXISTS wakebook
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'wakebook_user'@'localhost'
    IDENTIFIED BY '개인_비밀번호';

GRANT ALL PRIVILEGES ON wakebook.* TO 'wakebook_user'@'localhost';
FLUSH PRIVILEGES;
```

### 환경변수

| 변수 | 값 | 필수 |
|---|---|:---:|
| `DB_URL` | `jdbc:mysql://localhost:3306/wakebook` | O |
| `DB_USERNAME` | `wakebook_user` | O |
| `DB_PASSWORD` | 로컬 DB 비밀번호 | O |
| `JWT_SECRET` | UTF-8 기준 32바이트 이상의 랜덤 문자열 | O |
| `JWT_EXPIRATION` | Access Token 만료 시간(ms), 기본값 `3600000` | X |
| `OPENAI_API_KEY` | 트렌드 맥락·도서 추천 문구 생성용 OpenAI 키 | O |
| `KAKAO_API_KEY` | ISBN 도서 상세 1차 조회용 카카오 REST API 키 | O |
| `ALADIN_TTB_KEY` | 카카오 상세가 부족할 때 보강하는 알라딘 TTB 키 | X |
| `DATA4LIBRARY_API_KEY` | 장서·대출 순위와 최종 상세 폴백용 정보나루 키 | O |
| `NAVER_CLIENT_ID` | 네이버 개발자센터 애플리케이션 Client ID (`검색`, `데이터랩`) | X |
| `NAVER_CLIENT_SECRET` | 네이버 개발자센터 애플리케이션 Client Secret | X |
| `TREND_SCHEDULER_ENABLED` | 일일 추천 스케줄러 사용 여부, 기본값 `true` | X |
| `TREND_SCHEDULE_CRON` | 일일 생성 cron, 기본값 KST 05:00 | X |

PowerShell 설정 예시:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/wakebook"
$env:DB_USERNAME = "wakebook_user"
$env:DB_PASSWORD = "개인_비밀번호"

$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:JWT_EXPIRATION = "3600000"
```

환경변수는 백엔드를 실행하는 터미널·IDE 실행 설정 또는 `backend/.env`에 지정합니다. Spring Boot는
`backend/.env`를 선택적으로 읽습니다. 위 PowerShell 설정은 현재 터미널에만 적용되며,
서버 재시작 후에도 기존 토큰을 유지하려면 같은 `JWT_SECRET`을 사용해야 합니다.
비밀번호와 JWT 비밀키는 저장소에 커밋하지 않습니다.

## 실행과 테스트

```powershell
cd backend
.\gradlew.bat bootRun
```

```powershell
cd backend
.\gradlew.bat clean test
```

애플리케이션 실행 시 Flyway가 스키마를 적용합니다. 테스트는 H2 인메모리 DB를 사용하므로
로컬 MySQL 데이터를 변경하지 않습니다.

트렌드 스케줄러는 매일 05:00 KST에 실행하며, 서버가 그 시각 이후에 시작되거나 이전 작업 도중
재시작된 경우에도 시작 직후 오늘 배치를 자동으로 생성·복구합니다. 사서 운영 도구에서는 생성 상태를
확인하고 `오늘 추천 생성/다시 생성`을 요청할 수 있습니다.

### 실제 외부 API 검증

실제 `.env` 인증 정보를 사용하는 테스트는 일반 `test`와 분리해서 명시적으로 실행합니다. 인증키는 테스트 출력에 기록하지 않습니다.

```powershell
$env:RUN_LIVE_API_TESTS='true'
.\gradlew.bat test --tests com.wakebook.external.ExternalApiLiveSmokeTest --rerun-tasks
```

네이버 연동은 2026-08-22에 네이버 개발자센터 Open API 규격으로 변경했습니다. 로컬 계약 테스트는 공식 경로·헤더·응답 파싱을 검증하고, `ExternalApiLiveSmokeTest.actualNaverOpenApiReturnsNewsAndSearchTrend`가 실제 키를 사용하는 선택 실행형 테스트입니다. 공개 큐레이션은 H2 인메모리 DB와 랜덤 포트를 사용하는 실제 HTTP 통합 테스트로 확인합니다.

```powershell
.\gradlew.bat test --tests com.wakebook.curation.CurationPublicIntegrationTest
```

도서관별 잠금의 실제 MySQL 동시성은 이름이 `_test`로 끝나는 전용 DB에서만 실행합니다.

```powershell
$env:MYSQL_TEST_URL='jdbc:mysql://localhost:3306/wakebook_mysql_test'
$env:MYSQL_TEST_USERNAME='root'
$env:MYSQL_TEST_PASSWORD=''
.\gradlew.bat mysqlTest
```

### Flyway 검증 오류 주의

기존 DB에 적용된 마이그레이션 SQL을 나중에 수정하면 `Migration checksum mismatch`로 애플리케이션 시작이 차단됩니다. 적용 완료된 파일을 다시 수정하지 말고 새 버전 마이그레이션을 추가해야 합니다. 실제 스키마 확인 없이 `flyway repair`를 실행하지 않습니다.

현재 로컬 개발 DB에서는 `V202608150001`의 적용 체크섬과 저장소 체크섬이 다른 상태가 확인됐습니다. 폐기 가능한 개인 DB는 재생성하고, 공유 DB는 적용 당시 SQL과 현재 스키마를 비교한 뒤 팀 합의로 처리합니다. 상세 검증 기록은 [통합 및 실제 API 검증 결과](../docs/통합및실API검증결과.md)를 참고하세요.

## API

요청과 응답은 `application/json`을 사용합니다.

| 기능 | Method | Endpoint | 성공 상태 |
|---|---|---|---:|
| 회원가입 | `POST` | `/auth/signup` | `201` |
| 로그인 | `POST` | `/auth/login` | `200` |
| 내 정보 조회 | `GET` | `/auth/me` | `200` |
| 일일 트렌드 추천 조회 | `GET` | `/trends/daily?libraryCode={code}` | `200` |
| 사서 추천 재생성 | `POST` | `/librarian/trends/refresh` | `202` |
| 사서 생성 상태 조회 | `GET` | `/librarian/trends/batches/{batchId}` | `200` |
| 공개 큐레이션 목록 | `GET` | `/curations?page=1&size=9` | `200` |
| 공개 큐레이션 상세 | `GET` | `/curations/{curationId}` | `200` |

잠자는 도서 상세는 `카카오 → 알라딘 → 정보나루` 순으로 조회합니다. 후보군은 기본 200권이며
KDC 0~9 대분류를 가능한 한 균등하게 구성합니다. 트렌드 추천은 전역 인기 순위만 사용하지 않고,
도서관별 도서 매칭 점수를 함께 반영해 최대 5개 트렌드를 선택합니다.

인증이 필요한 API에는 로그인 응답의 토큰을 전달합니다.

```http
Authorization: Bearer {accessToken}
```

요청·응답 필드와 오류 코드는 [API 명세](../docs/API명세.md)를 기준으로 합니다.

## 관련 문서

- [백엔드 팀 합의사항](<./WakeBook_Backend_Team_Setup .md>)
- [프로젝트 안내](../docs/README.md)
- [API 명세](../docs/API명세.md)
- [차별화 아이디어 API 명세](../docs/차별화아이디어API명세.md)
