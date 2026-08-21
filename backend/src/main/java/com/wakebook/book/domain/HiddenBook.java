package com.wakebook.book.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "kdc_code", length = 10)
    private String kdcCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HiddenBookSource source;

    @Column(name = "call_number", length = 100)
    private String callNumber;

    @Column(name = "shelf_name", length = 200)
    private String shelfName;

    @Column(length = 2000)
    private String description;

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

    /** 사서 CSV 업로드로 만든 후보. 실제 대출건수를 알고 있다. */
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
        this(isbn, libraryCode, libraryName, title, author, cover, loanCount, qualityScore,
            reason, keywords, HiddenBookSource.CSV_UPLOAD, null, null, null, null);
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
        List<String> keywords,
        HiddenBookSource source,
        String callNumber,
        String shelfName,
        String description
    ) {
        this(isbn, libraryCode, libraryName, title, author, cover, loanCount, qualityScore,
            reason, keywords, source, callNumber, shelfName, description, null);
    }

    public HiddenBook(
        String isbn, String libraryCode, String libraryName, String title, String author, String cover,
        long loanCount, int qualityScore, String reason, List<String> keywords, HiddenBookSource source,
        String callNumber, String shelfName, String description, String kdcCode
    ) {
        this.isbn = isbn;
        this.libraryCode = libraryCode;
        this.libraryName = libraryName;
        this.title = title;
        this.author = author;
        this.cover = cover;
        this.loanCount = loanCount;
        this.qualityScore = qualityScore;
        this.kdcCode = kdcCode;
        this.reason = reason;
        this.keywords = new ArrayList<>(keywords);
        this.source = source;
        this.callNumber = callNumber;
        this.shelfName = shelfName;
        this.description = description;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 추천 이유는 후보군을 만들 때가 아니라 처음 화면에 필요할 때 생성한다.
     * 후보 도서 수만큼 AI를 미리 호출하면 산출이 몇 배로 느려지고 대부분은 노출되지도 않는다.
     */
    public void applyGeneratedReason(String generatedReason, List<String> generatedKeywords) {
        this.reason = generatedReason;
        if (generatedKeywords != null && !generatedKeywords.isEmpty()) {
            this.keywords = new ArrayList<>(generatedKeywords);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasReason() {
        return reason != null && !reason.isBlank();
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

    public String getKdcCode() { return kdcCode; }

    public String getReason() {
        return reason;
    }

    public HiddenBookSource getSource() {
        return source;
    }

    public String getCallNumber() {
        return callNumber;
    }

    public String getShelfName() {
        return shelfName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }
}
