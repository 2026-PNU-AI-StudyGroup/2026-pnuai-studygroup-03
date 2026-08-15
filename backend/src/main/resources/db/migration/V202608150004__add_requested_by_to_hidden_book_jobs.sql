-- 후보군 산출은 정보나루 호출을 수십 번 쓴다(일일 한도는 IP 미등록 시 500건).
-- 한 사람이 도서관을 계속 눌러 한도를 소진하지 못하도록 요청자를 기록해 일일 횟수를 제한한다.
ALTER TABLE hidden_book_jobs ADD COLUMN requested_by BIGINT NULL;

CREATE INDEX idx_hidden_book_jobs_requested_by ON hidden_book_jobs (requested_by, created_at);
