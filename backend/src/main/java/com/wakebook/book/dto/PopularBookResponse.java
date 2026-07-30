package com.wakebook.book.dto;

import com.wakebook.external.library.PopularLoanBookItem;

public record PopularBookResponse(
    String isbn,
    String title,
    String author,
    String cover,
    int rank,
    long loanCount
) {

    public static PopularBookResponse from(PopularLoanBookItem item) {
        return new PopularBookResponse(
            item.isbn(), item.title(), item.author(), item.cover(), item.rank(), item.loanCount()
        );
    }
}
