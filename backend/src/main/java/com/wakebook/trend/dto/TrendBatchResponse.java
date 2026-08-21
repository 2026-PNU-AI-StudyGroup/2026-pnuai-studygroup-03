package com.wakebook.trend.dto;

import com.wakebook.trend.domain.DailyTrendBatch;
import com.wakebook.trend.domain.TrendBatchStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TrendBatchResponse(Long batchId, LocalDate date, String libraryCode, TrendBatchStatus status,
    int createdCount, LocalDateTime requestedAt, LocalDateTime startedAt, LocalDateTime completedAt, String errorCode) {
    public static TrendBatchResponse from(DailyTrendBatch batch) {
        return new TrendBatchResponse(batch.getId(), batch.getRecommendationDate(), batch.getLibraryCode(),
            batch.getStatus(), batch.getCreatedCount(), batch.getCreatedAt(), batch.getStartedAt(),
            batch.getCompletedAt(), batch.getErrorCode());
    }
}
