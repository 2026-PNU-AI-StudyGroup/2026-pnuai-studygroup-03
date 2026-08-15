package com.wakebook.recommendation.dto;

import java.util.List;

public record RecommendationResponse(
    String isbn,
    String title,
    String author,
    String cover,
    int score,
    int keywordRelevance,
    int purposeMatch,
    int moodMatch,
    int discoveryValue,
    String reason,
    List<String> keywords,
    String libraryName,
    String callNumber,
    String shelfName
) {
}
