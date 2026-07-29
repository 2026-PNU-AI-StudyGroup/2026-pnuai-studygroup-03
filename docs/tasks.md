# 후속 작업 체크리스트

나중에 확인/보완이 필요한 항목을 기록해두는 파일입니다. 완료되면 체크박스만 채우지 말고 항목을 지워주세요.

## 3.2 도서 검색

도서관정보나루(data4library) `srchBooks`로 구현 완료. 공식 매뉴얼(`data4library.kr/downloadApiManual`, 16번 항목)로 요청/응답 필드를 전부 확인했고, 3.1 인기도서와 같은 `authKey`를 재사용하므로 별도 인증키 발급이 필요 없음.
(`backend/src/main/java/com/wakebook/external/library/Data4LibraryBookSearchProvider.java`)

- [ ] **`keyword` 파라미터의 일치검색 동작 확인**: 매뉴얼에 "키워드(keyword)를 입력할 경우 일치검색 결과만 제공"이라고 명시돼 있음. 실제 인증키로 호출해서 "심리"처럼 짧은 키워드를 넣었을 때 "이상심리학" 같은 부분일치 결과도 나오는지, 아니면 완전히 같은 단어만 걸리는지 확인 필요. 너무 엄격하면 `title`/`author`/`publisher` 파라미터 조합(단, 2개 이상 입력 시 AND 검색이라는 점 주의)으로 대체 검토.
      (`backend/src/main/java/com/wakebook/external/library/Data4LibraryBookSearchProvider.java`)

## 3.3 도서 상세 조회

도서관정보나루 API 조합으로 구현 완료:
- **description/publisher/publishedYear/cover**: `srchDtlList`(도서 상세 조회) 사용. (`Data4LibraryBookDetailProvider.java`)
- **libraries(소장 도서관·청구기호·대출가능여부)**: `libSrchByBook`(소장 도서관 목록, 최대 5곳) → 도서관마다 `bookExist`(대출가능여부) + `itemSrch`(청구기호 = `class_no` + `-` + `book_code`)를 추가 호출해서 조합. (`Data4LibraryHoldingProvider.java`)

- [ ] **`region` 필수 제약 UX 검토**: `libSrchByBook`은 `region`(지역코드)이 필수라서 "대한민국 전체에서 소장 도서관 찾기"가 한 번에 안 됨. 지금은 `region` 쿼리파라미터를 안 주면 `libraries: []`, `availability: "UNKNOWN"`으로 처리. 나중에 사용자 프로필에 소속 도서관/지역 정보가 생기면 자동으로 넘겨주는 방식으로 개선 검토.
- [ ] **소장 도서관 조회 성능**: 도서 1건 상세조회에 `libSrchByBook` 1회 + 도서관당 `bookExist`/`itemSrch` 2회씩(최대 5곳 = 최대 11회) 외부 API 호출이 발생함. 실사용 트래픽이 늘면 캐싱(예: 도서관 단위로 몇 분간 캐시) 검토 필요.
**tableOfContents(목차)**: 알라딘(Aladin) Open API `ItemLookUp`(`OptResult=Toc`, 응답 경로 `item[0].subInfo.toc`)로 구현 완료. SEOJI의 `BOOK_TB_CNT_URL`은 실제 인증키로 테스트해보니 빈 문자열이라 채택 안 함(출판사가 목차를 선택 제출이라 데이터 자체가 희소함). 국립중앙도서관/정보나루 같은 도서관·공공데이터가 아니라 민간 서점의 상업 API라는 점 참고(공모전 필수 데이터 요건은 이미 정보나루로 충족돼 있어 보조용으로 곁들이는 건 문제 없음).
(`backend/src/main/java/com/wakebook/external/aladin/AladinTableOfContentsProvider.java`)

- [ ] **`ALADIN_TTB_KEY` 환경변수 설정**: 발급받은 TTBKey를 로컬/배포 환경에 `ALADIN_TTB_KEY`로 설정해야 목차가 채워짐. 안 넣으면 `tableOfContents`는 항상 빈 배열(에러는 안 남).
- [ ] **`toc` 파싱 검증**: `<br>` 태그·줄바꿈 기준으로 문장을 쪼개도록 구현했는데, 실제 응답으로 여러 책을 받아보고 목차가 항목별로 잘 나뉘는지, 숫자/장 표시(`Ⅰ.`, `1.` 등)가 지저분하게 섞이진 않는지 확인 필요.

## 3.4/3.5 오늘의 잠자는 책·우연히 발견하기, 4번 AI 추천 API

`hidden_books` 테이블(도서관별로 구분 저장)을 3.4/3.5/4.2/4.4가 공통으로 사용하도록 구현 완료.
(`HiddenBookUploadService.java`, `HiddenBookCsvParser.java`, `HiddenBookService.java`, `com.wakebook.recommendation.*`)

