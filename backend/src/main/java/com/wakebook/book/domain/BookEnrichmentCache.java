package com.wakebook.book.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ISBN 단위로 상세조회(정보나루)·AI 생성(reason/keywords) 결과를 재사용하기 위한 캐시.
 * 여러 도서관이 같은 책을 후보군으로 올릴 때 외부 API 호출을 반복하지 않기 위함이며,
 * {@code hidden_books}처럼 도서관 코드별로 지워지지 않고 ISBN 기준으로 계속 누적된다.
 */
@Entity
@Table(name = "book_enrichment_cache")
public class BookEnrichmentCache {

    @Id
    @Column(length = 20)
    private String isbn;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String author;

    @Column(length = 2048)
    private String cover;

    @Column(name = "quality_score", nullable = false)
    private int qualityScore;

    @Column(length = 1000)
    private String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "book_enrichment_cache_keywords", joinColumns = @JoinColumn(name = "isbn"))
    @Column(name = "keyword", nullable = false, length = 100)
    private List<String> keywords = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BookEnrichmentCache() {
    }

    public BookEnrichmentCache(
        String isbn,
        String title,
        String author,
        String cover,
        int qualityScore,
        String reason,
        List<String> keywords
    ) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.cover = cover;
        this.qualityScore = qualityScore;
        this.reason = reason;
        this.keywords = new ArrayList<>(keywords);
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getCover() {
        return cover;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }
}
