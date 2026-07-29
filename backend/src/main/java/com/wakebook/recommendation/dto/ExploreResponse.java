package com.wakebook.recommendation.dto;

import java.util.List;

public record ExploreResponse(
    String isbn,
    String title,
    String author,
    String cover,
    int score,
    int relevance,
    int discoveryValue,
    String reason,
    List<String> keywords
) {
}
