# 일 단위 트렌드 연계 잠자는 도서 추천 API 명세 및 구현 계획

> 작성일: 2026-08-15  
> 대상: WakeBook 백엔드·프론트엔드 공동 개발  
> Base URL: `http://localhost:8080/api`  
> 상태: 구현 전 계약 초안(MVP 권장안)

## 1. 목적

중간보고서 8.2의 아래 두 아이디어는 하나의 파이프라인으로 구현한다.

1. 최근 24시간의 인기 검색 트렌드를 매일 자동 수집한다.
2. 트렌드의 맥락을 AI가 짧고 중립적으로 설명한다.
3. 도서관별 `hidden_books` 후보군에서 트렌드와 연결되는 잠자는 도서를 선정한다.
4. 트렌드와 도서의 연결 이유를 AI가 개별 추천 문구로 생성한다.
5. 생성 결과를 날짜별로 저장하고 프론트엔드에는 조회 API로 제공한다.

기존 `GET /books/today`는 날짜에 따라 후보 한 권을 고르는 단순 추천이므로 그대로 유지한다. 이번 기능은 별도의 `GET /trends/daily`로 추가하여 기존 프론트 계약을 깨지 않는다.

## 2. MVP에서 확정할 정책

| 항목 | 결정 |
|---|---|
| 기준 시간대 | `Asia/Seoul` |
| 트렌드 범위 | 대한민국, 최근 24시간 |
| 1차 트렌드 원천 | Google Trends `Trending now` RSS, 지역 `KR` |
| 원천 추상화 | `TrendProvider` 인터페이스를 두어 공급자 교체 가능하게 구현 |
| 수집 시각 | 매일 05:00 KST |
| 화면 노출량 | 기본 5개 트렌드, 트렌드당 도서 1권 |
| 추천 대상 | 해당 `libraryCode`의 `hidden_books`만 사용 |
| 생성 방식 | 트렌드 맥락 생성 1회 + 도서관별 추천 생성 1회 이상의 배치 처리 |
| 결과 안정성 | 요청 때마다 AI를 호출하지 않고 하루 결과를 DB에서 조회 |
| 실패 대응 | 오늘 결과가 없으면 최근 3일 이내의 가장 최신 성공 결과 반환 |
| 보관 기간 | 90일 |
| 공개 범위 | 사용자 조회 API는 공개, 사서 미리보기·재생성 API는 `LIBRARIAN` 전용 |

Google Trends의 공식 도움말은 `Trending now`가 최근 4시간·24시간·48시간·7일 범위를 지원하고 평균 약 10분마다 갱신되며 RSS 내보내기를 제공한다고 설명한다. 다만 RSS는 유료 API 수준의 가용성 계약이 없으므로, 반드시 타임아웃·재시도·캐시·공급자 교체 구조를 둔다.

- 공식 안내: <https://support.google.com/trends/answer/3076011?hl=ko>
- 데이터 사용·출처 표기 안내: <https://support.google.com/trends/answer/4365538?hl=ko>

> 네이버 데이터랩 검색어 트렌드 API는 백엔드가 미리 입력한 주제어의 상대 검색 추이를 조회하는 용도다. 그날의 인기 검색어 목록 자체를 제공하는 API가 아니므로 1차 수집원으로 사용하지 않는다. 향후 후보 키워드의 상승 여부를 교차 검증하는 보조 공급자로는 추가할 수 있다.

## 3. 전체 처리 흐름

```text
[매일 05:00 KST 스케줄러]
          |
          v
[Google Trends KR RSS 수집]
          |
          v
[중복 제거·정규화·민감 주제 필터]
          |
          v
[AI: 트렌드 맥락 설명 + 도서 연결 가능성 판정]
          |
          v
[날짜별 daily_trends 저장]
          |
          +-----------------------------+
          | 도서관별 hidden_books 조회  |
          v                             |
[AI: 트렌드별 도서 선정·추천 문구 생성] |
          |                             |
          v                             |
[ISBN 후보 검증·결과 스냅샷 저장] <-----+
          |
          v
[GET /trends/daily -> 프론트 표시]
```

조회 요청에서는 외부 트렌드 API나 OpenAI를 호출하지 않는다. 이렇게 해야 응답 시간이 일정하고, 같은 날 사용자마다 추천 문구가 달라지지 않으며, 외부 API 장애가 사용자 요청으로 전파되지 않는다.

## 4. 트렌드 선정과 문구 생성 규칙

### 4.1 트렌드 수집·정규화

