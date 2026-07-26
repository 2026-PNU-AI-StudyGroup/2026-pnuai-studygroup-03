package com.wakebook.book.dto;

import com.wakebook.external.library.BookSearchItem;

public record BookSearchResponse(String isbn, String title, String author, String cover) {

    public static BookSearchResponse from(BookSearchItem item) {
        return new BookSearchResponse(item.isbn(), item.title(), item.author(), item.cover());
    }
}
