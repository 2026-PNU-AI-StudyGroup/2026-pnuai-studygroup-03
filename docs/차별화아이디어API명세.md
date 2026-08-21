# 일 단위 트렌드 연계 잠자는 도서 추천 API 명세 및 구현 계획

> 최초 작성: 2026-08-15 · 하이브리드 트렌드 조사 방식 개정: 2026-08-18 · 도서관별 매칭 개정: 2026-08-19 · 네이버 개발자센터 Open API 및 시작 복구 반영: 2026-08-22
> 대상: WakeBook 백엔드·프론트엔드 공동 개발  
> Base URL: `http://localhost:8080/api`  
> 상태: 백엔드 구현 완료

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
| 후보 발견 | Google Trends `Trending now` RSS, 지역 `KR` |
| 뉴스 근거 보강 | 네이버 개발자센터 뉴스 검색 Open API의 최근 기사 제목·요약문 |
| 국내 상승 검증 | 네이버 개발자센터 데이터랩 통합 검색어 트렌드 API, 최근 14일 일간 추이 |
| 원천 추상화 | `TrendProvider`, `NewsEvidenceProvider`, `SearchTrendValidator` 인터페이스로 외부 공급자 분리 |
| 수집 시각 | 매일 05:00 KST |
| 화면 노출량 | 관련성 기준을 통과한 최대 5개 트렌드, 트렌드당 최대 2권 |
| 추천 대상 | 해당 `libraryCode`의 KDC 균형 `hidden_books` 최대 200권만 사용 |
| 생성 방식 | 후보 발견 → 뉴스 근거 보강 → AI 주제 재정의 → 네이버 상승 검증 → 도서관별 추천 배치 |
| 결과 안정성 | 요청 때마다 AI를 호출하지 않고 하루 결과를 DB에서 조회 |
| 실패 대응 | 오늘 결과가 없으면 최근 3일 이내의 가장 최신 성공 결과 반환 |
| 보관 기간 | 90일 |
| 공개 범위 | 사용자 조회 API는 공개, 사서 미리보기·재생성 API는 `LIBRARIAN` 전용 |

Google Trends의 공식 도움말은 `Trending now`가 최근 4시간·24시간·48시간·7일 범위를 지원하고 평균 약 10분마다 갱신되며 RSS 내보내기를 제공한다고 설명한다. 다만 RSS는 유료 API 수준의 가용성 계약이 없으므로, 반드시 타임아웃·재시도·캐시·공급자 교체 구조를 둔다.

- 공식 안내: <https://support.google.com/trends/answer/3076011?hl=ko>
- 데이터 사용·출처 표기 안내: <https://support.google.com/trends/answer/4365538?hl=ko>

Google Trends KR은 한국의 모든 검색 사용자를 대표하지 않고 한국 내 Google 검색 사용자의 급상승 관심사를 보여주는 표본이라는 한계가 있다. 또한 실제 RSS에서는 `geo=KR`이어도 외국어 검색어나 해외 기사가 섞일 수 있고, 관련 기사 요약 필드가 비어 있는 경우가 있다. 따라서 RSS 원문 키워드를 그대로 화면 주제로 사용하거나 기사 제목만으로 맥락을 생성하지 않는다.

네이버 개발자센터의 비로그인 Open API는 다음 두 역할로 MVP부터 함께 사용한다.

- **뉴스 검색 API**: Google 후보 키워드로 최근 기사 3~5건을 검색하고 `title`, `description`, `link`, `pubDate`를 AI 근거로 제공한다. 언론사 본문을 직접 크롤링하지 않는다.
- **검색어 트렌드 API**: Google이 발견한 후보를 입력해 네이버 통합검색에서도 최근 상승 신호가 있는지 확인한다. 이 API는 키워드를 미리 지정해야 하므로 후보 발견용으로는 부적합하지만, 이미 발견된 후보의 국내 교차 검증에는 적합하다.

네이버 검색어 트렌드는 절대 검색량이 아니라 요청 기간 내 상대값을 제공하므로, 검증 실패를 즉시 탈락 조건으로 사용하지 않는다. Google 급상승 신호, 네이버 최근 상승률, 뉴스 근거 일관성을 합산한 점수로 최종 순위를 결정한다.

- 네이버 뉴스 검색 API: <https://developers.naver.com/docs/serviceapi/search/news/news.md>
- 네이버 통합 검색어 트렌드 API: <https://developers.naver.com/docs/serviceapi/datalab/search/search.md>

## 3. 전체 처리 흐름

