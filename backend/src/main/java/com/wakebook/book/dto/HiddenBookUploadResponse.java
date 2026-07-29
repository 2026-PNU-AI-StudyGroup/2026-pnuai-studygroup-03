package com.wakebook.book.dto;

public record HiddenBookUploadResponse(
    String libraryCode,
    String libraryName,
    int totalRows,
    int savedCount
) {
}
