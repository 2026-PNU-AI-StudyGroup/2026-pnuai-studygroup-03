CREATE TABLE books (
    isbn VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    cover VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_books PRIMARY KEY (isbn)
);

CREATE TABLE bookshelves (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_bookshelves PRIMARY KEY (id),
    CONSTRAINT fk_bookshelves_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_bookshelves_type
        CHECK (type IN ('DEFAULT', 'CUSTOM'))
);

CREATE INDEX idx_bookshelves_user_id ON bookshelves (user_id);

CREATE TABLE bookshelf_books (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bookshelf_id BIGINT NOT NULL,
    isbn VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_bookshelf_books PRIMARY KEY (id),
    CONSTRAINT uk_bookshelf_books_shelf_isbn UNIQUE (bookshelf_id, isbn),
    CONSTRAINT fk_bookshelf_books_bookshelf
        FOREIGN KEY (bookshelf_id) REFERENCES bookshelves (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookshelf_books_book
        FOREIGN KEY (isbn) REFERENCES books (isbn),
    CONSTRAINT ck_bookshelf_books_status
        CHECK (status IN ('WISH', 'READING', 'COMPLETED', 'REVISIT'))
);

INSERT INTO bookshelves (
    user_id,
    name,
    description,
    type,
    created_at,
    updated_at
)
SELECT
    id,
    '읽고 싶은 책',
    NULL,
    'DEFAULT',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM users;
