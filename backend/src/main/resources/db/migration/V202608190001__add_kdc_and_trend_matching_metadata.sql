ALTER TABLE hidden_books ADD COLUMN kdc_code VARCHAR(10) NULL;

UPDATE hidden_books
SET kdc_code = CASE
    WHEN TRIM(call_number) LIKE '0%' THEN '0'
    WHEN TRIM(call_number) LIKE '1%' THEN '1'
    WHEN TRIM(call_number) LIKE '2%' THEN '2'
    WHEN TRIM(call_number) LIKE '3%' THEN '3'
    WHEN TRIM(call_number) LIKE '4%' THEN '4'
    WHEN TRIM(call_number) LIKE '5%' THEN '5'
    WHEN TRIM(call_number) LIKE '6%' THEN '6'
    WHEN TRIM(call_number) LIKE '7%' THEN '7'
    WHEN TRIM(call_number) LIKE '8%' THEN '8'
    WHEN TRIM(call_number) LIKE '9%' THEN '9'
    ELSE NULL
END
WHERE kdc_code IS NULL AND call_number IS NOT NULL;

ALTER TABLE daily_trends ADD COLUMN retrieval_intent VARCHAR(500) NULL;
ALTER TABLE daily_trends ADD COLUMN required_concepts JSON NULL;

ALTER TABLE daily_trend_recommendations ADD COLUMN match_score DECIMAL(4,3) NOT NULL DEFAULT 0;
ALTER TABLE daily_trend_recommendations ALTER COLUMN match_score DROP DEFAULT;

-- batch_id를 선두로 하는 새 유니크 인덱스를 먼저 만들어야, fk_daily_trend_recommendations_batch가
-- 옛 인덱스(uk_daily_trend_recommendations_book) 없이도 계속 지원돼서 DROP INDEX가 성공한다.
-- 순서를 반대로 하면 "Cannot drop index ...: needed in a foreign key constraint" 에러가 난다.
ALTER TABLE daily_trend_recommendations
    ADD CONSTRAINT uk_daily_trend_recommendations_batch_isbn UNIQUE (batch_id, isbn);
ALTER TABLE daily_trend_recommendations DROP INDEX uk_daily_trend_recommendations_book;
