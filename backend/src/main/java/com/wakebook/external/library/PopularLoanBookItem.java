package com.wakebook.external.library;

public record PopularLoanBookItem(
    String isbn,
    String title,
    String author,
    String cover,
    int rank,
    long loanCount
) {
}
