package com.wakebook.curation.dto;

import java.util.List;

public record CurationGenerateResponse(
        String title,
        String description,
        List<String> hashtags,
        List<BookItem> books
) {
    public record BookItem(String isbn, String title, String reason) {
    }
}
