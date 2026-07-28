# WakeBook API 명세서

> Base URL: `http://localhost:8080/api`  
> 형식: `application/json; charset=UTF-8`  
> 인증: 로그인 후 `Authorization: Bearer {accessToken}` 헤더를 사용합니다.

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

### 2.1 회원가입

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
| libraryName | String | 사서 | 소속 도서관 |
| department | String | 사서 | 담당 부서 |

**201 Created**

```json
{ "success": true, "message": "회원가입이 완료되었습니다.", "data": { "id": 12, "role": "LIBRARIAN", "name": "김도서" } }
```

### 2.2 로그인

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
    "user": { "id": 12, "name": "김도서", "role": "LIBRARIAN", "libraryName": "부산대학교 도서관" }
  }
}
```

### 2.3 내 정보 조회

`GET /auth/me` · 인증 필요

```json
{ "success": true, "data": { "id": 12, "name": "김도서", "nickname": "책지기", "role": "LIBRARIAN", "libraryName": "부산대학교 도서관" } }
```

## 3. 도서 탐색 API

### 3.1 인기 도서 조회

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

### 3.2 도서 검색

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

### 3.3 도서 상세 조회

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

`GET /books/today`

매일 선정되는 저이용·고품질 도서 한 권과 추천 이유를 반환합니다.

```json
{ "success": true, "data": { "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "cover": "https://...", "reason": "나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요.", "keywords": ["인간관계", "심리"] } }
```

### 3.5 우연히 발견하기

`GET /books/random`

품질 검증을 통과한 잠자는 도서 중 한 권을 무작위로 반환합니다.

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

선택 키워드, 독서 목적·분위기·시간을 반영해 잠자는 도서를 추천합니다.

```json
{
  "isbn": "9788996991342",
  "keywords": ["인간관계", "심리"],
  "purpose": "마음의 위로",
  "mood": "따뜻한",
  "readingTime": "MEDIUM"
}
```

| 필드 | 값 |
|---|---|
| purpose | `마음의 위로`, `새로운 관점`, `실용적인 해결책`, `깊이 있는 사유` |
| mood | `따뜻한`, `담백한`, `유쾌한`, `사색적인` |
| readingTime | `SHORT`, `MEDIUM`, `LONG`, `SLOW` |

```json
{
  "success": true,
  "data": [{
    "isbn": "9788960867450", "title": "관계에도 연습이 필요합니다", "author": "박상미", "cover": "https://...",
    "score": 93, "keywordRelevance": 95, "purposeMatch": 92, "moodMatch": 90, "timeMatch": 88, "discoveryValue": 89,
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
{ "isbn": "9788960867450", "type": "DEEPER" }
```

`type`: `SIMILAR_TOPIC`, `SAME_MOOD`, `EASIER`, `DEEPER`, `OPPOSITE_VIEW`

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

### 6.1 사서 대시보드

`GET /librarian/dashboard`

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

### 6.2 AI 큐레이션 초안 생성

`POST /librarian/curations/generate`

```json
{
  "topic": "청년의 불안", "targetAge": "20대", "mood": "따뜻한", "category": "인문", "bookCount": 5,
  "excludedKeywords": ["취업"], "purpose": "전시 큐레이션"
}
```

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

### 6.3 큐레이션 저장 및 조회

- `POST /librarian/curations`: 큐레이션 저장
- `GET /librarian/curations?page=1&size=10`: 내 큐레이션 목록 조회
- `GET /librarian/curations/{curationId}`: 큐레이션 상세 조회

```json
{ "title": "괜찮지 않아도 괜찮은 우리에게", "description": "...", "isPublic": true, "books": [{ "isbn": "9788960867450", "displayOrder": 1, "comment": "관계 불안을 다정하게 다룹니다." }] }
```

### 6.4 큐레이션 수정 및 삭제

- `PATCH /librarian/curations/{curationId}`: 제목, 소개, 공개 여부, 도서 순서 수정
- `DELETE /librarian/curations/{curationId}`: 큐레이션 삭제

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