1. RSS에서 상위 20개를 읽는다.
2. 앞뒤 공백, 연속 공백, 대소문자를 정규화한다.
3. 같은 사건을 나타내는 유사 검색어는 하나의 대표 키워드로 묶는다.
4. `sourceKey`는 공급자가 주는 식별자가 있으면 사용하고, 없으면 `정규화 키워드 + 시작 시각`의 SHA-256 해시로 만든다.
5. 검색량은 공급자가 제공하는 경우에만 `trafficLabel`에 원문 문자열로 보관한다. 서로 다른 공급자의 검색량을 같은 수치처럼 비교하지 않는다.
6. 원문 기사 제목과 링크는 AI의 맥락 생성 근거로만 사용하고 출처 URL도 함께 저장한다.

### 4.2 안전성 필터

아래 항목은 도서 추천으로 가볍게 소비될 위험이 있으므로 기본 제외한다.

- 사망·재난·참사·강력 범죄처럼 피해자가 존재하는 사건
- 자해, 혐오, 선정적 사건 또는 미성년자 대상 범죄
- 사실 확인 근거가 부족한 인물 루머
- 단순 인명·경기 결과처럼 도서와 의미 있는 연결이 어려운 검색어
- AI가 후보 도서와 연결하기 어렵다고 판정한 검색어

AI 판정값은 `ELIGIBLE`, `SENSITIVE`, `NO_BOOK_MATCH` 중 하나로 제한한다. `ELIGIBLE`만 추천 생성 단계로 넘긴다. AI 출력 파싱에 실패하면 안전하게 제외하고 로그만 남긴다.

### 4.3 AI 생성 문구

문구는 두 단계로 생성한다.

#### A. 트렌드 맥락 설명

- 입력: 검색 키워드, 공급자가 제공한 관련 기사 제목·URL, 검색 시작 시각
- 출력: `contextDescription` 1~2문장
- 원칙: 제공된 근거 안에서만 설명하고 확인되지 않은 원인·수치·전망을 만들지 않는다.
- 예시: `원·달러 환율 변동 폭이 커지면서 환율과 개인 자산 관리에 대한 관심이 높아지고 있습니다.`

#### B. 트렌드 연계 도서 추천 문구

- 입력: 트렌드 키워드·맥락, 해당 도서관의 잠자는 도서 후보 목록
- 출력: `recommendationTitle`, 선정 ISBN, ISBN별 `reason`
- 예시 제목: `격동하는 환율, 돈의 흐름을 읽는 법`
- 예시 이유: `환율이 생활비와 투자 판단에 어떤 영향을 주는지 기초 경제 원리부터 차근차근 이해하도록 돕는 책입니다.`
- AI는 입력 후보에 포함된 ISBN만 반환할 수 있다.
- 서버는 AI가 반환한 ISBN이 실제 해당 도서관의 후보군에 있는지 다시 검증한다.
- 같은 날짜·도서관 안에서는 가능한 한 같은 ISBN을 중복 추천하지 않는다.
- 추천할 만한 도서가 없으면 억지로 연결하지 않고 해당 트렌드를 결과에서 제외한다.

### 4.4 AI 응답 내부 계약

OpenAI에는 JSON 모드를 사용하고 다음 구조만 허용한다. 이 구조는 서버 내부 계약이며 프론트엔드에는 직접 노출하지 않는다.

```json
{
  "items": [
    {
      "trendId": 31,
      "recommendationTitle": "격동하는 환율, 돈의 흐름을 읽는 법",
      "books": [
        {
          "isbn": "9788960867450",
          "reason": "환율 변화가 개인의 소비와 자산에 미치는 영향을 이해하도록 돕습니다."
        }
      ]
    }
  ]
}
```

서버 검증 조건은 다음과 같다.

- `trendId`가 현재 배치 입력에 포함되어 있어야 한다.
- `isbn`이 현재 `libraryCode`의 `hidden_books`에 있어야 한다.
- `recommendationTitle`은 100자 이하, `reason`은 300자 이하이다.
- 트렌드당 도서 수는 설정된 상한을 넘을 수 없다.
- 유효한 도서가 하나도 남지 않으면 해당 트렌드 추천은 저장하지 않는다.

## 5. 사용자용 API

### 5.1 일일 트렌드 연계 추천 조회

`GET /trends/daily?libraryCode=121018&date=2026-08-15`

인증은 필요하지 않다. 기존 `GET /books/today`와 동일하게 프론트엔드가 선택한 도서관 코드를 전달한다.

#### 요청 쿼리

| 필드 | 타입 | 필수 | 조건 | 설명 |
|---|---|:---:|---|---|
| `libraryCode` | String | O | 1~20자, 공백 불가 | 추천을 조회할 도서관정보나루 도서관 코드 |
| `date` | String | X | `YYYY-MM-DD`, 미래 날짜 불가 | 생략하면 KST 기준 오늘 |