```text
[서버 시작 시 누락 배치 복구 + 매일 05:00 KST 스케줄러]
          |
          v
[Google Trends KR RSS 수집]
          |
          v
[중복 제거·언어/지역·민감 주제 1차 필터]
          |
          v
[NAVER 뉴스 검색: 최근 기사 제목·요약문 보강]
          |
          v
[AI: displayTopic 재정의 + 맥락·근거 일관성 판정]
          |
          v
[NAVER 검색어 트렌드: 최근 14일 상승 신호 검증]
          |
          v
[Google·NAVER·뉴스 신호 합산 후 적격 후보 최대 20개 저장]
          |
          +-----------------------------+
          | 도서관별 KDC 균형 200권 조회|
          v                             |
[관계 보존 검색·도서관별 트렌드 재순위] |
          |                             |
          v                             |
[AI: 상위 후보 검증·추천 문구 생성]     |
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

1. RSS에서 상위 20개를 읽어 `sourceKeyword`로 보관한다.
2. 앞뒤 공백, 연속 공백, 대소문자를 정규화한다.
3. 한글·영문·숫자 비율과 관련 기사 출처를 확인해 한국과 무관한 외국어 검색 결과를 1차 제외한다. 영문 서비스명처럼 국내 기사 근거가 있는 키워드는 허용한다.
4. 같은 사건을 나타내는 유사 검색어는 하나의 대표 후보로 묶는다.
5. `sourceKey`는 공급자가 주는 식별자가 있으면 사용하고, 없으면 `정규화 키워드 + 시작 시각`의 SHA-256 해시로 만든다.
6. Google 검색량은 `trafficLabel`에 원문 문자열로 보관하고 네이버 상대 검색 비율과 같은 수치로 직접 비교하지 않는다.
7. 각 후보를 NAVER 뉴스 검색 API에 `display=5&sort=date`로 조회한다. `<b>` 같은 검색 강조 HTML 태그를 제거하고 최근 48시간 이내의 제목·요약문·링크만 근거로 저장한다.
8. RSS 기사와 네이버 뉴스 결과가 서로 다른 사건을 가리키면 `EVIDENCE_MISMATCH`로 제외한다.

### 4.2 주제 재정의와 국내 상승 검증

RSS의 원문 검색어와 사용자에게 보여줄 실제 사건 주제를 분리한다.

- `sourceKeyword`: Google Trends 원문 검색어. 추적·디버깅·출처 상세용이다.
- `displayTopic`: RSS 기사와 네이버 뉴스 근거에서 AI가 다시 추출한 공통 주제. 사용자 카드 제목으로 사용한다.
- `retrievalIntent`: `displayTopic`의 개념 관계를 그대로 유지한 도서 검색용 한 문장이다.
- `requiredConceptGroups`: 내부 배열은 동의어(OR), 외부 배열은 필수 개념(AND)인 관계 검증 조건이다.
- `topicConfidence`: 기사 근거들이 같은 주제를 가리키는 정도를 나타내는 `0.0~1.0` 값이다.
- `validationStatus`: 네이버 검색 상승 검증 결과인 `CONFIRMED`, `UNVERIFIED`, `CONTRADICTED` 중 하나다.

AI는 `displayTopic`을 한 단어 또는 짧은 구로 생성한다. 인명·기관명만 반복하지 말고 실제로 주목받은 사건이나 쟁점이 드러나야 한다. `topicConfidence < 0.70`이거나 기사 근거가 서로 다른 사건을 설명하면 추천 후보에서 제외한다.

네이버 검색어 트렌드 검증 규칙은 다음과 같다.

1. AI가 만든 `displayTopic`과 `sourceKeyword`를 하나의 키워드 그룹으로 묶는다.
2. KST 기준 최근 14일을 `timeUnit=date`로 조회한다.
3. 가장 최근 제공일의 비율을 직전 7일 평균과 비교해 `naverSpikeScore`를 계산한다.
4. 네이버 API가 한 요청에 최대 5개 그룹을 허용하므로 후보 20개는 최대 4회로 나누어 조회한다.
5. 네이버 데이터가 없거나 검색량이 너무 작으면 `UNVERIFIED`로 두고 탈락시키지 않는다.
6. 최근 값이 기준선보다 뚜렷하게 높으면 `CONFIRMED`, 장기 하락과 명백히 충돌하면 `CONTRADICTED`로 기록한다.

최종 노출 순위의 기본 가중치는 다음과 같다. 한 신호가 없으면 사용 가능한 신호끼리 가중치를 재정규화한다.

```text
finalTrendScore
= Google 급상승 순위·검색량 점수 0.40
 + NAVER 최근 상승 점수          0.35
 + 뉴스 근거 일관성 점수         0.25
