# WakeBook API 명세서

> Base URL: `http://localhost:8080/api`  
> 형식: `application/json; charset=UTF-8`  
> 인증: 로그인 후 `Authorization: Bearer {accessToken}` 헤더를 사용합니다.

## 변경 이력

### 2026-08-19 — 공개 큐레이션 조회 및 공개 상태 기본값 보완

- **4.5 `GET /curations`, `GET /curations/{curationId}`(신규)**: 인증 없이 공개 큐레이션의 목록과 상세를 조회할 수 있습니다. 비공개 큐레이션은 목록에서 제외되며 상세 조회 시 `404 CURATION_001`을 반환합니다.
- **6.3 `POST /librarian/curations`**: `isPublic`을 생략하면 비공개로 저장합니다.
- **6.4 `PATCH /librarian/curations/{curationId}`**: `isPublic`을 생략하면 기존 공개 상태를 유지합니다.

### 2026-08-19 — 최종보고서 기준 독서 시간 추천 복원

- **4.2 `POST /recommendations`**: 최종보고서 4.1절에 맞춰 요청의 `readingTime`과 응답의 `timeMatch`를 복원했습니다.
- `readingTime`은 필수이며 `SHORT`, `MEDIUM`, `LONG`, `SLOW` 중 하나를 사용합니다.
- 최종 점수는 키워드 0.35, 목적 0.20, 분위기 0.15, 독서 시간 0.10, 도서 정보 품질 0.10, 발견 가치 0.10의 가중치를 사용합니다.

### 2026-08-15 (4) — 도서 상세를 알라딘 우선으로

**API 계약은 그대로입니다. 응답을 채우는 출처만 바뀌었습니다.**

정보나루 호출의 대부분이 도서 상세(`srchDtlList`)였습니다. 후보군 산출은 후보 1권마다 1회를 쓰기 때문입니다.
이 조회를 **알라딘 `ItemLookUp` → 실패·소개글 없음일 때만 정보나루** 순서로 바꿨습니다(`FallbackBookDetailProvider`).

수영구 30권 + 강서 20권 전수로 잰 알라딘 응답(2026-08-15):

| | 알라딘 | 정보나루(기존) |
|---|---|---|
| 수록률 | 50/50 (100%) | — |
| 품질 기준(제목·저자·출판사·소개글 30자) 통과 | 50/50 | — |
| 소개글 길이 중앙값 | 148자 | 166자 |
| `qualityScore` 서로 다른 값 | 22종 | 24종 |

- **소개글이 짧아지고 표지·저자 표기가 알라딘 형식으로 바뀝니다.** 정렬에 쓰는 `qualityScore`의 변별력은 유지됩니다(22종 대 24종).
- **알라딘에 없거나 소개글이 없으면 정보나루로 넘어갑니다.** 상업 서점 DB라 절판·비유통 장서는 못 잡을 수 있어서, 사서 CSV 경로에서는 폴백 비율이 올라갈 수 있습니다.
- 캐시(`bookDetails`, 7일)는 폴백 판단 **앞단**에 걸려 있어 알라딘으로 해결된 건도 캐시됩니다.
- 알라딘이 `OptResult`(`Toc`·`fulldescription`)를 무시하는 것을 확인했습니다. 목차 기능은 여전히 불가입니다(`docs/tasks.md`).

**같이 고친 버그 — 한도 초과가 엉뚱한 오류로 보이던 문제**

정보나루 한도 초과 응답(`{"response":{"errCode":"outOflimit", …}}`)에는 `numFound`가 없는데 DTO가 primitive `long`이라
**`errCode` 검사에 닿기도 전에 파싱이 터지고 있었습니다.** 그래서 한도가 소진되면:

- 인기 도서·도서 검색이 `503 BOOK_003`(한도 초과)이 아니라 `502 BOOK_002`(조회 실패)로 나왔습니다.
- 더 위험하게는, **도서관별 대출 순위 조회가 조용히 빈 집합을 반환**했습니다. 후보군 산출이 "대출 순위에 아무것도 없다"고 판단해 장서 전체를 저이용으로 오인할 수 있는 상태였습니다.

`loanItemSrch`·`srchBooks`·`itemSrch`·`libSrch` 네 DTO의 건수 필드를 `Long`으로 바꿔 해결했습니다.
실제로 한도가 소진된 상태에서 인기 도서·도서 검색이 `503 BOOK_003`으로 정확히 응답하는 것을 확인했습니다.

**사서가 만든 후보군을 다른 사람이 덮어쓸 수 없습니다**

후보군은 사용자별이 아니라 **도서관별로 하나**입니다(`hidden_books`에 사용자 컬럼이 없습니다).
누가 산출하면 로그인하지 않은 방문자까지 그 결과를 보는 공용 자원인데,
`POST /libraries/{libraryCode}/hidden-books`는 로그인만 하면 **아무 도서관이나** 산출할 수 있었습니다.
사서가 실제 대출건수(CSV)로 만들어 둔 정확한 후보군을, 순위 기반 API 산출로 조용히 갈아치울 수 있는 상태였습니다.

이제 기존 후보군의 `source`가 `CSV_UPLOAD`이면 **그 도서관 소속 사서만** 다시 만들 수 있습니다(`403 AUTH_002`).
후보군이 아직 없는 도서관은 이용자가 직접 열어 보는 것이 이 경로의 목적이므로 그대로 누구나 만들 수 있고,
`LIBRARY_API`·`DEMO_SEED`로 만들어진 후보군도 7일 쿨다운으로 충분해 제한하지 않습니다.

**대출 순위를 일부만 받으면 후보군 산출을 중단합니다**

