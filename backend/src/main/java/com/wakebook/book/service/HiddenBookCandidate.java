package com.wakebook.book.service;

/**
 * CSV 업로드와 정보나루 API 두 경로에서 나온 후보 도서를 같은 형태로 다루기 위한 값.
 *
 * @param loanCount CSV 경로에서만 실제 대출건수가 채워진다. API 경로는 대출건수를 알 수 없어 0이다.
 */
public record HiddenBookCandidate(
    String isbn,
    String title,
    String author,
    String cover,
    long loanCount,
    String className,
    String callNumber,
    String shelfName
) {

    public static HiddenBookCandidate fromCsv(String isbn, String title, String author, long loanCount) {
        return new HiddenBookCandidate(isbn, title, author, null, loanCount, null, null, null);
    }
}
