package com.wakebook.curation.dto;

import com.wakebook.curation.domain.CurationBook;

public record CurationBookResponse(Long id, String isbn, String title, String cover, int displayOrder, String comment) {

    public static CurationBookResponse from(CurationBook curationBook) {
        return new CurationBookResponse(
                curationBook.getId(),
                curationBook.getBook().getIsbn(),
                curationBook.getBook().getTitle(),
                curationBook.getBook().getCover(),
                curationBook.getDisplayOrder(),
                curationBook.getComment()
        );
    }
}