```

`CONTRADICTED`는 무조건 제외하지 않지만, 최종 점수에서 감점한다. 민감 사건과 근거 불일치는 점수와 관계없이 제외한다.

### 4.3 안전성 필터

아래 항목은 도서 추천으로 가볍게 소비될 위험이 있으므로 기본 제외한다.

- 사망·재난·참사·강력 범죄처럼 피해자가 존재하는 사건
- 자해, 혐오, 선정적 사건 또는 미성년자 대상 범죄
- 사실 확인 근거가 부족한 인물 루머
- 단순 인명·경기 결과처럼 도서와 의미 있는 연결이 어려운 검색어
- AI가 후보 도서와 연결하기 어렵다고 판정한 검색어

AI 판정값은 `ELIGIBLE`, `SENSITIVE`, `NO_BOOK_MATCH`, `EVIDENCE_MISMATCH` 중 하나로 제한한다. `ELIGIBLE`만 추천 생성 단계로 넘긴다. AI 출력 파싱에 실패하면 안전하게 제외하고 로그만 남긴다.

### 4.4 AI 생성 문구

문구는 두 단계로 생성한다.

#### A. 트렌드 주제 재정의와 맥락 설명

- 입력: `sourceKeyword`, Google 관련 기사 제목·URL, 네이버 뉴스 제목·요약문·URL·발행 시각
- 출력: `displayTopic`, `topicConfidence`, `contextDescription`, `retrievalIntent`, `requiredConceptGroups`, `eligibility`, `evidenceConsistencyScore`
- `displayTopic`은 기사들의 공통 주제를 한 단어 또는 짧은 구로 재정의한다.
- `contextDescription`은 1~2문장으로 작성한다.
- 원칙: 제공된 근거 안에서만 설명하고 확인되지 않은 원인·수치·전망을 만들지 않는다.
- 예시: `원·달러 환율 변동 폭이 커지면서 환율과 개인 자산 관리에 대한 관심이 높아지고 있습니다.`

#### B. 트렌드 연계 도서 추천 문구

- 입력: `displayTopic`, `retrievalIntent`, 검증된 맥락, 필수 개념군을 모두 통과한 상위 15권
- 출력: `recommendationTitle`, 선정 ISBN, ISBN별 `matchScore`·`reason`
- 예시 제목: `격동하는 환율, 돈의 흐름을 읽는 법`
- 예시 이유: `환율이 생활비와 투자 판단에 어떤 영향을 주는지 기초 경제 원리부터 차근차근 이해하도록 돕는 책입니다.`
- AI는 입력 후보에 포함된 ISBN만 반환할 수 있다.
- 서버는 AI가 반환한 ISBN이 실제 해당 도서관의 후보군에 있는지 다시 검증한다.
- 같은 날짜·도서관 안에서는 가능한 한 같은 ISBN을 중복 추천하지 않는다.
- 추천할 만한 도서가 없으면 억지로 연결하지 않고 해당 트렌드를 결과에서 제외한다.
- `항공 AI`를 `항공`, `AI`로 독립 검색해 합치는 방식은 사용하지 않는다. 모든 필수 개념군을 충족해야 한다.

### 4.5 AI 응답 내부 계약

트렌드 보강 단계의 내부 JSON 계약은 다음과 같다.

```json
{
  "items": [
    {
      "sourceKey": "google-trend-key",
      "displayTopic": "항공 분야 AI 기술 도입",
      "topicConfidence": 0.91,
      "contextDescription": "항공 운영과 안전 관리에 AI를 적용하려는 정책과 서비스가 주목받고 있습니다.",
      "retrievalIntent": "항공 운항과 공항 운영에 적용되는 인공지능 기술",
      "requiredConceptGroups": [["항공", "비행", "공항"], ["AI", "인공지능", "머신러닝"]],
      "eligibility": "ELIGIBLE",
      "evidenceConsistencyScore": 0.88
    }
  ]
}
```

도서 추천 단계에서도 JSON 모드를 사용하고 다음 구조만 허용한다. 이 구조는 서버 내부 계약이며 프론트엔드에는 직접 노출하지 않는다.

```json
{
  "items": [
    {
      "trendId": 31,
      "recommendationTitle": "격동하는 환율, 돈의 흐름을 읽는 법",
      "books": [
        {
          "isbn": "9788960867450",
          "matchScore": 0.82,
          "reason": "환율 변화가 개인의 소비와 자산에 미치는 영향을 이해하도록 돕습니다."
        }
      ]
    }
  ]
}
```

서버 검증 조건은 다음과 같다.

- `displayTopic`은 100자 이하이고 `topicConfidence`는 `0.0~1.0`이어야 한다.
- AI가 입력 근거에 없는 URL, 수치, 인과관계를 추가하면 해당 항목을 폐기한다.
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
    "sources": [
      {
        "type": "GOOGLE_TRENDS",
        "name": "Google Trends",
        "role": "DISCOVERY",
        "region": "KR",
        "fetchedAt": "2026-08-15T05:00:12+09:00",
        "url": "https://trends.google.com/trending?geo=KR"
      },
      {
        "type": "NAVER_NEWS",
        "name": "NAVER 뉴스 검색",
        "role": "EVIDENCE",
        "region": "KR",
        "fetchedAt": "2026-08-15T05:00:45+09:00",
        "url": "https://search.naver.com/search.naver?where=news"
      },
      {
        "type": "NAVER_DATALAB",
        "name": "NAVER 데이터랩",
        "role": "VALIDATION",
        "region": "KR",
        "fetchedAt": "2026-08-15T05:01:10+09:00",
        "url": "https://datalab.naver.com/keyword/trendSearch.naver"
      }
    ],
    "items": [
      {
        "trendId": 31,
        "sourceKeyword": "환율 급등",
        "displayTopic": "원·달러 환율 변동",
        "finalRank": 2,
        "trafficLabel": "10K+",
        "startedAt": "2026-08-15T01:20:00+09:00",
        "topicConfidence": 0.94,
        "validationStatus": "CONFIRMED",
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
| `sources` | Array | 후보 발견·뉴스 근거·국내 검증에 사용한 원천과 수집 시각. 화면 하단 출처 표시에 사용 |
| `sources[].role` | String | `DISCOVERY`, `EVIDENCE`, `VALIDATION` 중 해당 원천의 역할 |
| `items` | Array | `finalRank` 오름차순으로 정렬된 추천 목록 |
| `sourceKeyword` | String | Google Trends 원문 검색어. 추적과 출처 상세용이며 카드 제목으로 사용하지 않음 |
| `displayTopic` | String | 기사 근거에서 AI가 재정의한 실제 주제. 프론트 카드의 트렌드 제목으로 사용 |
| `topicConfidence` | Number | 기사 근거들이 같은 주제를 가리키는 신뢰도, `0.0~1.0` |
| `validationStatus` | String | 네이버 검색 상승 교차 검증 결과: `CONFIRMED`, `UNVERIFIED`, `CONTRADICTED` |
| `contextDescription` | String | 트렌드가 주목받는 맥락을 설명하는 AI 문구 |
| `recommendationTitle` | String | 트렌드와 도서 묶음을 연결하는 전시형 제목 |
| `books[].reason` | String | 해당 도서를 이 트렌드에 추천하는 개별 문구 |

#### 캐시·대체 결과 조건

- `date`를 생략했거나 오늘을 요청했고 오늘 생성분이 있으면 `CURRENT`를 반환한다.
- 오늘 생성이 실패했으면 최근 3일 이내의 가장 최신 성공 결과를 `FALLBACK`으로 반환한다.
- `FALLBACK`이면 `requestedDate`와 `recommendationDate`가 다르다.
- 과거 날짜를 명시한 요청은 그 날짜의 정확한 결과만 반환하고 자동 대체하지 않는다.
- 응답에는 `Cache-Control: public, max-age=300`과 `ETag`를 적용한다.
- 결과 배열은 항상 `finalRank` 오름차순, 책은 `displayOrder` 오름차순이다.

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
    "sources": [
      {
        "type": "GOOGLE_TRENDS",
        "name": "Google Trends",
        "role": "DISCOVERY",
        "region": "KR",
        "fetchedAt": "2026-08-14T05:00:10+09:00",
        "url": "https://trends.google.com/trending?geo=KR"
      },
      {
        "type": "NAVER_NEWS",
        "name": "NAVER 뉴스 검색",
        "role": "EVIDENCE",
        "region": "KR",
        "fetchedAt": "2026-08-14T05:00:43+09:00",
        "url": "https://search.naver.com/search.naver?where=news"
      },
      {
        "type": "NAVER_DATALAB",
        "name": "NAVER 데이터랩",
        "role": "VALIDATION",
        "region": "KR",
        "fetchedAt": "2026-08-14T05:01:08+09:00",
        "url": "https://datalab.naver.com/keyword/trendSearch.naver"
      }
    ],
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
    "sources": [],
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

Flyway 파일명: `V202608180001__create_daily_trend_recommendations.sql`

현재 저장소의 마지막 migration(`V202608150004`) 이후 번호를 사용한다. 실제 병합 전에 최신 migration 번호와 충돌 여부를 다시 확인한다.

### 8.1 `daily_trends`

트렌드 원문과 AI 맥락을 날짜별로 한 번 저장한다.

| 컬럼 | 타입 | 제약/설명 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `trend_date` | DATE | KST 기준 날짜 |
| `source` | VARCHAR(30) | 후보 발견 원천, MVP에서는 `GOOGLE_TRENDS` |
| `source_key` | VARCHAR(255) | 공급자 항목 식별자 또는 서버 생성 해시 |
| `source_keyword` | VARCHAR(200) | Google Trends 원문 검색어, 추적·디버깅용 |
| `normalized_source_keyword` | VARCHAR(200) | 중복 비교용 정규화 검색어 |
| `display_topic` | VARCHAR(200) | 뉴스 근거에서 AI가 재정의한 화면 표시 주제 |
| `topic_confidence` | DECIMAL(4,3) | 주제 재정의 신뢰도 `0.000~1.000` |
| `google_rank` | INT | Google Trends 원천 순위 |
| `google_traffic_label` | VARCHAR(50) | Google이 제공한 검색량 문자열, nullable |
| `started_at` | DATETIME(6) | 트렌드 시작 시각, nullable |
| `source_url` | VARCHAR(2048) | 대표 원천 링크 |
| `google_news_evidence` | JSON | RSS가 제공한 기사 제목·URL·출처 목록 |
| `naver_news_evidence` | JSON | NAVER 뉴스 검색의 제목·요약문·URL·발행 시각 목록 |
| `context_description` | VARCHAR(1000) | AI가 생성한 중립적 맥락 설명 |
| `retrieval_intent` | VARCHAR(500) | 개념 관계를 보존한 도서 검색 문장 |
| `required_concepts` | JSON | 외부 배열 AND·내부 배열 OR인 필수 개념군 |
| `eligibility` | VARCHAR(30) | `ELIGIBLE`, `SENSITIVE`, `NO_BOOK_MATCH`, `EVIDENCE_MISMATCH` |
| `evidence_consistency_score` | DECIMAL(4,3) | Google·NAVER 뉴스 근거 일관성 `0.000~1.000` |
| `validation_status` | VARCHAR(30) | `CONFIRMED`, `UNVERIFIED`, `CONTRADICTED` |
| `naver_spike_score` | DECIMAL(8,4) | 네이버 최근 검색 상승 점수, nullable |
| `final_trend_score` | DECIMAL(8,4) | 세 신호를 합산한 최종 노출 점수 |
| `fetched_at` | DATETIME(6) | 외부 원천 수집 시각 |
| `news_enriched_at` | DATETIME(6) | NAVER 뉴스 근거 보강 시각, nullable |
| `validated_at` | DATETIME(6) | NAVER 검색어 트렌드 검증 시각, nullable |
| `created_at` | DATETIME(6) | 생성 시각 |

유니크 키는 `(trend_date, source, source_key)`로 둔다. 조회 인덱스는 `(trend_date, eligibility, final_trend_score)`로 둔다.

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
| `match_score` | DECIMAL(4,3) | 서버 점수와 AI 점수를 결합한 최종 도서 연관도 |
| `display_order` | INT | 트렌드 내 도서 순서 |
| `created_at` | DATETIME(6) | 생성 시각 |

유니크 키는 `(batch_id, isbn)`로 두어 한 배치에서 같은 책이 여러 트렌드에 중복 노출되지 않게 한다. 조회 인덱스는 `(library_code, batch_id, display_order)`로 둔다.

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

com.wakebook.external.naver
├─ NaverApiProperties
├─ NewsEvidenceProvider
├─ SearchTrendValidator
├─ NaverNewsSearchClient
└─ NaverSearchTrendClient
```

