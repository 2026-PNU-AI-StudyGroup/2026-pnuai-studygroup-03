package com.wakebook.external.library;

import java.time.LocalDate;

public record PopularLoanBookCriteria(
    LocalDate startDt,
    LocalDate endDt,
    String kdc,
    String gender,
    Integer age,
    int pageNo,
    int pageSize
) {
}
