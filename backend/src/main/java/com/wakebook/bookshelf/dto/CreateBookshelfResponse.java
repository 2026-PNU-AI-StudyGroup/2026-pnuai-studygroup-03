package com.wakebook.bookshelf.dto;

import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.BookshelfType;

public record CreateBookshelfResponse(
        Long id,
        String name,
        String description,
        BookshelfType type
) {

    public static CreateBookshelfResponse from(Bookshelf bookshelf) {
        return new CreateBookshelfResponse(
                bookshelf.getId(),
                bookshelf.getName(),
                bookshelf.getDescription(),
                bookshelf.getType()
        );
    }
}
