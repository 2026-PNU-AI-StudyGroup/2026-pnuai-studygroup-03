CREATE TABLE daily_trends (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trend_date DATE NOT NULL,
    source VARCHAR(30) NOT NULL,
    source_key VARCHAR(255) NOT NULL,
    source_keyword VARCHAR(200) NOT NULL,
    normalized_source_keyword VARCHAR(200) NOT NULL,
    display_topic VARCHAR(200) NOT NULL,
    topic_confidence DECIMAL(4,3) NOT NULL,
    google_rank INT NOT NULL,
    google_traffic_label VARCHAR(50) NULL,
    started_at DATETIME(6) NULL,
    source_url VARCHAR(2048) NULL,
    google_news_evidence JSON NULL,
    naver_news_evidence JSON NULL,
    context_description VARCHAR(1000) NOT NULL,
    eligibility VARCHAR(30) NOT NULL,
    evidence_consistency_score DECIMAL(4,3) NOT NULL,
    validation_status VARCHAR(30) NOT NULL,
    naver_spike_score DECIMAL(8,4) NULL,
    final_trend_score DECIMAL(8,4) NOT NULL,
    fetched_at DATETIME(6) NOT NULL,
    news_enriched_at DATETIME(6) NULL,
    validated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_daily_trends PRIMARY KEY (id),
    CONSTRAINT uk_daily_trends_source UNIQUE (trend_date, source, source_key)
);

CREATE INDEX idx_daily_trends_selection
    ON daily_trends (trend_date, eligibility, final_trend_score);

CREATE TABLE daily_trend_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recommendation_date DATE NOT NULL,
    library_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(50) NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_daily_trend_batches PRIMARY KEY (id),
    CONSTRAINT uk_daily_trend_batches_date_library UNIQUE (recommendation_date, library_code)
);

CREATE INDEX idx_daily_trend_batches_library_date
    ON daily_trend_batches (library_code, recommendation_date);

CREATE TABLE daily_trend_recommendations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    daily_trend_id BIGINT NOT NULL,
    library_code VARCHAR(20) NOT NULL,
    isbn VARCHAR(20) NOT NULL,
    book_title VARCHAR(500) NOT NULL,
    book_author VARCHAR(500) NULL,
    book_cover VARCHAR(2048) NULL,
    loan_count BIGINT NOT NULL,
    recommendation_title VARCHAR(200) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_daily_trend_recommendations PRIMARY KEY (id),
    CONSTRAINT fk_daily_trend_recommendations_batch
        FOREIGN KEY (batch_id) REFERENCES daily_trend_batches (id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_trend_recommendations_trend
        FOREIGN KEY (daily_trend_id) REFERENCES daily_trends (id),
    CONSTRAINT uk_daily_trend_recommendations_book UNIQUE (batch_id, daily_trend_id, isbn)
);

CREATE INDEX idx_daily_trend_recommendations_lookup
    ON daily_trend_recommendations (library_code, batch_id, display_order);