#### 200 OK

```json
{
  "success": true,
  "message": "오늘의 트렌드 연계 추천을 조회했습니다.",
  "data": {
    "requestedDate": "2026-08-15",
    "recommendationDate": "2026-08-15",
    "libraryCode": "121018",
    "libraryName": "부산광역시 금정도서관",
    "freshness": "CURRENT",
    "generatedAt": "2026-08-15T05:08:31+09:00",
    "source": {
      "type": "GOOGLE_TRENDS",
      "name": "Google Trends",
      "region": "KR",
      "fetchedAt": "2026-08-15T05:00:12+09:00",
      "url": "https://trends.google.com/trending?geo=KR"
    },
    "items": [
      {
        "trendId": 31,
        "keyword": "환율 급등",
        "rank": 2,
        "trafficLabel": "10K+",
        "startedAt": "2026-08-15T01:20:00+09:00",
        "contextDescription": "원·달러 환율 변동 폭이 커지면서 환율과 개인 자산 관리에 대한 관심이 높아지고 있습니다.",
        "recommendationTitle": "격동하는 환율, 돈의 흐름을 읽는 법",
        "books": [
          {
            "recommendationId": 84,
            "isbn": "9788960867450",
            "title": "환율을 읽는 시간",
            "author": "홍길동",
            "cover": "https://...",
            "loanCount": 1,
            "reason": "환율 변화가 개인의 소비와 자산에 미치는 영향을 기초 경제 원리부터 이해하도록 돕습니다."
          }
        ]
      }
    ]
  }
}
```

#### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `requestedDate` | String | 프론트가 요청한 날짜. 날짜를 생략했다면 오늘 |
| `recommendationDate` | String | 실제 반환된 추천의 기준 날짜 |
| `freshness` | String | `CURRENT` 또는 `FALLBACK` |
| `generatedAt` | String | 해당 도서관 추천 생성 완료 시각, ISO 8601 + KST 오프셋 |
| `source` | Object | 트렌드 원천과 수집 시각. 화면 하단 출처 표시에 사용 |
| `items` | Array | 순위가 빠른 트렌드부터 정렬된 추천 목록 |
| `contextDescription` | String | 트렌드가 주목받는 맥락을 설명하는 AI 문구 |
| `recommendationTitle` | String | 트렌드와 도서 묶음을 연결하는 전시형 제목 |
| `books[].reason` | String | 해당 도서를 이 트렌드에 추천하는 개별 문구 |

#### 캐시·대체 결과 조건

- `date`를 생략했거나 오늘을 요청했고 오늘 생성분이 있으면 `CURRENT`를 반환한다.
- 오늘 생성이 실패했으면 최근 3일 이내의 가장 최신 성공 결과를 `FALLBACK`으로 반환한다.
- `FALLBACK`이면 `requestedDate`와 `recommendationDate`가 다르다.
- 과거 날짜를 명시한 요청은 그 날짜의 정확한 결과만 반환하고 자동 대체하지 않는다.
- 응답에는 `Cache-Control: public, max-age=300`과 `ETag`를 적용한다.
- 결과 배열은 항상 `rank` 오름차순, 책은 `displayOrder` 오름차순이다.

#### FALLBACK 예시

```json
{
  "success": true,
  "message": "가장 최근의 트렌드 연계 추천을 조회했습니다.",
  "data": {
    "requestedDate": "2026-08-15",
    "recommendationDate": "2026-08-14",
    "libraryCode": "121018",
    "libraryName": "부산광역시 금정도서관",
    "freshness": "FALLBACK",
    "generatedAt": "2026-08-14T05:07:18+09:00",
    "source": {
      "type": "GOOGLE_TRENDS",
      "name": "Google Trends",
      "region": "KR",
      "fetchedAt": "2026-08-14T05:00:10+09:00",
      "url": "https://trends.google.com/trending?geo=KR"
    },
    "items": []
  }
}
```

위 예시의 `items`는 형식 설명을 위해 생략한 것이며, 실제 200 응답에는 최소 1개가 있어야 한다.

## 6. 사서용 API

모든 API는 `Authorization: Bearer {accessToken}`과 `LIBRARIAN` 권한이 필요하다. 요청에서 `libraryCode`를 받지 않고 JWT 사용자의 `libraryCode`를 서버에서 조회한다. 다른 도서관의 결과를 생성하거나 조회할 수 없어야 한다.

### 6.1 내 도서관 오늘 추천 미리보기

`GET /librarian/trends/daily`

요청 바디와 쿼리는 없다. 응답은 5.1과 동일하다.

