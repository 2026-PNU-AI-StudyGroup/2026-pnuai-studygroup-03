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