후보군은 `장서 목록 − 대출 순위 5,000위`라서, 순위 집합이 모자라면 **빠진 순위의 인기 도서가 그대로 "잠자는 책" 후보**가 됩니다.
지금까지는 페이지 조회가 실패하면 그때까지 모은 부분 집합을 그대로 돌려주고 있었고(주석에는 "보수적으로 동작"이라 적혀 있었지만 실제로는 반대 방향),
그 부분 집합이 `libraryLoanRanking` 캐시에 **1일간 그대로 재사용**됐습니다.

이제 페이지마다 **최대 3회까지 재시도**(0.5초·1초 간격)하고, 그래도 못 받으면 `502 BOOK_002`로 작업을 실패시킵니다. 예외를 던지므로 캐시에도 남지 않습니다.
한도 초과는 정상 응답(`errCode`)으로 오기 때문에 재시도 대상이 아닙니다 — 하루치가 소진된 상태에서 헛되이 더 두드리지 않고 바로 `503`으로 끊습니다.
캐시 키에 조회 기간이 빠져 있어 기간이 달라도 같은 값을 쓰던 문제도 함께 고쳤습니다(`{libraryCode, startDt, endDt}`).
docs가 비었거나 페이지가 가득 차지 않은 경우는 **정상 종료**입니다(장서가 5,000권 미만인 도서관).

### 2026-08-15 (3) — 정보나루 호출량 절감

**정보나루 일일 호출 한도는 IP 미등록 시 500건입니다.** 초과하면 HTTP 200에 아래 본문이 옵니다.

```json
{"response":{"errCode":"outOflimit","error":"1일 500건 이상 요청 시 IP 등록이 필요합니다."}}
```

지금까지는 이 응답을 그대로 파싱해 "결과 없음"으로 처리하고 있었습니다.
**운영 전에 정보나루 마이페이지에서 개발 PC와 배포 서버 IP를 등록해야 합니다.**

호출 비용(실측):

| 동작 | 정보나루 호출 |
|---|---|
| 인기 도서·검색 1페이지 | 1 |
| 도서 상세(지역 없이) | 1 → **0** (2026-08-15 (4) 이후 알라딘이 받음) |
| **도서 상세(지역 포함)** | **2N** (소장 도서관 N곳마다 `bookExist` + `itemSrch`) |
| 오늘의 책·추천·재탐색 | 0 (DB에서 읽음) |
| **후보군 산출 1회** | 약 40~50 → **약 10** (상세 조회가 알라딘으로 빠짐) |

- **오류 처리**: 모든 정보나루 응답의 `errCode`를 검사합니다. 한도 초과는 `503 BOOK_003`으로 명확히 알립니다.
- **캐싱**: 도서 상세(7일), 소장 도서관(3일), 청구기호(7일), 인기 도서(12시간), 도서관 목록(7일), 도서관 대출 순위(1일). 대출 가능 여부만 10분으로 짧게 잡아 실시간성을 유지합니다.
- **산출 제한**: 같은 도서관은 7일 안에 재산출 불가(`409 JOB_003`), 사용자당 하루 3곳(`429 JOB_004`). 사서의 CSV 업로드는 스스로 정확한 데이터를 넣는 일이므로 제한하지 않습니다.
- **후보 0건이면 기존 후보군을 지우지 않습니다.** 이전에는 산출 결과가 비면 멀쩡한 기존 후보군까지 사라졌습니다.
- **7. 오류 코드**: `BOOK_003`, `JOB_003`, `JOB_004` 추가.

### 2026-08-15 (2) — 사서 CSV 없이도 동작하도록

사서가 CSV를 올린 도서관에서만 추천이 되던 구조를 바꿨습니다. 정보나루 API를 확인한 결과
**도서관별 장서 목록(`itemSrch`)과 도서관별 대출 순위(`loanItemSrch` + `libCode`)를 모두 받을 수 있어서**,
그 차집합("장서에는 있는데 대출 순위에는 없는 책")으로 후보군을 자동 산출할 수 있습니다.
2026-07-29에 "다운로드 파일로만 제공된다"고 적었던 판단을 수정합니다.