```json
{
  "success": true,
  "message": "내 도서관의 오늘 트렌드 추천을 조회했습니다.",
  "data": {
    "requestedDate": "2026-08-15",
    "recommendationDate": "2026-08-15",
    "libraryCode": "121018",
    "libraryName": "부산광역시 금정도서관",
    "freshness": "CURRENT",
    "generatedAt": "2026-08-15T05:08:31+09:00",
    "source": {},
    "items": []
  }
}
```

### 6.2 내 도서관 오늘 추천 재생성

`POST /librarian/trends/refresh`

스케줄러 실패 복구와 시연용 API다. 일반적인 화면 진입 시 호출하지 않고 사서 관리 화면의 명시적인 `오늘 추천 다시 생성` 버튼에서만 호출한다.

```json
{
  "force": false
}
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|:---:|---|---|
| `force` | Boolean | X | `false` | `true`면 오늘의 성공 결과가 있어도 다시 생성 |

`force=true`는 OpenAI 비용이 발생하므로 같은 사서·도서관 기준 30분에 한 번으로 제한한다. `libraryCode`, 날짜, 키워드, 임의 프롬프트는 요청으로 받지 않는다.

처리 조건은 다음과 같다.

- 오늘 성공 배치가 있고 `force=false`이면 새 작업을 만들지 않고 `200 OK`와 기존 배치 상태를 반환한다.
- 실행 중인 배치가 있으면 `409 TREND_003`과 기존 `batchId`를 반환한다.
- 오늘 배치가 없거나 실패했거나, 성공 배치에 `force=true`를 요청하면 `202 Accepted`로 생성 작업을 시작한다.
- 강제 재생성은 같은 날짜·도서관의 배치 행을 재사용하되 기존 추천 행을 먼저 삭제하지 않는다. 새 결과 생성과 검증이 모두 끝난 뒤에만 추천 행을 교체한다.

#### 202 Accepted

```json
{
  "success": true,
  "message": "오늘의 트렌드 추천 생성을 요청했습니다.",
  "data": {
    "batchId": 17,
    "date": "2026-08-15",
    "libraryCode": "121018",
    "status": "PENDING",
    "requestedAt": "2026-08-15T14:22:10+09:00"
  }
}
```

#### 이미 생성된 경우 200 OK

```json
{
  "success": true,
  "message": "오늘의 트렌드 추천이 이미 생성되어 있습니다.",
  "data": {
    "batchId": 17,
    "date": "2026-08-15",
    "libraryCode": "121018",
    "status": "COMPLETED",
    "requestedAt": "2026-08-15T05:00:00+09:00"
  }
}
```

프론트엔드는 202 응답 후 3~5초 간격으로 6.3을 조회한다. 최대 1분 뒤에도 완료되지 않으면 폴링을 중단하고 `생성 중입니다. 잠시 후 다시 확인해 주세요.`를 표시한다.

### 6.3 생성 상태 조회

`GET /librarian/trends/batches/{batchId}`

자기 도서관의 배치만 조회할 수 있다. 다른 도서관 배치 ID는 존재 여부를 노출하지 않고 `404 TREND_001`을 반환한다.

```json
{
  "success": true,
  "data": {
    "batchId": 17,
    "date": "2026-08-15",
    "status": "COMPLETED",
    "createdCount": 5,
    "startedAt": "2026-08-15T14:22:11+09:00",
    "completedAt": "2026-08-15T14:22:26+09:00",
    "errorCode": null
  }
}
```

`status` 값은 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` 중 하나다. 실패 응답에는 내부 예외 메시지를 노출하지 않고 `errorCode`만 반환한다.

## 7. 오류 응답

기존 공통 오류 형식을 그대로 사용한다.

```json
{
  "success": false,
  "code": "TREND_001",
  "message": "조회할 수 있는 트렌드 추천이 없습니다.",
  "data": null
}
```

| HTTP | 코드 | 발생 조건 | 프론트 처리 |
|---:|---|---|---|
| 400 | `VALIDATION_001` | 도서관 코드 누락, 날짜 형식 오류, 미래 날짜 요청 | 입력값 확인 안내 |
| 401 | `AUTH_001` | 사서 API 토큰 없음·만료 | 로그인 화면 이동 |
| 403 | `AUTH_002` | 일반 사용자가 사서 API 호출 | 권한 안내 |
| 404 | `BOOK_001` | 해당 도서관의 `hidden_books` 후보군이 없음 | 장서 CSV 업로드 또는 다른 도서관 선택 안내 |
| 404 | `TREND_001` | 요청 날짜와 대체 기간에 저장된 추천이 없음 | `아직 오늘의 추천을 준비 중입니다.` 표시 |
| 409 | `TREND_003` | 같은 도서관의 배치가 이미 실행 중 | 기존 `batchId`로 상태 조회 |
| 429 | `TREND_004` | 사서 강제 재생성 제한 초과 | `retryAfterSeconds` 뒤 재시도 안내 |
| 503 | `TREND_002` | 외부 트렌드 수집 실패이며 대체 데이터도 없음 | 잠시 후 재시도 안내 |
| 500 | `AI_001` | AI 응답 생성·파싱 실패 | 기존 결과 유지, 운영 로그 확인 |

