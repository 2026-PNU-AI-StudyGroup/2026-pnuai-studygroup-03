CREATE TABLE hidden_books (
    id BIGINT NOT NULL AUTO_INCREMENT,
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(500) NULL,
    cover VARCHAR(2048) NULL,
    loan_count BIGINT NOT NULL DEFAULT 0,
    quality_score INT NOT NULL DEFAULT 0,
    reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_hidden_books PRIMARY KEY (id),
    CONSTRAINT uk_hidden_books_isbn UNIQUE (isbn)
);

CREATE TABLE hidden_book_keywords (
    hidden_book_id BIGINT NOT NULL,
    keyword VARCHAR(100) NOT NULL,
    CONSTRAINT fk_hidden_book_keywords_hidden_book
        FOREIGN KEY (hidden_book_id) REFERENCES hidden_books (id) ON DELETE CASCADE
);

CREATE INDEX idx_hidden_book_keywords_hidden_book_id ON hidden_book_keywords (hidden_book_id);
