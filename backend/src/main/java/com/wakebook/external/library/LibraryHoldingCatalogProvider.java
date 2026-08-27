package com.wakebook.external.library;

import java.time.LocalDate;

/**
 * 도서관별 장서 목록(itemSrch). 정보나루는 이 목록에 대출건수를 주지 않기 때문에,
 * "저이용" 판단은 대출 순위 목록(loanItemSrch)에 없는지로 대신한다.
 */
public interface LibraryHoldingCatalogProvider {

    HoldingCatalogResult fetch(String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo, int pageSize);
}
