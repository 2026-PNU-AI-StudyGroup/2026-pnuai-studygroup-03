package com.wakebook.bookshelf.dto;

import com.wakebook.book.domain.Book;
import com.wakebook.bookshelf.domain.BookshelfBook;
import com.wakebook.bookshelf.domain.ReadingStatus;

public record BookshelfBookResponse(
        Long id,
        String isbn,
        String title,
        ReadingStatus status,
        String cover
) {

    public static BookshelfBookResponse from(BookshelfBook bookshelfBook) {
        Book book = bookshelfBook.getBook();
        return new BookshelfBookResponse(
                bookshelfBook.getId(),
                book.getIsbn(),
                book.getTitle(),
                bookshelfBook.getStatus(),
                book.getCover()
        );
    }
}
