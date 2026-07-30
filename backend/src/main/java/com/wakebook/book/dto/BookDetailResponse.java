package com.wakebook.book.dto;

import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.LibraryHolding;

import java.util.List;

public record BookDetailResponse(
    String isbn,
    String title,
    String author,
    String publisher,
    Integer publishedYear,
    String cover,
    String description,
    List<String> tableOfContents,
    String availability,
    List<LibraryResponse> libraries
) {

    public record LibraryResponse(String name, String callNumber, boolean available) {

        public static LibraryResponse from(LibraryHolding holding) {
            return new LibraryResponse(holding.name(), holding.callNumber(), holding.available());
        }
    }

    public static BookDetailResponse of(BookDetail detail, List<LibraryHolding> holdings, List<String> tableOfContents) {
        List<LibraryResponse> libraries = holdings.stream()
            .map(LibraryResponse::from)
            .toList();
        String availability = libraries.isEmpty()
            ? "UNKNOWN"
            : libraries.stream().anyMatch(LibraryResponse::available) ? "AVAILABLE" : "UNAVAILABLE";

        return new BookDetailResponse(
            detail.isbn(),
            detail.title(),
            detail.author(),
            detail.publisher(),
            detail.publishedYear(),
            detail.cover(),
            detail.description(),
            tableOfContents,
            availability,
            libraries
        );
    }
}
