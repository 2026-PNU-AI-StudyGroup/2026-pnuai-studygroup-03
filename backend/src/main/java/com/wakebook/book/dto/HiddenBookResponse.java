package com.wakebook.book.dto;

import com.wakebook.book.domain.HiddenBook;

import java.util.List;

public record HiddenBookResponse(
    String isbn,
    String title,
    String cover,
    String reason,
    List<String> keywords
) {

    public static HiddenBookResponse from(HiddenBook hiddenBook) {
        return new HiddenBookResponse(
            hiddenBook.getIsbn(), hiddenBook.getTitle(), hiddenBook.getCover(),
            hiddenBook.getReason(), hiddenBook.getKeywords()
        );
    }
}
