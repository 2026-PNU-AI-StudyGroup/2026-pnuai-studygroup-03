package com.wakebook.trend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "daily_trends", uniqueConstraints = @UniqueConstraint(
    name = "uk_daily_trends_source", columnNames = {"trend_date", "source", "source_key"}))
public class DailyTrend {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "trend_date", nullable = false) private LocalDate trendDate;
    @Column(nullable = false, length = 30) private String source;
    @Column(name = "source_key", nullable = false, length = 255) private String sourceKey;
    @Column(name = "source_keyword", nullable = false, length = 200) private String sourceKeyword;
    @Column(name = "normalized_source_keyword", nullable = false, length = 200) private String normalizedSourceKeyword;
    @Column(name = "display_topic", nullable = false, length = 200) private String displayTopic;
    @Column(name = "topic_confidence", nullable = false, precision = 4, scale = 3) private BigDecimal topicConfidence;
    @Column(name = "google_rank", nullable = false) private int googleRank;
    @Column(name = "google_traffic_label", length = 50) private String googleTrafficLabel;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "source_url", length = 2048) private String sourceUrl;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "google_news_evidence", columnDefinition = "JSON") private String googleNewsEvidence;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "naver_news_evidence", columnDefinition = "JSON") private String naverNewsEvidence;
    @Column(name = "context_description", nullable = false, length = 1000) private String contextDescription;
    @Column(name = "retrieval_intent", length = 500) private String retrievalIntent;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_concepts", columnDefinition = "JSON") private String requiredConcepts;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TrendEligibility eligibility;
    @Column(name = "evidence_consistency_score", nullable = false, precision = 4, scale = 3) private BigDecimal evidenceConsistencyScore;
    @Enumerated(EnumType.STRING) @Column(name = "validation_status", nullable = false, length = 30)
    private TrendValidationStatus validationStatus;
    @Column(name = "naver_spike_score", precision = 8, scale = 4) private BigDecimal naverSpikeScore;
    @Column(name = "final_trend_score", nullable = false, precision = 8, scale = 4) private BigDecimal finalTrendScore;
    @Column(name = "fetched_at", nullable = false) private LocalDateTime fetchedAt;
    @Column(name = "news_enriched_at") private LocalDateTime newsEnrichedAt;
    @Column(name = "validated_at") private LocalDateTime validatedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected DailyTrend() {}

    public DailyTrend(LocalDate trendDate, String source, String sourceKey, String sourceKeyword,
                      String normalizedSourceKeyword, String displayTopic, double topicConfidence,
                      int googleRank, String googleTrafficLabel, LocalDateTime startedAt,
                      String sourceUrl, String googleNewsEvidence, String naverNewsEvidence,
                      String contextDescription, TrendEligibility eligibility,
                      double evidenceConsistencyScore, TrendValidationStatus validationStatus,
                      Double naverSpikeScore, double finalTrendScore, LocalDateTime fetchedAt,
                      LocalDateTime newsEnrichedAt, LocalDateTime validatedAt) {
        this(trendDate, source, sourceKey, sourceKeyword, normalizedSourceKeyword, displayTopic,
            topicConfidence, googleRank, googleTrafficLabel, startedAt, sourceUrl, googleNewsEvidence,
            naverNewsEvidence, contextDescription, displayTopic + ". " + contextDescription, "[]",
            eligibility, evidenceConsistencyScore, validationStatus, naverSpikeScore, finalTrendScore,
            fetchedAt, newsEnrichedAt, validatedAt);
    }

    public DailyTrend(LocalDate trendDate, String source, String sourceKey, String sourceKeyword,
                      String normalizedSourceKeyword, String displayTopic, double topicConfidence,
                      int googleRank, String googleTrafficLabel, LocalDateTime startedAt,
                      String sourceUrl, String googleNewsEvidence, String naverNewsEvidence,
                      String contextDescription, String retrievalIntent, String requiredConcepts,
                      TrendEligibility eligibility, double evidenceConsistencyScore,
                      TrendValidationStatus validationStatus, Double naverSpikeScore,
                      double finalTrendScore, LocalDateTime fetchedAt,
                      LocalDateTime newsEnrichedAt, LocalDateTime validatedAt) {
        this.trendDate = trendDate;
        this.source = source;
        this.sourceKey = sourceKey;
        this.sourceKeyword = sourceKeyword;
        this.normalizedSourceKeyword = normalizedSourceKeyword;
        this.displayTopic = displayTopic;
        this.topicConfidence = BigDecimal.valueOf(topicConfidence);
        this.googleRank = googleRank;
        this.googleTrafficLabel = googleTrafficLabel;
        this.startedAt = startedAt;
        this.sourceUrl = sourceUrl;
        this.googleNewsEvidence = googleNewsEvidence;
        this.naverNewsEvidence = naverNewsEvidence;
        this.contextDescription = contextDescription;
        this.retrievalIntent = retrievalIntent;
        this.requiredConcepts = requiredConcepts;
        this.eligibility = eligibility;
        this.evidenceConsistencyScore = BigDecimal.valueOf(evidenceConsistencyScore);
        this.validationStatus = validationStatus;
        this.naverSpikeScore = naverSpikeScore == null ? null : BigDecimal.valueOf(naverSpikeScore);
        this.finalTrendScore = BigDecimal.valueOf(finalTrendScore);
        this.fetchedAt = fetchedAt;
        this.newsEnrichedAt = newsEnrichedAt;
        this.validatedAt = validatedAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LocalDate getTrendDate() { return trendDate; }
    public String getSource() { return source; }
    public String getSourceKeyword() { return sourceKeyword; }
    public String getDisplayTopic() { return displayTopic; }
    public double getTopicConfidence() { return topicConfidence.doubleValue(); }
    public int getGoogleRank() { return googleRank; }
    public String getGoogleTrafficLabel() { return googleTrafficLabel; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public String getContextDescription() { return contextDescription; }
    public String getRetrievalIntent() { return retrievalIntent; }
    public String getRequiredConcepts() { return requiredConcepts; }
    public TrendEligibility getEligibility() { return eligibility; }
    public TrendValidationStatus getValidationStatus() { return validationStatus; }
    public double getFinalTrendScore() { return finalTrendScore.doubleValue(); }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public LocalDateTime getNewsEnrichedAt() { return newsEnrichedAt; }
    public LocalDateTime getValidatedAt() { return validatedAt; }
}
