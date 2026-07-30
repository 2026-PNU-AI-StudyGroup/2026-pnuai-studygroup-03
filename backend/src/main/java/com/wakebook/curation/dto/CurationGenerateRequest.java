package com.wakebook.curation.dto;

import java.util.List;

public record CurationGenerateRequest(
        String topic,
        String targetAge,
        String mood,
        String category,
        Integer bookCount,
        List<String> excludedKeywords,
        String purpose
) {
}