- **후보군 소스가 실시간 API 호출이 아니라 사서의 CSV 업로드 방식으로 바뀜**: 처음에는 정보나루 `itemSrch`를 `libCode`만으로(isbn13 없이) 호출하면 도서관 전체 장서/대출건수를 받아올 수 있을 거라 가정하고 구현했었으나, 실제 호출 결과 `loan_count` 필드 자체가 없고 그냥 최근 등록 장서 목록만 나온다는 걸 확인함(대출 통계가 아님). "장서 대출목록"은 정보나루가 API가 아니라 [오픈데이터 페이지](https://data4library.kr/openDataV)에서 도서관·월 단위로 다운로드해야 하는 CSV였음 — 자동 다운로드 가능한 고정 URL 패턴도 없어서, 사서가 직접 CSV를 다운받아 `POST /librarian/hidden-books/upload`로 업로드하는 방식으로 재설계함(같은 도서관 코드의 기존 후보군은 업로드 시 전부 교체).
- [ ] **`OPENAI_API_KEY` 발급 및 환경변수 설정**: `openai.api-key`(env `OPENAI_API_KEY`)가 비어 있으면 업로드 처리 중 reason/keywords 생성과 4.1/4.2/4.3/4.4가 전부 `AI_001` 오류를 반환한다. 모델은 기본값 `gpt-4o-mini`(env `OPENAI_MODEL`로 변경 가능)로 잡아뒀는데 실제 사용 모델/비용을 팀에서 다시 확인 필요.
- [ ] **"도서 정보 품질"/"잠자는 도서 발견 가치" 점수 산정 로직은 임시 휴리스틱**: `HiddenBookUploadService.calculateQualityScore`(publisher/publishedYear/cover/description 유무 기반)와 `RecommendationScorer.discoveryValue`(대출건수를 후보군 내 min-max로 정규화)는 실제 데이터 없이 만든 근사치다. 실사용 데이터가 쌓이면 가중치/기준 재조정 검토.
- [ ] **CSV 컬럼명 의존성**: `HiddenBookCsvParser`는 정보나루 CSV의 정확한 한글 헤더(`도서명`, `저자`, `ISBN`, `대출건수`)로 값을 찾는다. 정보나루가 CSV 포맷이나 컬럼명을 바꾸면 파싱이 에러 없이 조용히 깨질 수 있음(해당 값이 비거나 0으로 들어감) — 업로드 응답의 `savedCount`가 평소보다 확 줄면 이걸 의심해볼 것.
- [ ] **업로드 신뢰 모델 검증 필요**: 지금은 `LIBRARIAN` 권한만 있으면 어떤 `libraryCode`/`libraryName`으로도 업로드할 수 있다. 회원가입 때 입력하는 `User.libraryName`(자유 텍스트)과 실제 업로드 대상 도서관이 일치하는지 검증하지 않으므로, 실수나 악의적 사용으로 다른 도서관의 후보군이 덮어써질 수 있음 — 추후 사서-도서관 매핑 검증 로직 필요.
- [ ] **대형 도서관 CSV 업로드 소요 시간**: 실제 부산광역시 금정도서관 CSV(약 30만 행)로 테스트해보니 업로드 1회에 5분 이상 걸림. `HiddenBookUploadService`가 대출건수 오름차순으로 정렬한 뒤 `candidatePoolSize`(30권)를 채울 때까지 후보를 하나씩 `srchDtlList`로 품질 검증하는데, 통과 못 하는 후보가 많으면(외국어 도서 등 메타데이터 부실) 시도 횟수 자체가 커진다. 트래픽이 늘면 "시도할 후보 수 자체에 상한"을 두거나 비동기 처리(업로드는 바로 202 응답, 처리는 백그라운드)로 개선 검토.
- **2026-07-29 실제 업로드 E2E 검증 완료**: 부산광역시 금정도서관(libCode 121018) 실제 CSV로 업로드→3.4/3.5/4.1/4.2/4.3/4.4 전부 실제 정보나루·OpenAI API로 호출 확인함. 이 과정에서 `HiddenBook.keywords`가 `@ElementCollection` LAZY 상태라 `open-in-view=false` 환경에서 컨트롤러 응답 직렬화 시점에 `LazyInitializationException`이 나는 버그를 발견해 `FetchType.EAGER`로 수정함. 또한 `spring.servlet.multipart.max-file-size`/`max-request-size` 기본값(10MB)이 실제 CSV(약 49MB)보다 작아 업로드가 실패해서 200MB로 올림.
- [ ] **`hidden-book.max-loan-count`(기본 2)/`hidden-book.candidate-pool-size`(기본 30) 기준값 재검토**: 실제 CSV 데이터 분포를 보고 조정 필요.
