package com.wakebook.trend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_trend_recommendations", uniqueConstraints = @UniqueConstraint(
    name = "uk_daily_trend_recommendations_book", columnNames = {"batch_id", "daily_trend_id", "isbn"}))
public class DailyTrendRecommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "batch_id") private DailyTrendBatch batch;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "daily_trend_id") private DailyTrend dailyTrend;
    @Column(name = "library_code", nullable = false, length = 20) private String libraryCode;
    @Column(nullable = false, length = 20) private String isbn;
    @Column(name = "book_title", nullable = false, length = 500) private String bookTitle;
    @Column(name = "book_author", length = 500) private String bookAuthor;
    @Column(name = "book_cover", length = 2048) private String bookCover;
    @Column(name = "loan_count", nullable = false) private long loanCount;
    @Column(name = "recommendation_title", nullable = false, length = 200) private String recommendationTitle;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "match_score", nullable = false, precision = 4, scale = 3) private BigDecimal matchScore;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected DailyTrendRecommendation() {}
    public DailyTrendRecommendation(DailyTrendBatch batch, DailyTrend dailyTrend, String libraryCode,
                                    String isbn, String bookTitle, String bookAuthor, String bookCover,
                                    long loanCount, String recommendationTitle, String reason, int displayOrder,
                                    double matchScore) {
        this.batch = batch; this.dailyTrend = dailyTrend; this.libraryCode = libraryCode;
        this.isbn = isbn; this.bookTitle = bookTitle; this.bookAuthor = bookAuthor;
        this.bookCover = bookCover; this.loanCount = loanCount;
        this.recommendationTitle = recommendationTitle; this.reason = reason;
        this.matchScore = BigDecimal.valueOf(matchScore);
        this.displayOrder = displayOrder; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public DailyTrendBatch getBatch() { return batch; }
    public DailyTrend getDailyTrend() { return dailyTrend; }
    public String getIsbn() { return isbn; }
    public String getBookTitle() { return bookTitle; }
    public String getBookAuthor() { return bookAuthor; }
    public String getBookCover() { return bookCover; }
    public long getLoanCount() { return loanCount; }
    public String getRecommendationTitle() { return recommendationTitle; }
    public String getReason() { return reason; }
    public double getMatchScore() { return matchScore.doubleValue(); }
    public int getDisplayOrder() { return displayOrder; }
}
