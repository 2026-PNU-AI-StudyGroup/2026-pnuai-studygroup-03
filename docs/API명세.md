# API Specification

## Base URL

```
http://localhost:8080/api
```

---

# 1. Auth API

## 1.1 회원가입

### POST

```
POST /auth/signup
```

### Request

```json
{
  "role": "USER",
  "name": "홍길동",
  "email": "test@test.com",
  "password": "1234",
  "nickname": "길동"
}
```

### Librarian Request

```json
{
  "role": "LIBRARIAN",
  "name": "홍길동",
  "email": "test@test.com",
  "password": "1234",
  "libraryName": "부산대학교 도서관",
  "department": "자료운영팀"
}
```

### Response

```json
{
  "status": 201,
  "message": "회원가입 성공"
}
```

---

## 1.2 로그인

### POST

```
POST /auth/login
```

### Request

```json
{
  "email":"test@test.com",
  "password":"1234"
}
```

### Response

```json
{
  "accessToken":"JWT_TOKEN",
  "role":"USER"
}
```

---

## 1.3 내 정보 조회

### GET

```
GET /auth/me
```

### Header

```
Authorization : Bearer JWT
```

### Response

```json
{
    "id":1,
    "name":"홍길동",
    "role":"USER"
}
```

---

# 2. Book API

## 2.1 인기 도서 조회

### GET

```
GET /books/popular
```

### Query

```
?page=1

&category=문학

&gender=ALL

&age=20
```

### Response

```json
[
  {
    "isbn":"9780000000",
    "title":"미움받을 용기",
    "author":"기시미 이치로",
    "cover":"...",
    "rank":1
  }
]
```

---

## 2.2 오늘의 잠자는 책

### GET

```
GET /books/today
```

### Response

```json
{
    "isbn":"97812345",
    "title":"...",
    "reason":"AI 추천"
}
```

---

## 2.3 랜덤 탐색

### GET

```
GET /books/random
```

---

## 2.4 도서 상세 조회

### GET

```
GET /books/{isbn}
```

### Response

```json
{
    "isbn":"",
    "title":"",
    "author":"",
    "publisher":"",
    "description":"",
    "cover":""
}
```

---

## 2.5 도서 검색

### GET

```
GET /books/search
```

### Query

```
keyword=심리
```

---

# 3. AI API

## 3.1 키워드 생성

### POST

```
POST /ai/keywords
```

### Request

```json
{
    "isbn":"97812345"
}
```

### Response

```json
[
    "심리",
    "자존감",
    "인간관계"
]
```

---

## 3.2 도서 추천

### POST

```
POST /recommendations
```

### Request

```json
{
    "isbn":"97812345",

    "keywords":[
        "심리",
        "인간관계"
    ],

    "purpose":"위로",

    "mood":"따뜻한",

    "readingTime":"SHORT"
}
```

### Response

```json
[
    {
        "isbn":"978111",

        "title":"",

        "score":91,

        "reason":"..."
    }
]
```

---

## 3.3 도서 비교

### POST

```
POST /recommendations/compare
```

### Request

```json
{
    "popularBook":"978111",

    "hiddenBook":"978222"
}
```

### Response

```json
{
    "commonKeyword":[
        "심리",
        "자존감"
    ],

    "difference":"..."
}
```

---

# 4. Bookshelf API

## 4.1 책장 조회

### GET

```
GET /bookshelf
```

---

## 4.2 책 저장

### POST

```
POST /bookshelf
```

### Request

```json
{
    "isbn":"978111",

    "status":"WISH"
}
```

---

## 4.3 읽기 상태 변경

### PATCH

```
PATCH /bookshelf/{id}
```

### Request

```json
{
    "status":"READING"
}
```

---

## 4.4 책 삭제

### DELETE

```
DELETE /bookshelf/{id}
```

---

## 4.5 사용자 컬렉션 생성

### POST

```
POST /collections
```

### Request

```json
{
    "name":"힐링도서"
}
```

---

## 4.6 컬렉션에 도서 추가

### POST

```
POST /collections/{id}/books
```

### Request

```json
{
    "isbn":"978111"
}
```

---

# 5. Librarian API

## 5.1 대시보드 조회

### GET

```
GET /librarian/dashboard
```

---

## 5.2 큐레이션 생성

### POST

```
POST /librarian/curation
```

### Request

```json
{
    "topic":"청년의 불안",

    "target":"20대",

    "count":5
}
```

### Response

```json
{
    "title":"괜찮지 않아도 괜찮은 우리에게",

    "description":"...",

    "books":[]
}
```

---

## 5.3 큐레이션 저장

### POST

```
POST /librarian/curation/save
```

---

## 5.4 큐레이션 수정

### PATCH

```
PATCH /librarian/curation/{id}
```

---

## 5.5 큐레이션 삭제

### DELETE

```
DELETE /librarian/curation/{id}
```

---

# Response Format

## Success

```json
{
    "success":true,

    "message":"success",

    "data":{}
}
```

---

## Error

```json
{
    "success":false,

    "message":"Not Found",

    "errorCode":"BOOK_NOT_FOUND"
}
```

---

# HTTP Status

|Code|Description|
|-----|-----------|
|200|OK|
|201|Created|
|204|No Content|
|400|Bad Request|
|401|Unauthorized|
|403|Forbidden|
|404|Not Found|
|500|Internal Server Error|