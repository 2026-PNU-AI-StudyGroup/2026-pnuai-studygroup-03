package com.wakebook.external.trend;

import java.time.LocalDateTime;
import java.util.List;

public record TrendItem(
    String sourceKey,
    String keyword,
    String trafficLabel,
    LocalDateTime startedAt,
    String sourceUrl,
    List<NewsEvidence> evidence,
    int sourceRank
) {}
