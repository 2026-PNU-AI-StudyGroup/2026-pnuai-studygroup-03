package com.wakebook.book.dto;

import com.wakebook.book.domain.HiddenBookSource;

/**
 * 잠자는 도서 후보군(hidden_books)이 등록돼 있는 도서관 목록. 이용자는 도서관 코드를 외울 수 없으므로,
 * 추천이 실제로 동작하는 도서관만 골라서 보여 주기 위한 응답이다.
 *
 * @param source 후보군 산출 근거. 정밀도가 다르므로 화면에서도 구분해 보여 준다.
 */
public record LibrarySummaryResponse(
    String libraryCode,
    String libraryName,
    HiddenBookSource source,
    long hiddenBookCount
) {
}
