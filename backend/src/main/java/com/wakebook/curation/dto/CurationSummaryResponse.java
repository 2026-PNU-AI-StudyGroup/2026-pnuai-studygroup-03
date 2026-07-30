package com.wakebook.curation.dto;

import com.wakebook.curation.domain.Curation;

public record CurationSummaryResponse(Long id, String title, int bookCount, boolean isPublic) {

    public static CurationSummaryResponse from(Curation curation) {
        return new CurationSummaryResponse(
                curation.getId(),
                curation.getTitle(),
                curation.getBooks().size(),
                curation.isPublic()
        );
    }
}
