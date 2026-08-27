package com.wakebook.curation.dto;

import com.wakebook.curation.domain.Curation;
import java.time.LocalDateTime;

public record PublicCurationSummaryResponse(
    Long id, String title, String description, int bookCount, String cover, LocalDateTime createdAt
) {
    public static PublicCurationSummaryResponse from(Curation curation) {
        String cover = curation.getBooks().stream()
            .findFirst()
            .map(curationBook -> curationBook.getBook().getCover())
            .orElse(null);
        return new PublicCurationSummaryResponse(
            curation.getId(), curation.getTitle(), curation.getDescription(),
            curation.getBooks().size(), cover, curation.getCreatedAt()
        );
    }
}
