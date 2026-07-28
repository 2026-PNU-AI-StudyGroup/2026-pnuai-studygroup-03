package com.wakebook.bookshelf.dto;

import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.BookshelfType;

import java.util.List;

public record BookshelfResponse(
        Long id,
        String name,
        BookshelfType type,
        int bookCount,
        List<BookshelfBookResponse> books
) {

    public static BookshelfResponse from(Bookshelf bookshelf) {
        List<BookshelfBookResponse> books = bookshelf.getBooks().stream()
                .map(BookshelfBookResponse::from)
                .toList();

        return new BookshelfResponse(
                bookshelf.getId(),
                bookshelf.getName(),
                bookshelf.getType(),
                books.size(),
                books
        );
    }
}