429 응답의 `data`는 다음과 같다.

```json
{
  "success": false,
  "code": "TREND_004",
  "message": "트렌드 추천은 30분 후 다시 생성할 수 있습니다.",
  "data": { "retryAfterSeconds": 1240 }
}
```

## 8. 데이터베이스 설계

Flyway 파일 권장명: `V202608150002__create_daily_trend_recommendations.sql`

현재 작업 트리에 `V202608150001__create_book_enrichment_cache.sql`이 있으므로 다음 번호를 사용한다. 실제 병합 전에 최신 migration 번호와 충돌 여부를 다시 확인한다.

### 8.1 `daily_trends`

트렌드 원문과 AI 맥락을 날짜별로 한 번 저장한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `trend_date` | DATE | KST 기준 날짜 |
| `source` | VARCHAR(30) | `GOOGLE_TRENDS` |
| `source_key` | VARCHAR(255) | 공급자 항목 식별자 또는 서버 생성 해시 |
| `keyword` | VARCHAR(200) | 화면 표시용 원문 키워드 |
| `normalized_keyword` | VARCHAR(200) | 중복 비교용 정규화 키워드 |
| `trend_rank` | INT | 원천 순위 |
| `traffic_label` | VARCHAR(50) | 원천 제공 검색량 문자열, nullable |
| `started_at` | DATETIME(6) | 트렌드 시작 시각, nullable |
| `source_url` | VARCHAR(2048) | 대표 원천 링크 |
| `article_evidence` | JSON | AI에 제공한 기사 제목·URL 목록 |
| `context_description` | VARCHAR(1000) | AI가 생성한 중립적 맥락 설명 |
| `eligibility` | VARCHAR(30) | `ELIGIBLE`, `SENSITIVE`, `NO_BOOK_MATCH` |
| `fetched_at` | DATETIME(6) | 외부 원천 수집 시각 |
| `created_at` | DATETIME(6) | 생성 시각 |

유니크 키는 `(trend_date, source, source_key)`로 둔다. 조회 인덱스는 `(trend_date, eligibility, trend_rank)`로 둔다.

### 8.2 `daily_trend_batches`

도서관별 추천 생성 상태와 재시도 정보를 저장한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `recommendation_date` | DATE | KST 기준 생성 대상 날짜 |
| `library_code` | VARCHAR(20) | 도서관 코드 |
| `status` | VARCHAR(20) | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `attempt_count` | INT | 시도 횟수 |
| `created_count` | INT | 최종 저장된 트렌드 추천 수 |
| `error_code` | VARCHAR(50) | 외부 노출 가능한 오류 코드, nullable |
| `started_at` | DATETIME(6) | 처리 시작 시각, nullable |
| `completed_at` | DATETIME(6) | 완료 시각, nullable |
| `created_at` | DATETIME(6) | 생성 시각 |
| `updated_at` | DATETIME(6) | 변경 시각 |

유니크 키는 `(recommendation_date, library_code)`로 둔다. 중복 스케줄 실행은 이 제약과 상태 전이 조건으로 막는다.

강제 재생성 중에는 기존 `daily_trend_recommendations`를 유지한다. 성공 시에만 기존 추천을 새 결과로 교체하고 `completed_at`을 갱신한다. 재생성이 실패하면 `status`와 `error_code`는 실패 상태를 기록하되 기존 추천과 직전 성공 `completed_at`은 보존한다.

### 8.3 `daily_trend_recommendations`

추천 당시 도서 정보를 스냅샷으로 저장한다. CSV 재업로드가 기존 `hidden_books`를 삭제·교체하더라도 당일 추천 응답이 깨지지 않도록 `hidden_book_id` 외래 키에 의존하지 않는다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `batch_id` | BIGINT | FK → `daily_trend_batches.id`, ON DELETE CASCADE |
| `daily_trend_id` | BIGINT | FK → `daily_trends.id` |
| `library_code` | VARCHAR(20) | 조회 최적화와 무결성 확인용 |
| `isbn` | VARCHAR(20) | 추천 당시 ISBN |
| `book_title` | VARCHAR(500) | 제목 스냅샷 |
| `book_author` | VARCHAR(500) | 저자 스냅샷 |
| `book_cover` | VARCHAR(2048) | 표지 URL 스냅샷 |
| `loan_count` | BIGINT | 추천 당시 대출 건수 |
| `recommendation_title` | VARCHAR(200) | 트렌드별 전시형 제목 |
| `reason` | VARCHAR(1000) | 개별 도서 추천 문구 |
| `display_order` | INT | 트렌드 내 도서 순서 |
| `created_at` | DATETIME(6) | 생성 시각 |

