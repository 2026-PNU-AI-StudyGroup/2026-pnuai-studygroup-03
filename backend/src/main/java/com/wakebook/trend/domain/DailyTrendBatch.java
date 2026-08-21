package com.wakebook.trend.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_trend_batches", uniqueConstraints = @UniqueConstraint(
    name = "uk_daily_trend_batches_date_library", columnNames = {"recommendation_date", "library_code"}))
public class DailyTrendBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "recommendation_date", nullable = false) private LocalDate recommendationDate;
    @Column(name = "library_code", nullable = false, length = 20) private String libraryCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TrendBatchStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "created_count", nullable = false) private int createdCount;
    @Column(name = "error_code", length = 50) private String errorCode;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected DailyTrendBatch() {}
    public DailyTrendBatch(LocalDate recommendationDate, String libraryCode) {
        this.recommendationDate = recommendationDate;
        this.libraryCode = libraryCode;
        this.status = TrendBatchStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
    public void queueAgain() { status = TrendBatchStatus.PENDING; errorCode = null; updatedAt = LocalDateTime.now(); }
    public void start() { status = TrendBatchStatus.PROCESSING; attemptCount++; startedAt = LocalDateTime.now(); errorCode = null; updatedAt = startedAt; }
    public void complete(int count) { status = TrendBatchStatus.COMPLETED; createdCount = count; completedAt = LocalDateTime.now(); updatedAt = completedAt; }
    public void fail(String code) { status = TrendBatchStatus.FAILED; errorCode = code; updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public LocalDate getRecommendationDate() { return recommendationDate; }
    public String getLibraryCode() { return libraryCode; }
    public TrendBatchStatus getStatus() { return status; }
    public int getCreatedCount() { return createdCount; }
    public String getErrorCode() { return errorCode; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
