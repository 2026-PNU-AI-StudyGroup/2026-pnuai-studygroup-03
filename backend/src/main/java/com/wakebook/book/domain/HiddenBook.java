package com.wakebook.book.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "hidden_books", uniqueConstraints = @UniqueConstraint(columnNames = {"library_code", "isbn"}))
public class HiddenBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String isbn;

    @Column(name = "library_code", nullable = false, length = 20)
    private String libraryCode;

    @Column(name = "library_name", length = 200)
    private String libraryName;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String author;

    @Column(length = 2048)
    private String cover;

    @Column(name = "loan_count", nullable = false)
    private long loanCount;

    @Column(name = "quality_score", nullable = false)
    private int qualityScore;

    @Column(length = 1000)
    private String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hidden_book_keywords", joinColumns = @JoinColumn(name = "hidden_book_id"))
    @Column(name = "keyword", nullable = false, length = 100)
    private List<String> keywords = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HiddenBook() {
    }

    public HiddenBook(
        String isbn,
        String libraryCode,
        String libraryName,
        String title,
        String author,
        String cover,
        long loanCount,
        int qualityScore,
        String reason,
        List<String> keywords
    ) {
        this.isbn = isbn;
        this.libraryCode = libraryCode;
        this.libraryName = libraryName;
        this.title = title;
        this.author = author;
        this.cover = cover;
        this.loanCount = loanCount;
        this.qualityScore = qualityScore;
        this.reason = reason;
        this.keywords = new ArrayList<>(keywords);
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getLibraryCode() {
        return libraryCode;
    }

    public String getLibraryName() {
        return libraryName;
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

    public long getLoanCount() {
        return loanCount;
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