유니크 키는 `(batch_id, daily_trend_id, isbn)`로 둔다. 조회 인덱스는 `(library_code, batch_id, display_order)`로 둔다.

## 9. 백엔드 코드 구조

기존 패키지 구조에 맞춰 다음 구성을 권장한다.

```text
com.wakebook.trend
├─ controller
│  ├─ DailyTrendController
│  └─ LibrarianTrendController
├─ domain
│  ├─ DailyTrend
│  ├─ DailyTrendBatch
│  ├─ DailyTrendRecommendation
│  ├─ TrendEligibility
│  └─ TrendBatchStatus
├─ dto
│  ├─ DailyTrendResponse
│  ├─ TrendRefreshRequest
│  ├─ TrendBatchResponse
│  └─ TrendSourceResponse
├─ repository
│  ├─ DailyTrendRepository
│  ├─ DailyTrendBatchRepository
│  └─ DailyTrendRecommendationRepository
├─ service
│  ├─ TrendIngestionService
│  ├─ TrendEnrichmentService
│  ├─ TrendBookRecommendationService
│  ├─ DailyTrendQueryService
│  └─ TrendBatchService
├─ scheduler
│  └─ DailyTrendScheduler
└─ support
   └─ TrendProperties

com.wakebook.external.trend
├─ TrendProvider
├─ TrendItem
└─ google
   ├─ GoogleTrendsRssClient
   └─ GoogleTrendsRssParser
```

핵심 인터페이스는 공급자 응답 형식을 도메인에서 분리한다.

```java
public interface TrendProvider {
    List<TrendItem> fetch(LocalDate date, String region);
}
```

`DailyTrendQueryService`는 공개 API와 사서 미리보기 API가 공통으로 사용한다. 사서 API에서는 컨트롤러가 받은 `libraryCode`가 아니라 JWT subject로 조회한 `User.libraryCode`만 서비스에 전달한다.

## 10. 스케줄·재시도·동시성

### 10.1 정상 실행

1. 매일 05:00 KST에 트렌드를 수집하고 `daily_trends`를 upsert한다.
2. `hidden_books`에서 `DISTINCT library_code`를 조회한다.
3. 도서관마다 `daily_trend_batches`를 생성하고 추천을 만든다.
4. 유효한 추천이 1개 이상이면 배치를 `COMPLETED`로 바꾼다.
5. 0개이면 `FAILED / TREND_001`로 기록하고 전날 결과를 보존한다.

### 10.2 실패 복구

- 외부 HTTP 연결/읽기 타임아웃은 각각 3초/10초로 둔다.
- 트렌드 수집은 지수 백오프로 최대 3회 재시도한다.
- OpenAI 실패는 도서관 배치별 최대 2회 재시도한다.
- 30분마다 복구 스케줄러가 오늘 결과가 없는 도서관과 20분 이상 `PROCESSING`인 배치를 확인한다.
- 재시도 중에도 기존 성공 결과는 삭제하지 않는다. 새 결과를 모두 검증한 뒤 한 트랜잭션에서 오늘 결과를 교체한다.
- 다중 서버 환경에서는 `(date, library_code)` 유니크 키만으로 AI 중복 호출을 완전히 막기 어려우므로 운영 전 DB 기반 분산 락 또는 ShedLock을 추가한다.

### 10.3 CSV 업로드와의 연결

사서가 장서 CSV를 업로드하여 `hidden_books`를 교체한 경우:

- 업로드 성공 후 오늘 배치가 없으면 해당 도서관 추천 배치를 `PENDING`으로 등록한다.
- 오늘 배치가 이미 `COMPLETED`이면 자동 재생성하지 않는다. 예기치 않은 OpenAI 비용과 화면 결과 변경을 막기 위함이다.
- 사서는 필요하면 `POST /librarian/trends/refresh`의 `force=true`로 새 후보군을 반영한다.
- 업로드 트랜잭션과 AI 호출은 분리한다. CSV 저장 롤백과 긴 외부 호출이 한 트랜잭션에 묶이면 안 된다.

## 11. 설정값

`application.properties` 권장 설정:

