CREATE TABLE curations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_curations PRIMARY KEY (id),
    CONSTRAINT fk_curations_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_curations_user_id ON curations (user_id);

CREATE TABLE curation_books (
    id BIGINT NOT NULL AUTO_INCREMENT,
    curation_id BIGINT NOT NULL,
    isbn VARCHAR(20) NOT NULL,
    display_order INT NOT NULL,
    comment VARCHAR(500) NULL,
    CONSTRAINT pk_curation_books PRIMARY KEY (id),
    CONSTRAINT uk_curation_books_curation_isbn UNIQUE (curation_id, isbn),
    CONSTRAINT fk_curation_books_curation
        FOREIGN KEY (curation_id) REFERENCES curations (id) ON DELETE CASCADE,
    CONSTRAINT fk_curation_books_book
        FOREIGN KEY (isbn) REFERENCES books (isbn)
);
