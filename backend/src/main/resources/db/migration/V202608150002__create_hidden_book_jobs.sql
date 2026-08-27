-- 후보군 산출은 후보 도서마다 외부 API를 호출하기 때문에 수 분이 걸린다.
-- HTTP 요청 안에서 끝내지 않고 작업으로 접수한 뒤 진행 상태를 조회하게 한다.
CREATE TABLE hidden_book_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    library_code VARCHAR(20) NOT NULL,
    library_name VARCHAR(200) NULL,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_candidates INT NOT NULL DEFAULT 0,
    processed_count INT NOT NULL DEFAULT 0,
    saved_count INT NOT NULL DEFAULT 0,
    message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_hidden_book_jobs PRIMARY KEY (id)
);

CREATE INDEX idx_hidden_book_jobs_library_code ON hidden_book_jobs (library_code, created_at);
