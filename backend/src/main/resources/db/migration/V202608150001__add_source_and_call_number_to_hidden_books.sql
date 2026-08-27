-- 후보군을 CSV 업로드 외에 정보나루 API(장서 목록 - 대출 순위)로도 만들 수 있게 한다.
-- API 경로는 대출건수를 알 수 없으므로 loan_count는 0으로 두고, source로 산출 근거를 구분한다.
ALTER TABLE hidden_books ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'CSV_UPLOAD';
ALTER TABLE hidden_books ALTER COLUMN source DROP DEFAULT;

-- 발견한 책을 실제로 빌릴 수 있도록 청구기호와 자료실을 함께 저장한다.
ALTER TABLE hidden_books ADD COLUMN call_number VARCHAR(100) NULL;
ALTER TABLE hidden_books ADD COLUMN shelf_name VARCHAR(200) NULL;

-- 정보나루 도서 소개글. 후보 도서마다 AI로 키워드를 미리 뽑는 대신 이 원문을 추천 프롬프트에 넣는다.
ALTER TABLE hidden_books ADD COLUMN description VARCHAR(2000) NULL;
