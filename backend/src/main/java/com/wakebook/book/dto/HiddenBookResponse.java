package com.wakebook.book.dto;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookSource;

import java.util.List;

/**
 * @param callNumber 청구기호. 발견한 책을 실제로 서가에서 찾을 수 있게 함께 내려 준다.
 * @param source 후보군 산출 근거(CSV 실제 대출건수 / API 순위 밖 / 데모 시드).
 */
public record HiddenBookResponse(
    String isbn,
    String title,
    String author,
    String cover,
    String reason,
    String description,
    String libraryName,
    String callNumber,
    String shelfName,
    String kdcCode,
    HiddenBookSource source,
    List<String> keywords
) {

    public static HiddenBookResponse from(HiddenBook hiddenBook) {
        return new HiddenBookResponse(
            hiddenBook.getIsbn(), hiddenBook.getTitle(), hiddenBook.getAuthor(), hiddenBook.getCover(),
            hiddenBook.getReason(), hiddenBook.getDescription(), hiddenBook.getLibraryName(),
            hiddenBook.getCallNumber(), hiddenBook.getShelfName(), hiddenBook.getKdcCode(), hiddenBook.getSource(),
            hiddenBook.getKeywords()
        );
    }
}