핵심 인터페이스는 공급자 응답 형식을 도메인에서 분리한다.

```java
public interface TrendProvider {
    List<TrendItem> fetchDailyTrends(String region, int limit);
}

public interface NewsEvidenceProvider {
    List<NewsEvidence> search(String keyword, int limit);
}

public interface SearchTrendValidator {
    SearchTrendValidation validate(String sourceKeyword, String displayTopic);
}
```

네이버 Open API 클라이언트는 공통 인증 헤더 `X-Naver-Client-Id`, `X-Naver-Client-Secret`을 사용한다. 뉴스 검색은 `GET /v1/search/news.json`, 검색 추이 검증은 `POST /v1/datalab/search`를 호출한다. 응답의 검색 강조용 `<b>` 태그는 저장 전에 제거한다. Client Secret은 소스와 프론트에 포함하지 않고 서버 환경변수로만 주입한다.

`DailyTrendQueryService`는 공개 API와 사서 미리보기 API가 공통으로 사용한다. 사서 API에서는 컨트롤러가 받은 `libraryCode`가 아니라 JWT subject로 조회한 `User.libraryCode`만 서비스에 전달한다.

## 10. 스케줄·재시도·동시성

### 10.1 정상 실행

1. 매일 05:00 KST에 Google Trends 후보 20개를 수집한다.
   서버 시작 시 오늘 성공 배치가 없으면 같은 작업을 즉시 요청하며, 재시작 전에 중단된 `PENDING/PROCESSING` 배치도 다시 큐에 넣는다.
