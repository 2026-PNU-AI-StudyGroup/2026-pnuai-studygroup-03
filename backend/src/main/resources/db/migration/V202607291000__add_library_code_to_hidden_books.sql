ALTER TABLE hidden_books DROP INDEX uk_hidden_books_isbn;
ALTER TABLE hidden_books ADD COLUMN library_code VARCHAR(20) NOT NULL DEFAULT '' AFTER isbn;
ALTER TABLE hidden_books ADD COLUMN library_name VARCHAR(200) NULL AFTER library_code;
ALTER TABLE hidden_books ALTER COLUMN library_code DROP DEFAULT;
ALTER TABLE hidden_books ADD CONSTRAINT uk_hidden_books_library_isbn UNIQUE (library_code, isbn);
