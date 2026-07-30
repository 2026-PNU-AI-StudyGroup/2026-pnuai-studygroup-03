package com.wakebook.book.support;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.ItemUsageRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 정보나루 "장서 대출목록" CSV(도서관별/월별로 사서가 사이트에서 직접 다운받는 파일)를 파싱한다.
 * 컬럼: 번호,도서명,저자,출판사,발행년도,ISBN,세트 ISBN,부가기호,권,주제분류번호,도서권수,대출건수,등록일자
 * 컬럼 순서가 아니라 헤더 이름으로 읽으므로, 컬럼 순서가 바뀌어도 안전하다.
 */
@Component
public class HiddenBookCsvParser {

    public List<ItemUsageRecord> parse(InputStream inputStream) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .setAllowMissingColumnNames(true)
            .build();

        List<ItemUsageRecord> records = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8), format)) {
            for (CSVRecord record : parser) {
                String isbn = safeGet(record, "ISBN");
                if (isbn == null || isbn.isBlank()) {
                    continue;
                }
                String title = safeGet(record, "도서명");
                String author = safeGet(record, "저자");
                long loanCount = parseLongSafe(safeGet(record, "대출건수"));
                records.add(new ItemUsageRecord(isbn.trim(), title, author, loanCount));
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "CSV 파일을 읽을 수 없습니다.");
        }
        return records;
    }

    private String safeGet(CSVRecord record, String column) {
        return record.isMapped(column) ? record.get(column) : null;
    }

    private long parseLongSafe(String value) {
        try {
            return value == null || value.isBlank() ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