2. 1차 필터를 통과한 후보마다 NAVER 뉴스 검색으로 최근 근거를 보강한다.
3. AI가 `displayTopic`, 맥락, 근거 일관성, 안전성을 생성·판정한다.
4. NAVER 검색어 트렌드를 최대 5개 그룹씩 나누어 최근 14일 상승 신호를 검증한다.
5. 세 신호를 합산해 적격 `daily_trends` 후보를 최대 20개 보존한다.
6. `hidden_books`에서 `DISTINCT library_code`를 조회한다.
7. 도서관마다 트렌드 점수 45%와 도서 매칭 점수 55%를 합산해 최종 최대 5개를 고른다.
8. 도서관마다 `daily_trend_batches`를 생성하고 추천을 만든다.
9. 유효한 추천이 1개 이상이면 배치를 `COMPLETED`로 바꾼다.
10. 0개이면 `FAILED`와 오류 코드를 기록하고 전날 결과를 보존한다.

### 10.2 실패 복구

- Google RSS와 네이버 Open API 호출 실패는 후보별로 격리하고 나머지 후보 처리를 계속한다.
- NAVER 뉴스 검색이 실패하면 빈 근거로 계속 처리하고, 검색어 트렌드가 실패하면 `UNVERIFIED`로 둔다.
- Google Trends 또는 OpenAI 단계가 실패하면 해당 도서관 배치를 `FAILED`와 오류 코드로 기록한다.
- 서버 시작 시 오늘 성공 배치가 없는 도서관을 다시 요청하며, 이전 실행에서 중단된 `PENDING/PROCESSING`도 복구한다.
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
trend.google.base-url=https://trends.google.com
trend.google.region=KR
naver-api.base-url=https://openapi.naver.com
naver-api.client-id=${NAVER_CLIENT_ID:}
naver-api.client-secret=${NAVER_CLIENT_SECRET:}
trend.candidate-limit=${TREND_CANDIDATE_LIMIT:20}
trend.top-trend-count=${TREND_TOP_COUNT:5}
trend.books-per-trend=${TREND_BOOKS_PER_TREND:2}
trend.minimum-topic-confidence=${TREND_MIN_TOPIC_CONFIDENCE:0.70}
trend.minimum-evidence-consistency=${TREND_MIN_EVIDENCE_CONSISTENCY:0.70}
trend.minimum-book-match-score=${TREND_MIN_BOOK_MATCH_SCORE:0.60}
trend.library-trend-candidate-count=${TREND_LIBRARY_CANDIDATE_COUNT:10}
trend.fallback-days=${TREND_FALLBACK_DAYS:3}
trend.force-refresh-cooldown-minutes=${TREND_REFRESH_COOLDOWN_MINUTES:30}
trend.scheduler-enabled=${TREND_SCHEDULER_ENABLED:true}
trend.schedule-cron=${TREND_SCHEDULE_CRON:0 0 5 * * *}
```

운영·테스트에서는 `trend.scheduler-enabled=false`로 자동 실행을 끈다. 일반 테스트는 실제 RSS, 네이버 Open API, OpenAI를 호출하지 않고 fake provider/client를 주입한다.

별도 사업 제휴 없이 네이버 개발자센터 애플리케이션에 `검색`, `데이터랩`을 추가하고 발급받은 Client ID와 Client Secret을 사용한다. 뉴스 검색의 하루 한도는 25,000회, 데이터랩 통합 검색어 트렌드는 1,000회다. 언론사 기사 본문은 직접 수집·저장하지 않는다.

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

- `displayTopic`: 트렌드 카드 제목. `sourceKeyword`를 카드 제목으로 사용하지 않음
- `contextDescription`: 트렌드 카드의 맥락 설명
- `validationStatus`: `CONFIRMED`이면 국내 검색 상승 확인 배지를 선택적으로 표시하고, `UNVERIFIED`·`CONTRADICTED`는 사용자에게 오류처럼 표시하지 않음
- `recommendationTitle`: 카드 제목
- `books[].reason`: 각 도서 아래 AI 추천 이유
- `sources`: 화면 하단에 `트렌드 발견: Google Trends · 국내 검증 및 뉴스 근거: NAVER`와 갱신 시각 표시
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
- `PENDING` 또는 `PROCESSING`이면 현재 구현은 2초 간격으로 상태를 확인한다.
- `COMPLETED`가 되면 미리보기 API를 다시 호출한다.
- `FAILED`여도 기존 추천 화면은 유지하고 오류 알림만 표시한다.
- `force=true` 호출 전에는 OpenAI 비용과 오늘 노출 결과 변경 가능성을 확인하는 confirm UI를 둔다.

## 13. 구현 순서

### 1단계 — 계약·DB 기반

- 이 문서의 필드명과 enum을 프론트와 확정한다.
- Flyway migration과 JPA entity/repository를 구현한다.
- 날짜 계산은 모든 서비스에서 `Clock`과 `ZoneId`를 주입해 테스트 가능하게 만든다.

### 2단계 — Google 트렌드 후보 수집

- `TrendProvider`와 Google RSS client/parser를 구현한다.
- XML 크기 제한, 외부 엔티티 비활성화(XXE 방지), 타임아웃을 적용한다.
- RSS fixture 기반 파서 테스트를 작성한다.
- 중복 제거, 언어·지역 1차 필터, `sourceKey` 생성 로직을 구현한다.

### 3단계 — NAVER 뉴스 근거 보강

- 네이버 개발자센터 Application에 `검색`, `데이터랩`을 추가하고 인증 정보를 환경변수로 설정한다.
- `GET /search/v1/news` 클라이언트를 구현한다.
- 후보별 최근 48시간 기사 5건의 제목·요약문·링크·발행 시각을 정규화한다.
- HTML 검색 강조 태그 제거, 빈 결과, timeout, 부분 실패 테스트를 작성한다.
- 언론사 원문 본문을 직접 크롤링하지 않는다.

### 4단계 — AI 주제 재정의·안전성 처리

- Google RSS와 NAVER 뉴스 근거를 바탕으로 후보를 JSON 배치 분류·요약한다.
- `displayTopic`, `topicConfidence`, `contextDescription`, `evidenceConsistencyScore`를 생성한다.
- enum, 길이, 필수값을 검증하고 잘못된 항목만 제외한다.
- 원문 검색어와 기사 실제 주제가 달라도 `displayTopic`으로 일관되게 재정의되는지 테스트한다.
- 민감 주제, 근거 불일치, 근거 없는 설명이 저장되지 않는 테스트를 작성한다.

### 5단계 — NAVER 검색 상승 검증·최종 순위

- `POST /search-trend/v1/search` 클라이언트를 구현한다.
- 후보를 최대 5개 그룹씩 나누어 최근 14일 일간 추이를 조회한다.
- 최근 제공일과 직전 7일 기준선으로 `naverSpikeScore`와 `validationStatus`를 계산한다.
- Google·NAVER·뉴스 점수를 합산하고 신호 누락 시 가중치를 재정규화한다.
- 전역 단계에서는 적격 후보를 최대 20개 저장하고, 도서관별 매칭 가능성을 반영해 최종 최대 5개를 확정한다.

### 6단계 — 도서관별 추천

- `libraryCode`별 `hidden_books`를 가져온다.
- KDC 10개 대분류를 가능한 한 균등하게 채운 최대 200권을 검색 대상으로 사용한다.
- `displayTopic + contextDescription + retrievalIntent` 전체 문맥으로 서버가 상위 15권을 추린다.
- 필수 개념군을 모두 만족하지 않는 책은 AI 호출 전에 제외한다.
- AI가 반환한 ISBN을 서버 후보 집합과 대조한다.
- 추천 결과를 도서 정보 스냅샷으로 저장한다.
- 동일 ISBN 중복 제거와 결과 교체의 원자성을 테스트한다.

### 7단계 — 스케줄러·복구

- `@EnableScheduling`, 일일 스케줄, 30분 복구 스케줄을 구현한다.
- 배치 상태 전이와 중복 실행 방지를 구현한다.
- 외부 실패 시 전날 데이터를 반환하는 fallback 테스트를 작성한다.

### 8단계 — HTTP API·보안

- 사용자 조회, 사서 미리보기, 재생성, 배치 상태 API를 구현한다.
- `SecurityConfig`에 `GET /trends/**`만 공개로 추가한다.
- `/librarian/trends/**`는 기존 `/librarian/**` 규칙으로 `LIBRARIAN`만 허용한다.
- 사서 API가 JWT 사용자의 도서관 코드만 사용하는지 컨트롤러 테스트로 검증한다.

### 9단계 — 프론트 연동·운영 확인

- 실제 API 응답으로 카드와 상태 UI를 연결한다.
- CURRENT, FALLBACK, 빈 후보, 생성 중, 실패 상태를 각각 확인한다.
- 운영 환경에서 1일 OpenAI 호출량과 평균 배치 시간을 측정한 뒤 노출 트렌드 수를 조정한다.

## 14. 테스트 범위

### 단위 테스트

- RSS 정상/빈 응답/깨진 XML/중복 키워드 파싱
- `geo=KR` RSS에 섞인 한국과 무관한 외국어 결과 제외
- NAVER 뉴스 제목·요약문의 `<b>` 태그 제거 및 최근 48시간 필터
- Google 기사와 NAVER 뉴스가 서로 다른 사건이면 `EVIDENCE_MISMATCH`
- 원문 검색어와 `displayTopic` 분리 및 `topicConfidence` 하한 검증
- NAVER 검색어 트렌드 5개 그룹 분할 호출
- 최근 값과 7일 기준선의 `naverSpikeScore` 계산
- NAVER 검증 데이터가 없으면 탈락이 아니라 `UNVERIFIED`
- 신호 누락 시 최종 가중치 재정규화
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

- 스케줄 실행 → Google 후보 → NAVER 뉴스 → AI 주제 재정의 → NAVER 검색 검증 → 추천 저장 → GET 조회 전체 흐름
- 트렌드 공급자 5xx/timeout 시 재시도와 기존 결과 유지
- NAVER 뉴스 일부 실패 시 나머지 후보로 배치 계속 진행
- NAVER 검색어 트렌드 전체 실패 시 Google·뉴스 신호만으로 제한적 생성
- OpenAI 실패 시 배치 `FAILED` 기록
- CSV 후보군 교체 후 기존 추천 스냅샷 조회 가능
- 일반 사용자 `/librarian/trends/**` 접근 시 403
- 다른 사서의 `batchId` 조회 시 404

### API 계약 테스트

- JSON 필드명과 enum 고정
- 공개 응답에 `sourceKeyword`, `displayTopic`, `topicConfidence`, `validationStatus`, `sources` 포함
- 날짜는 ISO 8601, 시각은 `+09:00` 오프셋 포함
- `items`와 `books`는 빈 값일 때도 `null`이 아니라 `[]`
- 200 응답에는 최소 추천 1개 존재
- 오류 응답은 공통 `success/code/message/data` 구조 사용

## 15. 완료 조건(Definition of Done)

- [ ] 매일 KST 기준 트렌드가 자동 수집된다.
- [ ] Google Trends 후보에 NAVER 뉴스 제목·요약문 근거가 보강된다.
- [ ] 원문 검색어와 화면 표시용 `displayTopic`이 분리되어 저장·응답된다.
- [ ] NAVER 검색어 트렌드로 최근 14일 국내 상승 신호가 교차 검증된다.
- [ ] NAVER 검증 데이터가 없어도 `UNVERIFIED`로 처리하고 전체 배치를 실패시키지 않는다.
- [ ] 기사 본문 직접 크롤링 없이 공식 API 응답 범위만 사용한다.
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
- [ ] 화면에 Google Trends와 NAVER의 역할별 출처와 수집 시각이 표시된다.

## 15.1 실제 API 검증 결과와 미충족 조건 (2026-08-20)

2026-08-20의 NAVER 수치는 이전 API Hub 구현에서 기록한 과거 결과다. 2026-08-22에 사용자가 등록한 네이버 개발자센터 `검색`·`데이터랩` 상품에 맞춰 주소와 헤더를 교체했으며, 공식 경로·헤더·응답 파싱 계약을 재현한 테스트가 통과했다. 실제 키를 사용하는 재검증은 `ExternalApiLiveSmokeTest.actualNaverOpenApiReturnsNewsAndSearchTrend`로 분리한다.

| 검증 항목 | 실제 결과 | 판정 |
|---|---|---|
| Google Trends KR RSS | 10건 수집 | 연결·파싱 통과 |
| NAVER 뉴스 검색 | 3건, 제목·URL 확인 | 통과 |
| NAVER 검색 추이 | `spikeScore=1.2641278263579958` | 통과 |
| OpenAI JSON 응답 | `{ "status": "ok" }` | 통과 |
| 관계 보존형 트렌드 메타데이터 | `displayTopic`, 맥락, 검색 의도, 필수 개념 그룹 생성 | 계약 통과 |

실제 메타데이터 응답의 핵심 값은 다음과 같았다.

```json
{
  "displayTopic": "트럼프가 김정은의 핵무기 보유 수를 공개하며 연내 회담을 예고했다.",
  "retrievalIntent": "트럼프와 김정은의 핵무기 관련 대화 의도를 탐색하기 위해 검색한다.",
  "requiredConceptGroups": [
    ["북한", "핵무기", "회담"],
    ["트럼프", "김정은", "정치"]
  ]
}
```

다만 첫 Google Trends `sourceKeyword`가 `개`로 반환됐다. 이는 RSS 연결 실패가 아니라 원천 후보 품질 문제다. 현재 완료 조건 중 “키워드가 사용자에게 의미 있는 주제로 재정의된다”는 항목은 아직 미충족으로 판단한다.

추가 구현 기준은 다음과 같다.

- 한 글자 키워드와 `개`, `명`, `건`, `회` 같은 단위·의존명사 제거
- 불용어 후보는 AI 입력 전에 제외하고 다음 순위 후보로 대체
- `displayTopic`을 한 단어 또는 짧은 명사구로 제한
- `displayTopic`과 기사 근거의 핵심 개념이 일정 비율 이상 겹치는지 검증
- 기준 미달 결과는 공개 추천에 저장하지 않음

도서 상세 실제 호출에서는 ISBN `9788937473135`에 대해 세 공급자가 모두 제목·저자·출판사·소개글을 반환했고, 통합 공급자는 카카오 응답을 선택했다. 따라서 **카카오 → 알라딘 → 정보나루** 폴백 순서도 실제 환경에서 확인됐다.

전체 원문 응답과 공개 큐레이션 HTTP 검증, Flyway 주의사항은 [통합 및 실제 API 검증 결과](./통합및실API검증결과.md)에 기록한다.

## 16. MVP 이후 확장

- Google Trends 외에 다른 합법적 급상승 후보 공급자를 `TrendProvider`로 추가
- NAVER 외의 뉴스·검색 신호를 동일 인터페이스로 추가해 복수 공급자 비교
- 지역별 트렌드와 도서관 소재 지역을 연결한 지역 맞춤 추천
- 트렌드·도서 임베딩을 저장해 AI 호출 전 후보를 더 정교하게 축소
- 사서가 민감하지 않은 트렌드를 승인·숨김 처리하는 검수 화면
- 추천 노출·상세 조회·책장 저장 이벤트를 기록해 트렌드 추천 효과 측정
- 실제 반응 데이터를 이용한 트렌드 관련성·발견 가치 가중치 개선
