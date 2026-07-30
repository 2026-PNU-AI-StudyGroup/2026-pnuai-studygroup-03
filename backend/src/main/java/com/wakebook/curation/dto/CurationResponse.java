package com.wakebook.curation.dto;

import com.wakebook.curation.domain.Curation;

import java.time.LocalDateTime;
import java.util.List;

public record CurationResponse(
        Long id,
        String title,
        String description,
        boolean isPublic,
        int bookCount,
        List<CurationBookResponse> books,
        LocalDateTime createdAt
) {
    public static CurationResponse from(Curation curation) {
        List<CurationBookResponse> books = curation.getBooks().stream()
                .map(CurationBookResponse::from)
                .toList();
        return new CurationResponse(
                curation.getId(),
                curation.getTitle(),
                curation.getDescription(),
                curation.isPublic(),
                books.size(),
                books,
                curation.getCreatedAt()
        );
    }
}
