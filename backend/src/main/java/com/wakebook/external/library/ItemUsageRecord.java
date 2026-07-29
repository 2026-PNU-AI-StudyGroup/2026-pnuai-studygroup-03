package com.wakebook.external.library;

public record ItemUsageRecord(
    String isbn,
    String title,
    String author,
    long loanCount
) {
}
