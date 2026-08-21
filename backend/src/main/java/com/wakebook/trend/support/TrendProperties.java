package com.wakebook.trend.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trend")
public record TrendProperties(
    int candidateLimit,
    int topTrendCount,
    int booksPerTrend,
    double minimumTopicConfidence,
    double minimumEvidenceConsistency,
    double minimumBookMatchScore,
    int libraryTrendCandidateCount,
    int fallbackDays,
    int forceRefreshCooldownMinutes
) {}