- **3.6 `GET /libraries`**: 응답에 `source`(`CSV_UPLOAD` / `LIBRARY_API` / `DEMO_SEED`) 추가. 후보군 산출 근거가 다르면 정밀도도 다르므로 화면에서 구분합니다.
- **3.7-1 `GET /libraries/{libraryCode}/hidden-books`(신규)**: 그 도서관의 잠자는 도서 목록. 저장된 후보군을 그대로 읽어 외부 호출이 없습니다. 그동안 후보군을 보려면 오늘의 책 1권·랜덤 1권이나 AI 추천을 거쳐야만 했습니다.
- **3.7 `GET /libraries/directory`(신규)**: 정보나루 지역별 전체 도서관 목록(`libSrch`). 후보군을 새로 만들 도서관을 고를 때 씁니다.
- **3.8 `POST /libraries/{libraryCode}/hidden-books`(신규)**: 정보나루 API만으로 그 도서관의 후보군을 산출합니다. 인증 필요, `202 Accepted` + 작업 정보를 반환합니다. **사서 CSV로 만들어진 후보군은 소속 사서만 다시 만들 수 있습니다(`403 AUTH_002`, 2026-08-15 (4)에서 추가).**
- **3.9 `GET /hidden-book-jobs/{jobId}`(신규)**: 후보군 산출 작업의 진행 상태.
- **6.5 `POST /librarian/hidden-books/upload`**: **응답이 바뀌었습니다.** 수 분이 걸리는 산출을 요청 안에서 끝내지 않고 `202 Accepted` + 작업 정보를 반환합니다. 진행 상태는 3.9로 확인합니다.
- **4.1 `POST /ai/keywords`**: **인증 불필요로 바꿨습니다.** 서비스의 진입점인 키워드 탐색이 로그인 뒤에 숨어 있으면 첫 방문자가 아무것도 볼 수 없습니다. 응답은 24시간 캐싱됩니다.
- **4.2 `POST /recommendations`**: 요청에 `limit`(선택, 기본 9, 최대 30) 추가. 이전에는 후보군 전체를 정렬만 해서 반환했습니다. 응답에 `libraryName`, `callNumber`, `shelfName` 추가.
- **4.3 `POST /recommendations/compare`**: 응답을 24시간 캐싱합니다.
- **4.4 `POST /recommendations/explore`**: 응답에 `libraryName`, `callNumber`, `shelfName` 추가, 결과는 상위 9권으로 제한.
- **3.4/3.5 `GET /books/today`, `GET /books/random`**: 응답에 `author`, `description`, `libraryName`, `callNumber`, `shelfName`, `source` 추가. `reason`은 후보군 산출 때가 아니라 **처음 조회될 때 생성해 저장**하므로, 아직 생성되지 않았으면 `null`일 수 있습니다.
- **6.1 `GET /librarian/dashboard`**: `exhibitionLoanRate` **필드를 제거**했습니다. 전시 이후 대출을 추적하는 구조가 없어 계산이 불가능한데 상수 0을 지표처럼 내려 주고 있었습니다.
- **7. 오류 코드**: `JOB_001`(같은 도서관 작업이 이미 진행 중), `JOB_002`(작업 없음) 추가.

### 2026-08-15

프론트엔드-백엔드 연동 작업 중 확인된 보안 문제와 누락 기능을 반영했습니다.

- **6.5 `POST /librarian/hidden-books/upload`**: **요청 계약이 바뀌었습니다.** 업로드 대상 도서관을 요청값이 아니라 **인증된 사서 계정의 소속 도서관**으로 결정합니다. `libraryName`은 더 이상 받지 않고(사서 계정 값 사용), `libraryCode`는 선택값이 되어 자기 소속과 일치하는지 검증만 합니다. 다른 도서관 코드로 요청하면 `403 AUTH_002`입니다. 이전에는 사서 권한만 있으면 임의의 도서관 후보군을 덮어쓸 수 있었습니다(중간보고서 8.1 보완 항목).
- **3.6 `GET /libraries`(신규)**: 잠자는 도서 후보군이 등록된 도서관 목록을 반환합니다(인증 불필요). 이용자가 도서관 코드를 직접 입력할 수 없으므로, 추천이 실제로 동작하는 도서관만 골라 보여 주기 위한 API입니다.
- **7. 오류 코드**: 실제로 반환하고 있던 `BOOKSHELF_001`~`BOOKSHELF_004`를 표에 추가했습니다.
- CORS 허용 출처를 `app.cors.allowed-origins`(환경변수 `CORS_ALLOWED_ORIGINS`)로 설정할 수 있게 했습니다. 배포 시 프론트엔드 도메인을 반드시 등록해야 합니다.

### 2026-07-30

사서의 소속 도서관을 자유입력 텍스트(`libraryName`)만으로는 `hidden_books`(도서관 코드로 구분)와 연결할 수 없어서, 회원가입에 `libraryCode`(사서 필수)를 추가했습니다. 이에 따라 아래 API의 **요청/응답 계약이 바뀌었습니다**.

- **2.1 `POST /auth/signup`**: 사서 가입 시 `libraryCode`(도서관정보나루 도서관 코드) 필수 입력 추가.
- **2.2 `POST /auth/login`**, **2.3 `GET /auth/me`**: 응답의 `user`/`data`에 `libraryCode` 필드 추가.
- **6.1 `GET /librarian/dashboard`**, **6.2 `POST /librarian/curations/generate`(신규)**: 별도 파라미터 없이, 로그인한 사서의 `libraryCode`로 자기 도서관의 "잠자는 도서" 후보군(6.5로 업로드된 `hidden_books`)을 자동으로 찾습니다. `libraryCode`가 없는 사서 계정은 후보군이 빈 것으로 처리됩니다.
- `exhibitionLoanRate`(전시 대출률)는 실제 대출 추적 데이터가 없어 현재 고정값(0)을 반환합니다. 중간보고서(2026-07-31) 이후 실데이터 연동 예정입니다.
- **6.3/6.4 큐레이션 저장/조회/수정/삭제(신규)**: 사서가 만든 큐레이션을 저장·조회·수정·삭제합니다.
- **4.2 `POST /recommendations`**: 불필요하다고 판단해 `readingTime` 필드를 제거했습니다. 응답의 `timeMatch`도 함께 제거되고, 그만큼의 가중치(0.10)는 `keywordRelevance`(0.35→0.45)로 흡수했습니다.

### 2026-07-29

"잠자는 도서" 후보군(3.4/3.5/4.2/4.4가 추천하는 도서)이 **도서관별로 분리 관리**되도록 변경했습니다. 사서가 자기 도서관의 정보나루 "장서 대출목록" CSV를 직접 업로드하면, 그 도서관의 후보군만 즉시 갱신됩니다(다른 도서관 데이터는 영향 없음). 이에 따라 아래 API들의 **요청 계약이 바뀌었습니다** — 프론트 작업 시 참고해 주세요.

