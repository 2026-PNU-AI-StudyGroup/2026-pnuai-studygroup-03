package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookDetailApiResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(@JsonProperty("detail") List<Detail> detail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(@JsonProperty("book") Book book) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Book(
        @JsonProperty("bookname") String bookname,
        @JsonProperty("authors") String authors,
        @JsonProperty("publisher") String publisher,
        @JsonProperty("publication_year") String publicationYear,
        @JsonProperty("isbn13") String isbn13,
        @JsonProperty("description") String description,
        @JsonProperty("bookImageURL") String bookImageUrl
    ) {
    }
}
