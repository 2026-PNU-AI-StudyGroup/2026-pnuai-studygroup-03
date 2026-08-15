CREATE TABLE book_enrichment_cache (
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(500) NULL,
    cover VARCHAR(2048) NULL,
    quality_score INT NOT NULL DEFAULT 0,
    reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_book_enrichment_cache PRIMARY KEY (isbn)
);

CREATE TABLE book_enrichment_cache_keywords (
    isbn VARCHAR(20) NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    CONSTRAINT fk_book_enrichment_cache_keywords_isbn
        FOREIGN KEY (isbn) REFERENCES book_enrichment_cache (isbn) ON DELETE CASCADE
);

CREATE INDEX idx_book_enrichment_cache_keywords_isbn ON book_enrichment_cache_keywords (isbn);