- **3.4 `GET /books/today`**, **3.5 `GET /books/random`**: 쿼리 파라미터 `libraryCode`(필수) 추가.
- **4.2 `POST /recommendations`**, **4.4 `POST /recommendations/explore`**: 요청 바디에 `libraryCode`(필수) 필드 추가.
- **6.5 `POST /librarian/hidden-books/upload`(신규)**: 사서가 CSV 파일을 업로드해 자기 도서관의 후보군을 갱신하는 API 추가.
- 이전 설계는 "도서관 1곳을 서버 설정값으로 고정 + 매달 자동 배치"였으나, 정보나루가 이 통계를 실시간 API가 아니라 도서관별 다운로드 파일로만 제공한다는 점을 확인해 위 방식으로 변경했습니다.

## 1. 공통 규칙

### 응답 형식

```json
{ "success": true, "message": "요청이 완료되었습니다.", "data": {} }
```

```json
{ "success": false, "code": "AUTH_001", "message": "로그인이 필요합니다.", "data": null }
```

| 역할 | 코드 | 권한 |
|---|---|---|
| 일반 사용자 | `USER` | 도서 탐색, 추천, 책장 관리 |
| 사서 | `LIBRARIAN` | 일반 사용자 기능 및 큐레이션 관리 |

## 2. 인증 API

### 2.1 회원가입 -> 완료

`POST /auth/signup`

일반 사용자 또는 사서 계정을 생성합니다. 사서는 `libraryName`, `department`를 필수 입력합니다.

