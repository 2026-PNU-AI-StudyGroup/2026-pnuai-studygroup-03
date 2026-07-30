# WakeBook API 명세서

> Base URL: `http://localhost:8080/api`  
> 형식: `application/json; charset=UTF-8`  
> 인증: 로그인 후 `Authorization: Bearer {accessToken}` 헤더를 사용합니다.

## 변경 이력

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
{ "success": true, "data": { "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "cover": "https://...", "reason": "나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요.", "keywords": ["인간관계", "심리"] } }
```

`libraryCode`에 해당하는 후보가 없으면 `404 BOOK_001`을 반환합니다.

### 3.5 우연히 발견하기

`GET /books/random?libraryCode=121018` **(`libraryCode` 필수 — 변경됨, 2026-07-29)**

품질 검증을 통과한 잠자는 도서 중 한 권을 무작위로 반환합니다. 쿼리·응답 형식은 3.4와 동일합니다.

```json
{ "success": true, "data": { "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "cover": "https://...", "reason": "나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요.", "keywords": ["인간관계", "심리"] } }
```

## 4. AI 추천 API

### 4.1 핵심 키워드 생성

`POST /ai/keywords`

```json
{ "isbn": "9788996991342" }
```

```json
{ "success": true, "data": { "keywords": ["인간관계", "자존감", "심리", "행복", "용기"] } }
```

### 4.2 잠자는 도서 추천

`POST /recommendations`

선택 키워드, 독서 목적·분위기를 반영해 잠자는 도서를 추천합니다.

```json
{
  "isbn": "9788996991342",
  "libraryCode": "121018",
  "keywords": ["인간관계", "심리"],
  "purpose": "마음의 위로",
  "mood": "따뜻한"
}
```

| 필드 | 값 |
|---|---|
| libraryCode | 도서관정보나루 도서관 코드 **(필수 — 변경됨, 2026-07-29)**. 이 도서관에 업로드된 후보군(6.5)만 대상으로 추천합니다. |
| purpose | `마음의 위로`, `새로운 관점`, `실용적인 해결책`, `깊이 있는 사유` |
| mood | `따뜻한`, `담백한`, `유쾌한`, `사색적인` |

```json
{
  "success": true,
  "data": [{
    "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "author": "박상미", "cover": "https://...",
    "score": 93, "keywordRelevance": 95, "purposeMatch": 92, "moodMatch": 90, "discoveryValue": 89,
    "reason": "나를 지키면서 타인과 건강하게 연결되는 구체적인 연습법을 만나 보세요.",
    "keywords": ["인간관계", "심리", "자존감"]
  }]
}
```

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
    "keywords": ["인간관계", "심리", "자존감"]
  }]
}
```

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

저장 요청의 `hashtags`는 6.2 초안 응답에서만 쓰이는 값으로, 큐레이션 자체에는 저장되지 않습니다.

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

- `PATCH /librarian/curations/{curationId}`: 제목, 소개, 공개 여부, 도서 순서 수정(요청 형식은 6.3 저장과 동일하며, `books` 목록으로 기존 도서 구성을 전체 교체합니다)
- `DELETE /librarian/curations/{curationId}`: 큐레이션 삭제

다른 사서가 만든 큐레이션에 접근하면 `404 CURATION_001`을 반환합니다.

### 6.5 장서/대출 데이터 업로드 (신규, 2026-07-29)

`POST /librarian/hidden-books/upload` · `multipart/form-data`

사서가 [도서관 정보나루 오픈데이터](https://data4library.kr/openDataV)에서 자기 도서관의 "장서 대출목록" CSV를 다운받아 업로드하면, 그 도서관의 "잠자는 도서" 후보군(3.4/3.5/4.2/4.4가 추천 대상으로 쓰는 데이터)을 즉시 다시 산출합니다. 같은 `libraryCode`로 이미 저장돼 있던 이전 후보군은 삭제되고 새 결과로 교체됩니다(다른 도서관 데이터는 영향 없음).

| 필드 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| libraryCode | String | O | 도서관정보나루 도서관 코드 |
| libraryName | String | O | 도서관명(표시용) |
| file | File | O | 정보나루에서 다운받은 "장서 대출목록" CSV 원본 파일 |

```json
{
  "success": true,
  "message": "장서/대출 데이터를 반영했습니다.",
  "data": { "libraryCode": "121018", "libraryName": "부산광역시 금정도서관", "totalRows": 3200, "savedCount": 24 }
}
```

`totalRows`는 CSV에서 읽은 전체 행 수, `savedCount`는 대출건수 하위·품질 검증을 통과해 실제로 후보군에 저장된 도서 수입니다.

## 7. 주요 오류 코드

| HTTP | 코드 | 상황 |
|---:|---|---|
| 400 | `VALIDATION_001` | 요청값 누락 또는 형식 오류 |
| 401 | `AUTH_001` | 인증 토큰 없음 또는 만료 |
| 403 | `AUTH_002` | 역할 권한 없음 |
| 404 | `BOOK_001` | 도서를 찾을 수 없음 |
| 404 | `CURATION_001` | 큐레이션을 찾을 수 없음 |
| 409 | `AUTH_003` | 이미 사용 중인 이메일 |
| 502 | `BOOK_002` | 정보나루 등 외부 도서 API 연동 실패 |
| 500 | `AI_001` | AI 추천 생성 실패 |
| 500 | `SERVER_001` | 그 외 서버 내부 오류 |
