# WakeBook Backend

WakeBook의 Spring Boot REST API 서버입니다.

## 기술 환경

| 항목 | 버전 및 설정 |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.0 |
| Gradle | 9.5.1, Groovy DSL |
| Database | MySQL 8.4 |
| DB Migration | Flyway |
| 기본 주소 | `http://localhost:8080/api` |

Gradle은 별도로 설치하지 않고 저장소에 포함된 Gradle Wrapper를 사용합니다.

## 1. 저장소 받은 후 준비할 것

각 개발자 PC에 다음 프로그램이 필요합니다.

- JDK 21
- MySQL 8.4
- Git
- 선택 사항: IntelliJ IDEA, VS Code, Postman

Windows에서는 프로젝트를 한글이나 공백이 없는 경로에 두는 것을 권장합니다.

```text
C:\dev\wakebook
```

설치 확인:

```powershell
java -version
javac -version

cd backend
.\gradlew.bat --version
```

Java와 Gradle의 JVM 버전이 모두 21이어야 합니다.

## 2. 로컬 MySQL 준비

MySQL 서비스가 실행 중인지 확인합니다.

```powershell
Get-Service MySQL84
```

`Status`가 `Running`이어야 합니다. 중지되어 있다면 관리자 PowerShell에서 실행합니다.

```powershell
Start-Service MySQL84
```

MySQL root 계정으로 접속합니다.

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p
```

설치할 때 설정한 root 비밀번호를 입력한 뒤 다음 SQL을 실행합니다.  
`안전한_개인_비밀번호`는 각자 사용할 로컬 비밀번호로 변경합니다.

```sql
CREATE DATABASE IF NOT EXISTS wakebook
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'wakebook_user'@'localhost'
    IDENTIFIED BY '안전한_개인_비밀번호';

GRANT ALL PRIVILEGES ON wakebook.* TO 'wakebook_user'@'localhost';
FLUSH PRIVILEGES;

SHOW GRANTS FOR 'wakebook_user'@'localhost';
EXIT;
```

애플리케이션에서는 root 계정을 사용하지 않고 `wakebook_user`를 사용합니다.

## 3. 환경변수 설정

백엔드는 다음 환경변수로 MySQL에 접속합니다.

| 변수 | 예시 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/wakebook` | MySQL 접속 주소 |
| `DB_USERNAME` | `wakebook_user` | 로컬 MySQL 계정 |
| `DB_PASSWORD` | 개인 비밀번호 | 로컬 MySQL 비밀번호 |

새 PowerShell을 열고 현재 창에 환경변수를 설정합니다.

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/wakebook"
$env:DB_USERNAME = "wakebook_user"
$env:DB_PASSWORD = "위에서_설정한_개인_비밀번호"
```

이 설정은 현재 PowerShell 창에서만 유지됩니다. 같은 창에서 백엔드를 실행해야 합니다.

환경변수 확인:

```powershell
$env:DB_URL
$env:DB_USERNAME
```

비밀번호가 화면이나 캡처에 노출될 수 있으므로 `$env:DB_PASSWORD`는 출력하지 않습니다.

### IntelliJ에서 설정

1. `Run → Edit Configurations`
2. `WakebookApplication` 실행 설정 선택
3. `Environment variables`에 다음 값을 입력

```text
DB_URL=jdbc:mysql://localhost:3306/wakebook;DB_USERNAME=wakebook_user;DB_PASSWORD=개인비밀번호
```

실제 비밀번호를 `application.properties`, README, GitHub, PR 또는 단체 채팅에 올리지 않습니다.

> Spring Boot는 `.env` 파일을 기본으로 자동 로드하지 않습니다. `.env` 파일만 만들어 두면 설정이 적용되지 않습니다.

## 4. 백엔드 실행

환경변수를 설정한 PowerShell에서 실행합니다.

```powershell
cd backend
.\gradlew.bat bootRun
```

다음 로그가 표시되면 정상입니다.

```text
Tomcat started on port 8080
Started WakebookApplication
```

최초 실행 시 Flyway가 자동으로 다음 테이블을 만듭니다.

```text
flyway_schema_history
users
```

서버 종료:

```text
Ctrl + C
```

## 5. 테스트

```powershell
cd backend
.\gradlew.bat clean test
```

성공 기준:

```text
BUILD SUCCESSFUL
```

테스트는 H2 인메모리 DB를 사용하므로 로컬 MySQL 데이터를 변경하지 않습니다.

## 6. Postman으로 회원가입 확인

백엔드를 실행한 상태에서 요청을 만듭니다.

- Method: `POST`
- URL: `http://localhost:8080/api/auth/signup`
- Header: `Content-Type: application/json`
- Body: `raw → JSON`

사서 회원가입 요청:

```json
{
  "role": "LIBRARIAN",
  "name": "김도서",
  "email": "librarian-test@wakebook.kr",
  "password": "Password!123",
  "nickname": "책지기",
  "libraryName": "부산대학교 도서관",
  "department": "자료운영팀"
}
```

정상 응답은 `201 Created`입니다.

```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "id": 1,
    "role": "LIBRARIAN",
    "name": "김도서"
  }
}
```

`id`는 DB 상태에 따라 달라집니다. 같은 이메일로 다시 요청하면 `409 Conflict`가 반환됩니다.

일반 사용자 요청:

```json
{
  "role": "USER",
  "name": "김독자",
  "email": "reader-test@wakebook.kr",
  "password": "Password!123",
  "nickname": "책벌레"
}
```

일반 사용자는 `libraryName`과 `department`가 필요하지 않습니다.

## 7. 문제 해결

### `Communications link failure`

- MySQL84 서비스가 실행 중인지 확인합니다.
- `DB_URL`의 포트가 `3306`인지 확인합니다.
- MySQL Configurator에서 설정한 포트와 일치하는지 확인합니다.

### `Access denied for user`

- `DB_USERNAME`, `DB_PASSWORD`를 다시 확인합니다.
- MySQL에서 `wakebook_user@localhost` 권한을 확인합니다.
- 환경변수를 설정한 PowerShell과 실행한 PowerShell이 같은 창인지 확인합니다.

### `JAVA_HOME is set to an invalid directory`

- JDK 21 설치 경로를 확인합니다.
- 새 PowerShell을 열어 `java -version`과 `$env:JAVA_HOME`을 다시 확인합니다.

### `409 Conflict`

이미 가입된 이메일입니다. Postman 요청의 이메일을 새로운 값으로 바꿉니다.

## 8. 문서

- [백엔드 팀 합의사항](<./WakeBook_Backend_Team_Setup .md>)
- [전체 프로젝트 안내](../docs/README.md)
- [API 명세](../docs/API명세.md)