```properties
trend.enabled=${TREND_ENABLED:true}
trend.provider=${TREND_PROVIDER:GOOGLE_TRENDS}
trend.google-rss-url=${TREND_GOOGLE_RSS_URL:https://trends.google.com/trending/rss?geo=KR}
trend.region=${TREND_REGION:KR}
trend.zone-id=${TREND_ZONE_ID:Asia/Seoul}
trend.daily-cron=${TREND_DAILY_CRON:0 0 5 * * *}
trend.recovery-cron=${TREND_RECOVERY_CRON:0 */30 * * * *}
trend.source-limit=${TREND_SOURCE_LIMIT:20}
trend.display-count=${TREND_DISPLAY_COUNT:5}
trend.books-per-trend=${TREND_BOOKS_PER_TREND:1}
trend.fallback-days=${TREND_FALLBACK_DAYS:3}
trend.retention-days=${TREND_RETENTION_DAYS:90}
trend.force-refresh-cooldown-minutes=${TREND_REFRESH_COOLDOWN_MINUTES:30}
```

운영·테스트에서 기능을 끌 수 있도록 반드시 `trend.enabled`를 둔다. 테스트는 실제 RSS와 OpenAI를 호출하지 않고 fake provider/client를 주입한다.

## 12. 프론트엔드 연동 계약

### 12.1 사용자 화면

프론트 API 함수 권장 형태:

```javascript
dailyTrends: (libraryCode, date) => client.get('/trends/daily', {
  params: { libraryCode, ...(date ? { date } : {}) },
})
```

도서관 코드는 현재 추천 화면과 같은 우선순위를 사용한다.

1. 사서 계정이면 `user.libraryCode`
2. 일반 사용자는 사용자가 입력·선택해 `localStorage.wakebook_library_code`에 저장한 값
3. 둘 다 없으면 API를 호출하지 않고 도서관 코드 선택 UI 표시

화면 표시 규칙:

- `contextDescription`: 트렌드 카드 상단의 맥락 설명
- `recommendationTitle`: 카드 제목
- `books[].reason`: 각 도서 아래 AI 추천 이유
- `source.name`, `source.url`, `source.fetchedAt`: 화면 하단 출처와 갱신 시각
- `freshness === 'FALLBACK'`: `오늘 데이터 수집이 지연되어 {recommendationDate} 추천을 보여드려요.` 배너 표시
- `TREND_001`: 샘플 데이터를 실제 추천처럼 대체하지 말고 준비 중 상태 표시
- `BOOK_001`: 이 도서관은 후보군이 없다는 안내 표시
- 중복 요청 방지를 위해 같은 `libraryCode + date` 요청은 클라이언트에서도 캐시 가능

React 렌더링 키는 배열 순번이 아니라 `trendId`, `recommendationId`를 사용한다.

### 12.2 사서 화면

```javascript
librarianDailyTrends: () => client.get('/librarian/trends/daily'),
refreshDailyTrends: (force = false) => client.post('/librarian/trends/refresh', { force }),
trendBatch: (batchId) => client.get(`/librarian/trends/batches/${batchId}`),
```

- 재생성 버튼은 처리 중 비활성화한다.
- `PENDING` 또는 `PROCESSING`이면 3~5초 간격으로 상태를 확인한다.
- `COMPLETED`가 되면 미리보기 API를 다시 호출한다.
- `FAILED`여도 기존 추천 화면은 유지하고 오류 알림만 표시한다.
- `force=true` 호출 전에는 OpenAI 비용과 오늘 노출 결과 변경 가능성을 확인하는 confirm UI를 둔다.

## 13. 구현 순서

### 1단계 — 계약·DB 기반

- 이 문서의 필드명과 enum을 프론트와 확정한다.
- Flyway migration과 JPA entity/repository를 구현한다.
- 날짜 계산은 모든 서비스에서 `Clock`과 `ZoneId`를 주입해 테스트 가능하게 만든다.

### 2단계 — 트렌드 수집

- `TrendProvider`와 Google RSS client/parser를 구현한다.
- XML 크기 제한, 외부 엔티티 비활성화(XXE 방지), 타임아웃을 적용한다.
- RSS fixture 기반 파서 테스트를 작성한다.
- 중복 제거와 `sourceKey` 생성 로직을 구현한다.

### 3단계 — AI 맥락·안전성 처리

- 트렌드 20개를 한 번의 JSON 배치 요청으로 분류·요약한다.
- enum, 길이, 필수값을 검증하고 잘못된 항목만 제외한다.
- 민감 주제와 근거 없는 설명이 저장되지 않는 테스트를 작성한다.

### 4단계 — 도서관별 추천

- `libraryCode`별 `hidden_books`를 가져온다.
- 최대 60권인 현재 후보군을 한 배치 프롬프트로 전달하되 ISBN·제목·키워드·기존 소개만 포함한다.
- AI가 반환한 ISBN을 서버 후보 집합과 대조한다.
- 추천 결과를 도서 정보 스냅샷으로 저장한다.
- 동일 ISBN 중복 제거와 결과 교체의 원자성을 테스트한다.

