package com.wakebook.external.library;

public record ItemUsageRecord(
    String isbn,
    String title,
    String author,
    String publisher,
    Integer publishedYear,
    String kdcCode,
    long loanCount
) {
    public static final String UNKNOWN_KDC_CATEGORY = "UNKNOWN";

    /**
     * 주제분류번호(KDC)의 대분류(첫 자리, 0~9)를 반환한다. 값이 비어있거나
     * 숫자로 시작하지 않으면 {@link #UNKNOWN_KDC_CATEGORY}로 묶는다.
     */
    public String kdcCategory() {
        if (kdcCode == null || kdcCode.isBlank()) {
            return UNKNOWN_KDC_CATEGORY;
        }
        char first = kdcCode.trim().charAt(0);
        return Character.isDigit(first) ? String.valueOf(first) : UNKNOWN_KDC_CATEGORY;
    }
}
