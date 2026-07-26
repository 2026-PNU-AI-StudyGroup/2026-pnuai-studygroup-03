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

환경변수는 백엔드를 실행하는 터미널 또는 IDE 실행 설정에 지정합니다. Spring Boot는
`.env` 파일을 자동으로 읽지 않습니다. 위 PowerShell 설정은 현재 터미널에만 적용되며,
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

## API

요청과 응답은 `application/json`을 사용합니다.

| 기능 | Method | Endpoint | 성공 상태 |
|---|---|---|---:|
| 회원가입 | `POST` | `/auth/signup` | `201` |
| 로그인 | `POST` | `/auth/login` | `200` |

인증이 필요한 API에는 로그인 응답의 토큰을 전달합니다.

```http
Authorization: Bearer {accessToken}
```

요청·응답 필드와 오류 코드는 [API 명세](../docs/API명세.md)를 기준으로 합니다.

## 관련 문서

- [백엔드 팀 합의사항](<./WakeBook_Backend_Team_Setup .md>)
- [프로젝트 안내](../docs/README.md)
- [API 명세](../docs/API명세.md)
