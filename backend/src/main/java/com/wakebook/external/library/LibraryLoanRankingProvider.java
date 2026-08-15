package com.wakebook.external.library;

import java.time.LocalDate;
import java.util.Set;

/**
 * 도서관별 대출 순위(loanItemSrch에 libCode 지정). 정보나루는 libCode를 주면 대출건수 없이 순위만 주므로,
 * "몇 회 대출됐는가"가 아니라 "순위 안에 들었는가"만 알 수 있다. 순위 밖 장서를 저이용으로 본다.
 */
public interface LibraryLoanRankingProvider {

    Set<String> fetchRankedIsbns(String libraryCode, LocalDate startDt, LocalDate endDt);
}
