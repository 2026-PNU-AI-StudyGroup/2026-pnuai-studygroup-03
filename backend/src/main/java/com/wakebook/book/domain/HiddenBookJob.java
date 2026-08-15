package com.wakebook.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 후보군 산출 작업의 진행 상태. 요청 안에서 끝나지 않는 작업이라 별도로 추적한다. */
@Entity
@Table(name = "hidden_book_jobs")
public class HiddenBookJob {

    private static final int MAX_MESSAGE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "library_code", nullable = false, length = 20)
    private String libraryCode;

    /** 요청자. 사용자별 일일 산출 횟수를 세기 위해 기록한다. CSV 업로드는 사서 본인이다. */
    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "library_name", length = 200)
    private String libraryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HiddenBookSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HiddenBookJobStatus status;

    @Column(name = "total_candidates", nullable = false)
    private int totalCandidates;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "saved_count", nullable = false)
    private int savedCount;

    @Column(length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HiddenBookJob() {
    }

    public HiddenBookJob(String libraryCode, String libraryName, HiddenBookSource source, Long requestedBy) {
        this.libraryCode = libraryCode;
        this.requestedBy = requestedBy;
        this.libraryName = libraryName;
        this.source = source;
        this.status = HiddenBookJobStatus.PENDING;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void start(int totalCandidates) {
        this.status = HiddenBookJobStatus.RUNNING;
        this.totalCandidates = totalCandidates;
        touch();
    }

    public void progress(int processedCount, int savedCount) {
        this.processedCount = processedCount;
        this.savedCount = savedCount;
        touch();
    }

    public void succeed(String libraryName, int savedCount, String message) {
        this.libraryName = libraryName;
        this.savedCount = savedCount;
        this.status = HiddenBookJobStatus.SUCCEEDED;
        this.message = truncate(message);
        touch();
    }

    public void fail(String message) {
        this.status = HiddenBookJobStatus.FAILED;
        this.message = truncate(message);
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public String getLibraryCode() {
        return libraryCode;
    }

    public String getLibraryName() {
        return libraryName;
    }

    public HiddenBookSource getSource() {
        return source;
    }

    public HiddenBookJobStatus getStatus() {
        return status;
    }

    public int getTotalCandidates() {
        return totalCandidates;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getSavedCount() {
        return savedCount;
    }

    public String getMessage() {
        return message;
    }
}