```json
{
  "role": "LIBRARIAN",
  "name": "김도서",
  "email": "librarian@wakebook.kr",
  "password": "Password!123",
  "nickname": "책지기",
  "libraryName": "부산대학교 도서관",
  "libraryCode": "121018",
  "department": "자료운영팀"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| role | String | O | `USER` 또는 `LIBRARIAN` |
| name | String | O | 이름 |
| email | String | O | 로그인 이메일, 중복 불가 |
| password | String | O | 비밀번호 |
| nickname | String | X | 사용자 별칭 |
| libraryName | String | 사서 | 소속 도서관(표시용) |
| libraryCode | String | 사서 | 도서관정보나루 도서관 코드. 6.1/6.2가 이 값으로 자기 도서관의 후보군을 찾음(**추가됨, 2026-07-30**) |
| department | String | 사서 | 담당 부서 |

**201 Created**

```json
{ "success": true, "message": "회원가입이 완료되었습니다.", "data": { "id": 12, "role": "LIBRARIAN", "name": "김도서" } }
```

### 2.2 로그인 -> 완료

`POST /auth/login`

```json
{ "email": "librarian@wakebook.kr", "password": "Password!123" }
```

```json
{
  "success": true,
  "message": "로그인되었습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "user": { "id": 12, "name": "김도서", "role": "LIBRARIAN", "libraryName": "부산대학교 도서관", "libraryCode": "121018" }
  }
}
```

### 2.3 내 정보 조회 -> 완료

`GET /auth/me` · 인증 필요

```json
{ "success": true, "data": { "id": 12, "name": "김도서", "nickname": "책지기", "role": "LIBRARIAN", "libraryName": "부산대학교 도서관", "libraryCode": "121018" } }
```

## 3. 도서 탐색 API

### 3.1 인기 도서 조회 -> 완료

`GET /books/popular?page=1&size=12&category=문학&gender=ALL&age=20`

| 쿼리 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| page | Number | 1 | 페이지 번호 |
| size | Number | 12 | 페이지당 개수 |
| category | String | ALL | 분야 |
| gender | String | ALL | `ALL`, `M`, `F` |
| age | Number | - | 연령대 |

```json
{
  "success": true,
  "data": {
    "content": [{ "isbn": "9788996991342", "title": "미움받을 용기", "author": "기시미 이치로", "cover": "https://...", "rank": 1, "loanCount": 1284 }],
    "page": 1, "totalPages": 8, "totalElements": 89
  }
}
```

### 3.2 도서 검색 -> 완료

`GET /books/search?keyword=심리&page=1&size=12`

제목, 저자, 출판사, 키워드를 통합 검색합니다. 응답은 인기 도서 조회와 같은 페이지 형식(`content`, `page`, `totalPages`, `totalElements`)을 사용합니다.

```json
{
  "success": true,
  "data": {
    "content": [{ "isbn": "9788996991342", "title": "미움받을 용기", "author": "기시미 이치로", "cover": "https://..." }],
    "page": 1, "totalPages": 3, "totalElements": 32
  }
}
```

### 3.3 도서 상세 조회 -> 완료

`GET /books/{isbn}?region=21`

| 쿼리 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| region | String | X | 도서관정보나루 지역 코드(예: 서울 11, 부산 21). 없으면 `libraries`는 빈 배열, `availability`는 `UNKNOWN` |

```json
{
  "success": true,
  "data": {
    "isbn": "9788996991342", "title": "미움받을 용기", "author": "기시미 이치로", "publisher": "인플루엔셜", "publishedYear": 2014,
    "cover": "https://...", "description": "아들러 심리학을 바탕으로...", "tableOfContents": ["트라우마를 부정하라"],
    "availability": "AVAILABLE", "libraries": [{ "name": "부산시립시민도서관", "callNumber": "189.1-기58ㅁ", "available": true }]
  }
}
```

`availability`: `AVAILABLE`(대출 가능한 소장 도서관 있음), `UNAVAILABLE`(소장은 하지만 대출 불가), `UNKNOWN`(`region` 미지정 등으로 소장 정보를 조회하지 않음)

> `tableOfContents`는 알라딘(Aladin) Open API로 조회합니다. 도서관·공공데이터가 아닌 민간 서점 API이며, 목차 정보가 없는 책이면 빈 배열을 반환합니다.

### 3.4 오늘의 잠자는 책

`GET /books/today?libraryCode=121018` **(`libraryCode` 필수 — 변경됨, 2026-07-29)**

매일 선정되는 저이용·고품질 도서 한 권과 추천 이유를 반환합니다.

| 쿼리 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| libraryCode | String | O | 도서관정보나루 도서관 코드. 사서가 6.5로 업로드해둔 도서관이어야 후보가 나옵니다. |

```json
{ "success": true, "data": { "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "author": "박상미", "cover": "https://...", "reason": "나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요.", "description": "정보나루 소개글...", "libraryName": "부산광역시 강서도서관", "callNumber": "813.6-박51관", "shelfName": "[강서구]종합자료실", "source": "LIBRARY_API", "keywords": ["인간관계", "심리"] } }
```

`libraryCode`에 해당하는 후보가 없으면 `404 BOOK_001`을 반환합니다.

### 3.5 우연히 발견하기

`GET /books/random?libraryCode=121018` **(`libraryCode` 필수 — 변경됨, 2026-07-29)**

품질 검증을 통과한 잠자는 도서 중 한 권을 무작위로 반환합니다. 쿼리·응답 형식은 3.4와 동일합니다.

```json
{ "success": true, "data": { "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "author": "박상미", "cover": "https://...", "reason": "나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요.", "description": "정보나루 소개글...", "libraryName": "부산광역시 강서도서관", "callNumber": "813.6-박51관", "shelfName": "[강서구]종합자료실", "source": "LIBRARY_API", "keywords": ["인간관계", "심리"] } }
```

### 3.6 도서관 목록 (신규, 2026-08-15)

`GET /libraries` · 인증 불필요

잠자는 도서 후보군(6.5로 업로드된 `hidden_books`)이 등록된 도서관만 반환합니다. 3.4/3.5/4.2/4.4에 넘길 `libraryCode`를
이용자가 외울 수 없으므로, 프론트엔드는 이 목록에서 도서관을 고르게 합니다. 목록이 비어 있으면 아직 어떤 사서도
장서 CSV를 올리지 않은 상태이며, 추천 기능 전체가 빈 결과를 반환합니다.

```json
{
  "success": true,
  "data": [
    {
      "libraryCode": "121018", "libraryName": "부산광역시 금정도서관",
      "source": "CSV_UPLOAD", "hiddenBookCount": 24
    }
  ]
}
```

| source | 산출 근거 | 정밀도 |
|---|---|---|
| `CSV_UPLOAD` | 사서가 올린 "장서 대출목록" CSV의 실제 대출건수 | 가장 정확 |
| `LIBRARY_API` | 정보나루 장서 목록(`itemSrch`) − 대출 순위 상위 5,000권(`loanItemSrch`) | 대출건수는 알 수 없는 간접 신호 |
| `DEMO_SEED` | 체험용으로 미리 넣어 둔 데이터(마이그레이션) | 실데이터지만 갱신되지 않음 |

### 3.7 도서관 검색 (신규, 2026-08-15)

`GET /libraries/directory?region=21` · 인증 불필요

정보나루에 등록된 지역별 도서관 목록(`libSrch`)입니다. 후보군이 아직 없는 도서관을 골라
3.8로 직접 만들 때 씁니다. `region`은 시도 코드(11 서울, 21 부산, …)입니다.

```json
{
  "success": true,
  "data": [
    {
      "libraryCode": "121020", "libraryName": "부산광역시 강서도서관",
      "address": "부산광역시 강서구 공항로811번길 10", "bookCount": 118432
    }
  ]
}
```

### 3.7-1 도서관별 잠자는 도서 목록 (신규, 2026-08-15)

`GET /libraries/{libraryCode}/hidden-books?page=1&size=12` · 인증 불필요

저장된 후보군을 그대로 반환합니다. **AI도 정보나루도 호출하지 않습니다.** 정보 품질이 좋은 순으로 정렬합니다.
`reason`은 그 도서가 오늘의 책·랜덤으로 노출될 때 생성되므로 아직 `null`일 수 있고, 이때는 `description`(정보나루 소개글)을 쓰면 됩니다.
응답은 3.1과 같은 페이지 형식이며 `size`는 최대 50입니다.

```json
{
  "success": true,
  "data": {
    "content": [{
      "isbn": "9791167903754", "title": "이름 없는 것들의 밤", "author": "정보라 (지은이)",
      "cover": "https://...", "reason": null, "description": "정보라의 연작소설집...",
      "libraryName": "부산광역시 강서도서관", "callNumber": "813.6-정45이B",
      "shelfName": "[강서구]종합자료실", "source": "DEMO_SEED", "keywords": ["문학", "한국문학", "소설"]
    }],
    "page": 1, "totalPages": 2, "totalElements": 20
  }
}
```

### 3.8 후보군 자동 산출 (신규, 2026-08-15)

`POST /libraries/{libraryCode}/hidden-books` · 인증 필요 · `202 Accepted`

사서의 CSV 업로드 없이 정보나루 API만으로 그 도서관의 잠자는 도서 후보군을 만듭니다.

1. `loanItemSrch`(+`libCode`)로 그 도서관 대출 순위 상위 5,000권의 ISBN을 모읍니다.
2. `itemSrch`(+`libCode`+기간)로 장서 목록을 훑으며 **순위에 없고 청구기호가 있는** 장서를 후보로 뽑습니다.
3. 후보마다 `srchDtlList`로 소개글이 있는지 확인해 통과한 것만 저장합니다.

수 분이 걸리므로 접수만 하고 작업 정보를 반환합니다. 같은 도서관 작업이 이미 돌고 있으면 `409 JOB_001`입니다.

```json
{
  "success": true,
  "message": "후보군 산출을 시작했습니다.",
  "data": {
    "jobId": 12, "libraryCode": "121020", "libraryName": null,
    "source": "LIBRARY_API", "status": "PENDING",
    "totalCandidates": 0, "processedCount": 0, "savedCount": 0, "message": null
  }
}
```

### 3.9 후보군 산출 작업 조회 (신규, 2026-08-15)

`GET /hidden-book-jobs/{jobId}` · 인증 필요

`status`는 `PENDING` → `RUNNING` → `SUCCEEDED` 또는 `FAILED`로 바뀝니다.
3.8과 6.5가 모두 이 API로 진행 상태를 알립니다.

```json
{
  "success": true,
  "data": {
    "jobId": 12, "libraryCode": "121020", "libraryName": "부산광역시 강서도서관",
    "source": "LIBRARY_API", "status": "SUCCEEDED",
    "totalCandidates": 90, "processedCount": 90, "savedCount": 30,
    "message": "후보 90권을 검토해 30권을 후보군으로 저장했습니다."
  }
}
```

## 4. AI 추천 API

### 4.1 핵심 키워드 생성

`POST /ai/keywords` · **인증 불필요 (변경됨, 2026-08-15)** · 응답 24시간 캐싱

```json
{ "isbn": "9788996991342" }
```

```json
{ "success": true, "data": { "keywords": ["인간관계", "자존감", "심리", "행복", "용기"] } }
```

### 4.2 잠자는 도서 추천

`POST /recommendations`

선택 키워드, 독서 목적·분위기·예상 독서 시간을 반영해 잠자는 도서를 추천합니다.

```json
{
  "isbn": "9788996991342",
  "libraryCode": "121018",
  "keywords": ["인간관계", "심리"],
  "purpose": "마음의 위로",
  "mood": "따뜻한",
  "readingTime": "MEDIUM",
  "limit": 6
}
```

| 필드 | 값 |
|---|---|
| libraryCode | 도서관정보나루 도서관 코드 **(필수 — 변경됨, 2026-07-29)**. 이 도서관에 업로드된 후보군(6.5)만 대상으로 추천합니다. |
| purpose | `마음의 위로`, `새로운 관점`, `실용적인 해결책`, `깊이 있는 사유` |
| mood | `따뜻한`, `담백한`, `유쾌한`, `사색적인` |
| readingTime | 예상 독서 시간 **(필수 — 복원, 2026-08-19)**. `SHORT`(30분 이내), `MEDIUM`(1~2시간), `LONG`(주말 동안), `SLOW`(천천히 읽기) |
| limit | 돌려받을 추천 수 **(선택 — 신규, 2026-08-15)**. 기본 9, 최대 30. 이전에는 후보군 전체를 반환했습니다. |

```json
{
  "success": true,
  "data": [{
    "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "author": "박상미", "cover": "https://...",
    "score": 93, "keywordRelevance": 95, "purposeMatch": 92, "moodMatch": 90, "timeMatch": 88, "discoveryValue": 89,
    "reason": "나를 지키면서 타인과 건강하게 연결되는 구체적인 연습법을 만나 보세요.",
    "keywords": ["인간관계", "심리", "자존감"],
    "libraryName": "부산광역시 강서도서관", "callNumber": "813.6-정45이B", "shelfName": "[강서구]종합자료실"
  }]
}
```

`score`는 `keywordRelevance × 0.35 + purposeMatch × 0.20 + moodMatch × 0.15 + timeMatch × 0.10 + 도서 정보 품질 × 0.10 + discoveryValue × 0.10`으로 계산합니다.
`timeMatch`는 후보 도서의 제목·키워드·소개글을 근거로 AI가 선택한 독서 시간이나 속도와의 적합도를 평가한 값입니다. 페이지 수·글자 수 데이터가 없으므로 정확한 독서 소요 시간을 의미하지 않습니다.

### 4.3 인기·잠자는 도서 비교

`POST /recommendations/compare`

```json
{ "popularBook": "9788996991342", "hiddenBook": "9788960867450" }
```

```json
{
  "success": true,
  "data": {
    "commonKeywords": ["인간관계", "심리", "자존감"],
    "difference": "두 책 모두 타인의 시선에서 벗어나는 태도를 다루며, 추천 도서는 일상 관계의 사례에 더 집중합니다.",
    "popularBookProfile": { "difficulty": "보통", "style": "철학적 대화" },
    "hiddenBookProfile": { "difficulty": "쉬움", "style": "일상 사례" }
  }
}
```

### 4.4 연관 조건 재탐색

`POST /recommendations/explore`

```json
{ "isbn": "9788960867450", "libraryCode": "121018", "type": "DEEPER" }
```

| 필드 | 값 |
|---|---|
| libraryCode | 도서관정보나루 도서관 코드 **(필수 — 변경됨, 2026-07-29)** |
| type | `SIMILAR_TOPIC`, `SAME_MOOD`, `EASIER`, `DEEPER`, `OPPOSITE_VIEW` |

주어진 `isbn`을 기준으로 `type` 조건에 맞는 잠자는 도서를 다시 추천합니다. 응답은 조건에 맞는 정도(`relevance`)순으로 정렬된 배열입니다.

```json
{
  "success": true,
  "data": [{
    "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "author": "박상미", "cover": "https://...",
    "score": 91, "relevance": 92, "discoveryValue": 89,
    "reason": "더 깊이 있는 사유를 원한다면 이 책의 후반부 사례가 도움이 됩니다.",
    "keywords": ["인간관계", "심리", "자존감"],
    "libraryName": "부산광역시 강서도서관", "callNumber": "813.6-정45이B", "shelfName": "[강서구]종합자료실"
  }]
}
```

### 4.5 공개 큐레이션 조회 -> 완료

- `GET /curations?page=1&size=9`: 공개 큐레이션 목록을 최신순으로 조회합니다.
- `GET /curations/{curationId}`: 공개 큐레이션의 도서 구성과 사서 코멘트를 조회합니다.

목록의 `page` 기본값은 1이고 `size` 기본값은 9, 최댓값은 30입니다. 두 API 모두 인증이 필요하지 않으며 `isPublic=true`인 큐레이션만 반환합니다. 비공개 큐레이션 ID 또는 존재하지 않는 ID를 상세 조회하면 동일하게 `404 CURATION_001`을 반환합니다.

목록 응답은 다음 페이지 형식을 사용합니다.

```json
{
  "success": true,
  "data": {
    "content": [{
      "id": 5,
      "title": "괜찮지 않아도 괜찮은 우리에게",
      "description": "불안한 오늘을 지나가는 청년들을 위한 책",
      "bookCount": 5,
      "cover": "https://...",
      "createdAt": "2026-08-19T12:00:00"
    }],
    "page": 1,
    "totalPages": 1,
    "totalElements": 1
  }
}
```

상세 응답은 6.3의 큐레이션 상세 응답과 같습니다.

## 5. 나의 책장 API

모든 API는 인증이 필요합니다.

### 5.1 책장 및 도서 목록 조회

`GET /bookshelves`

```json
{
  "success": true,
  "data": [{
    "id": 1, "name": "읽고 싶은 책", "type": "DEFAULT", "bookCount": 4,
    "books": [{ "id": 101, "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "status": "WISH", "cover": "https://..." }]
  }]
}
```

### 5.2 사용자 컬렉션 생성

`POST /bookshelves`

```json
{ "name": "마음을 돌보는 책", "description": "천천히 읽고 싶은 책 모음" }
```

### 5.3 책장에 도서 저장

`POST /bookshelves/{shelfId}/books`

```json
{ "isbn": "9788960867450", "status": "WISH" }
```

`status`: `WISH`, `READING`, `COMPLETED`, `REVISIT`

### 5.4 읽기 상태 변경

`PATCH /bookshelves/{shelfId}/books/{bookId}`

```json
{ "status": "READING" }
```

### 5.5 책장 도서 삭제

`DELETE /bookshelves/{shelfId}/books/{bookId}`

### 5.6 컬렉션 수정 및 삭제

`PATCH /bookshelves/{shelfId}` · `DELETE /bookshelves/{shelfId}`

## 6. 사서 API

모든 API는 `LIBRARIAN` 권한이 필요합니다. 일반 사용자는 `403 FORBIDDEN`을 반환합니다.

### 6.1 사서 대시보드 -> 완료

`GET /librarian/dashboard`

로그인한 사서의 `libraryCode`로 자기 도서관의 `hidden_books` 후보군을 찾아 집계합니다(별도 파라미터 불필요). `libraryCode`가 없는 계정은 `hiddenBookCount: 0`, `popularKeywords: []`로 응답합니다. `exhibitionLoanRate`는 대출 추적 데이터가 없어 현재 고정값(0)을 반환합니다(추후 실데이터 연동 예정).

```json
{
  "success": true,
  "data": {
    "hiddenBookCount": 128, "monthlyCurationCount": 12, "exhibitionLoanRate": 86,
    "popularKeywords": ["청년", "불안", "관계"],
    "recentCurations": [{ "id": 5, "title": "괜찮지 않아도 괜찮은 우리에게", "bookCount": 5, "isPublic": true }]
  }
}
```

### 6.2 AI 큐레이션 초안 생성 -> 완료

`POST /librarian/curations/generate`

`topic`만 필수이며, 나머지는 선택 입력입니다. 로그인한 사서의 `libraryCode`에 해당하는 `hidden_books` 후보군(6.5로 업로드된 데이터) 중에서 골라 추천하며, 후보가 없으면 `404 BOOK_001`을 반환합니다. 이 응답은 초안일 뿐 저장되지 않으며, 마음에 들면 6.3으로 저장합니다.

```json
{
  "topic": "청년의 불안", "targetAge": "20대", "mood": "따뜻한", "category": "인문", "bookCount": 5,
  "excludedKeywords": ["취업"], "purpose": "전시 큐레이션"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| topic | String | O | 큐레이션 주제 |
| targetAge | String | X | 대상 연령대 |
| mood | String | X | 원하는 분위기 |
| category | String | X | 분야 |
| bookCount | Number | X | 선정할 도서 수(기본값 5, 후보 수만큼 상한) |
| excludedKeywords | String[] | X | 제외할 키워드 |
| purpose | String | X | 큐레이션 목적 |

```json
{
  "success": true,
  "data": {
    "title": "괜찮지 않아도 괜찮은 우리에게", "description": "불안한 오늘을 지나가는 청년들을 위한 다정한 책의 목소리.",
    "hashtags": ["#청년", "#불안", "#마음돌봄"],
    "books": [{ "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "reason": "관계 불안을 구체적인 사례로 풀어냅니다." }]
  }
}
```

### 6.3 큐레이션 저장 및 조회 -> 완료

- `POST /librarian/curations`: 큐레이션 저장
- `GET /librarian/curations?page=1&size=10`: 내 큐레이션 목록 조회(응답은 3.1과 같은 페이지 형식)
- `GET /librarian/curations/{curationId}`: 큐레이션 상세 조회

저장 요청의 `hashtags`는 6.2 초안 응답에서만 쓰이는 값으로, 큐레이션 자체에는 저장되지 않습니다. `isPublic`은 선택값이며 생략하면 안전하게 비공개(`false`)로 저장됩니다.

```json
{ "title": "괜찮지 않아도 괜찮은 우리에게", "description": "...", "isPublic": true, "books": [{ "isbn": "9788960867450", "displayOrder": 1, "comment": "관계 불안을 다정하게 다룹니다." }] }
```

```json
{
  "success": true,
  "data": {
    "id": 5, "title": "괜찮지 않아도 괜찮은 우리에게", "description": "...", "isPublic": true, "bookCount": 1,
    "books": [{ "id": 9, "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "cover": "https://...", "displayOrder": 1, "comment": "관계 불안을 다정하게 다룹니다." }],
    "createdAt": "2026-07-30T12:00:00"
  }
}
```

### 6.4 큐레이션 수정 및 삭제 -> 완료

- `PATCH /librarian/curations/{curationId}`: 제목, 소개, 공개 여부, 도서 순서 수정(요청 형식은 6.3 저장과 동일하며, `books` 목록으로 기존 도서 구성을 전체 교체합니다. `isPublic`을 생략하면 기존 공개 상태를 유지합니다)
- `DELETE /librarian/curations/{curationId}`: 큐레이션 삭제

다른 사서가 만든 큐레이션에 접근하면 `404 CURATION_001`을 반환합니다.

### 6.5 장서/대출 데이터 업로드 (신규, 2026-07-29)

`POST /librarian/hidden-books/upload` · `multipart/form-data`

사서가 [도서관 정보나루 오픈데이터](https://data4library.kr/openDataV)에서 자기 도서관의 "장서 대출목록" CSV를 다운받아 업로드하면, 그 도서관의 "잠자는 도서" 후보군(3.4/3.5/4.2/4.4가 추천 대상으로 쓰는 데이터)을 즉시 다시 산출합니다. 같은 `libraryCode`로 이미 저장돼 있던 이전 후보군은 삭제되고 새 결과로 교체됩니다(다른 도서관 데이터는 영향 없음).

| 필드 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| libraryCode | String | X | **변경됨(2026-08-15).** 보내면 인증된 사서의 소속 도서관 코드와 같은지 검증만 합니다. 다르면 `403 AUTH_002`. 생략하면 사서의 소속 도서관에 저장합니다. |
| file | File | O | 정보나루에서 다운받은 "장서 대출목록" CSV 원본 파일 |

`libraryName`은 더 이상 요청으로 받지 않고, 사서 계정의 소속 도서관명을 사용합니다.
소속 도서관 코드가 없는 사서 계정은 `403 AUTH_002`입니다.

> **처리 시간 주의:** 후보 도서마다 정보나루 상세 조회가 필요해 산출에 수 분이 걸립니다.
> 후보군을 만들 때 AI를 호출하지는 않습니다. 추천 이유는 그 도서가 실제로 화면에 노출될 때 만들어 저장합니다.

**응답이 바뀌었습니다(2026-08-15).** CSV 파싱까지만 요청 안에서 하고, 후보마다 외부 API를 호출하는
산출 작업은 비동기로 넘긴 뒤 `202 Accepted`로 작업 정보를 반환합니다. 진행 상태는 3.9로 확인합니다.

```json
{
  "success": true,
  "message": "장서 데이터를 접수했습니다. 후보군을 만드는 중입니다.",
  "data": {
    "jobId": 7, "libraryCode": "121018", "libraryName": "부산광역시 금정도서관",
    "source": "CSV_UPLOAD", "status": "PENDING",
    "totalCandidates": 0, "processedCount": 0, "savedCount": 0, "message": null
  }
}
```

## 7. 주요 오류 코드

| HTTP | 코드 | 상황 |
|---:|---|---|
| 400 | `VALIDATION_001` | 요청값 누락 또는 형식 오류 |
| 401 | `AUTH_001` | 인증 토큰 없음 또는 만료 |
| 403 | `AUTH_002` | 역할 권한 없음 |
| 404 | `BOOK_001` | 도서를 찾을 수 없음 |
| 404 | `CURATION_001` | 큐레이션을 찾을 수 없음 |
| 404 | `BOOKSHELF_001` | 컬렉션을 찾을 수 없음 |
| 403 | `BOOKSHELF_002` | 기본 책장은 수정·삭제할 수 없음 |
| 409 | `BOOKSHELF_003` | 이미 해당 책장에 저장된 도서 |
| 404 | `BOOKSHELF_004` | 책장에 저장된 도서를 찾을 수 없음 |
| 409 | `JOB_001` | 같은 도서관의 후보군 산출 작업이 이미 진행 중 |
| 404 | `JOB_002` | 후보군 산출 작업을 찾을 수 없음 |
| 409 | `JOB_003` | 최근 7일 안에 이미 산출한 도서관 |
| 429 | `JOB_004` | 사용자당 하루 산출 횟수(3곳) 초과 |
| 503 | `BOOK_003` | 정보나루 일일 호출 한도 초과(IP 미등록 시 500건) |
| 409 | `AUTH_003` | 이미 사용 중인 이메일 |
| 502 | `BOOK_002` | 정보나루 등 외부 도서 API 연동 실패 |
| 500 | `AI_001` | AI 추천 생성 실패 |
| 500 | `SERVER_001` | 그 외 서버 내부 오류 |