### 5단계 — 스케줄러·복구

- `@EnableScheduling`, 일일 스케줄, 30분 복구 스케줄을 구현한다.
- 배치 상태 전이와 중복 실행 방지를 구현한다.
- 외부 실패 시 전날 데이터를 반환하는 fallback 테스트를 작성한다.

### 6단계 — HTTP API·보안

- 사용자 조회, 사서 미리보기, 재생성, 배치 상태 API를 구현한다.
- `SecurityConfig`에 `GET /trends/**`만 공개로 추가한다.
- `/librarian/trends/**`는 기존 `/librarian/**` 규칙으로 `LIBRARIAN`만 허용한다.
- 사서 API가 JWT 사용자의 도서관 코드만 사용하는지 컨트롤러 테스트로 검증한다.

### 7단계 — 프론트 연동·운영 확인

- 실제 API 응답으로 카드와 상태 UI를 연결한다.
- CURRENT, FALLBACK, 빈 후보, 생성 중, 실패 상태를 각각 확인한다.
- 운영 환경에서 1일 OpenAI 호출량과 평균 배치 시간을 측정한 뒤 노출 트렌드 수를 조정한다.

## 14. 테스트 범위

### 단위 테스트

- RSS 정상/빈 응답/깨진 XML/중복 키워드 파싱
- KST 자정 경계의 `trendDate` 계산
- 민감 트렌드 제외
- AI JSON 파싱 실패 시 해당 항목 제외
- 후보군 밖 ISBN 거부
- 같은 날짜·도서관·ISBN 중복 방지
- 사서 도서관 코드 강제 적용
- 오늘 결과 및 3일 fallback 조회
- 4일 이상 지난 결과는 fallback하지 않음
- 강제 재생성 30분 제한

### 통합 테스트

- 스케줄 실행 → 트렌드 저장 → 추천 저장 → GET 조회 전체 흐름
- 트렌드 공급자 5xx/timeout 시 재시도와 기존 결과 유지
- OpenAI 실패 시 배치 `FAILED` 기록
- CSV 후보군 교체 후 기존 추천 스냅샷 조회 가능
- 일반 사용자 `/librarian/trends/**` 접근 시 403
- 다른 사서의 `batchId` 조회 시 404

### API 계약 테스트

- JSON 필드명과 enum 고정
- 날짜는 ISO 8601, 시각은 `+09:00` 오프셋 포함
- `items`와 `books`는 빈 값일 때도 `null`이 아니라 `[]`
- 200 응답에는 최소 추천 1개 존재
- 오류 응답은 공통 `success/code/message/data` 구조 사용

## 15. 완료 조건(Definition of Done)

- [ ] 매일 KST 기준 트렌드가 자동 수집된다.
- [ ] 트렌드 5개 이하와 도서관별 잠자는 도서가 연결되어 저장된다.
- [ ] 트렌드 맥락 문구와 도서별 추천 문구가 분리되어 응답된다.
- [ ] AI가 후보군에 없는 ISBN을 반환해도 저장되지 않는다.
- [ ] 민감 사건은 추천에서 제외된다.
- [ ] 사용자 GET 요청에서 외부 API나 OpenAI를 호출하지 않는다.
- [ ] 오늘 배치 실패 시 3일 이내 최근 결과가 `FALLBACK`으로 제공된다.
- [ ] 사서 API는 요청값이 아닌 로그인 사서의 `libraryCode`를 사용한다.
- [ ] CSV 교체 이후에도 이미 생성된 당일 추천을 조회할 수 있다.
- [ ] 단위·통합·보안·API 계약 테스트가 통과한다.
- [ ] 프론트가 CURRENT/FALLBACK/BOOK_001/TREND_001 상태를 구분해 표시한다.
- [ ] 화면에 트렌드 출처와 수집 시각이 표시된다.

## 16. MVP 이후 확장

- Google Trends 외에 다른 합법적 데이터 공급자를 `TrendProvider`로 추가해 교차 검증
- 지역별 트렌드와 도서관 소재 지역을 연결한 지역 맞춤 추천
- 트렌드·도서 임베딩을 저장해 AI 호출 전 후보를 더 정교하게 축소
- 사서가 민감하지 않은 트렌드를 승인·숨김 처리하는 검수 화면
- 추천 노출·상세 조회·책장 저장 이벤트를 기록해 트렌드 추천 효과 측정
- 실제 반응 데이터를 이용한 트렌드 관련성·발견 가치 가중치 개선
