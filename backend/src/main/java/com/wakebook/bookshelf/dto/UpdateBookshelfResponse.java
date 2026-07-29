package com.wakebook.bookshelf.dto;

import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.BookshelfType;

public record UpdateBookshelfResponse(
        Long id,
        String name,
        String description,
        BookshelfType type
) {

    public static UpdateBookshelfResponse from(Bookshelf bookshelf) {
        return new UpdateBookshelfResponse(
                bookshelf.getId(),
                bookshelf.getName(),
                bookshelf.getDescription(),
                bookshelf.getType()
        );
    }
}
