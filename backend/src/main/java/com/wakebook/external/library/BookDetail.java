package com.wakebook.external.library;

public record BookDetail(
    String isbn,
    String title,
    String author,
    String publisher,
    Integer publishedYear,
    String cover,
    String description
) {
}
