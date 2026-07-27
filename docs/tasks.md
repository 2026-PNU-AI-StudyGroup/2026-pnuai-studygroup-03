# 후속 작업 체크리스트

나중에 확인/보완이 필요한 항목을 기록해두는 파일입니다. 완료되면 체크박스만 채우지 말고 항목을 지워주세요.

## 3.2 도서 검색

국립중앙도서관 SEOJI API 대신 **도서관정보나루(data4library) `srchBooks`**로 전환 완료. 공식 매뉴얼(`data4library.kr/downloadApiManual`, 16번 항목)로 요청/응답 필드를 전부 확인했고, 3.1 인기도서와 같은 `authKey`를 재사용하므로 별도 인증키 발급이 필요 없음.
(`backend/src/main/java/com/wakebook/external/library/Data4LibraryBookSearchProvider.java`)

- [ ] **`keyword` 파라미터의 일치검색 동작 확인**: 매뉴얼에 "키워드(keyword)를 입력할 경우 일치검색 결과만 제공"이라고 명시돼 있음. 실제 인증키로 호출해서 "심리"처럼 짧은 키워드를 넣었을 때 "이상심리학" 같은 부분일치 결과도 나오는지, 아니면 완전히 같은 단어만 걸리는지 확인 필요. 너무 엄격하면 `title`/`author`/`publisher` 파라미터 조합(단, 2개 이상 입력 시 AND 검색이라는 점 주의)으로 대체 검토.
      (`backend/src/main/java/com/wakebook/external/library/Data4LibraryBookSearchProvider.java`)

## 3.3 도서 상세 조회 (아직 미구현)

공식 매뉴얼 기준으로 필요한 데이터를 도서관정보나루 API 조합으로 채울 수 있음:

- **description(책소개)**: `srchDtlList`(도서 상세 조회) API의 `description` 필드 사용.
- **libraries(도서관별 소장여부·청구기호·대출가능여부)**: 아래 3개 API를 조합해야 함 (단일 API로는 안 됨).
  - `libSrchByBook` (isbn+region) → 소장 도서관 목록(도서관명, 주소 등). 단, `region` 파라미터가 필수라 "전국"을 한 번에 조회할 수 없음 — 사용자 지역 기반으로 호출하거나 지역 코드를 순회해야 함.
  - `bookExist` (libCode+isbn13) → 특정 도서관의 소장여부(`hasBook`)·대출가능여부(`loanAvailable`).
  - `itemSrch` (libCode+isbn13+type=ALL) → 청구기호(`callNumbers.callNumber`). `libSrchByBook`/`bookExist`엔 청구기호가 없음.
- **tableOfContents(목차)**: data4library 쪽엔 목차 필드가 없음. 국립중앙도서관 SEOJI API(`BOOK_TB_CNT_URL`)가 후보이나, 이 필드는 텍스트가 아니라 목차 페이지/파일 **링크**라 실제로 목차를 채우려면 그 링크를 한 번 더 가져와 파싱해야 함. SEOJI 인증키(`nl.go.kr` 회원가입 필요)는 아직 발급 안 함.
- `docs/API명세.md`의 `publishedYear`는 `srchDtlList`의 `publication_year` 그대로 사용 가능.
