-- Job 행이 전혀 없는 도서관도 포함해, 후보군 산출 요청을 도서관별로 직렬화한다.
CREATE TABLE hidden_book_collection_locks (
    library_code VARCHAR(20) NOT NULL,
    CONSTRAINT pk_hidden_book_collection_locks PRIMARY KEY (library_code)
);
