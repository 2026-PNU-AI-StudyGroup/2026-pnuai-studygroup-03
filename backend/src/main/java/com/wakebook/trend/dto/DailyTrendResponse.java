package com.wakebook.trend.dto;

import com.wakebook.trend.domain.TrendValidationStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyTrendResponse(
    LocalDate requestedDate,
    LocalDate recommendationDate,
    String libraryCode,
    String libraryName,
    String freshness,
    OffsetDateTime generatedAt,
    List<Source> sources,
    List<Item> items
) {
    public record Source(String type, String name, String role, String region, OffsetDateTime fetchedAt, String url) {}
    public record Item(Long trendId, String sourceKeyword, String displayTopic, int finalRank,
        String trafficLabel, OffsetDateTime startedAt, double topicConfidence,
        TrendValidationStatus validationStatus, String contextDescription,
        String recommendationTitle, List<Book> books) {}
    public record Book(Long recommendationId, String isbn, String title, String author, String cover,
        long loanCount, String reason) {}
}
