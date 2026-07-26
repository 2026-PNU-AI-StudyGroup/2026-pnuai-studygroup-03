package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookSearchApiResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        @JsonProperty("numFound") long numFound,
        @JsonProperty("docs") List<DocWrapper> docs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocWrapper(@JsonProperty("doc") Doc doc) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Doc(
        @JsonProperty("bookname") String bookname,
        @JsonProperty("authors") String authors,
        @JsonProperty("publisher") String publisher,
        @JsonProperty("isbn13") String isbn13,
        @JsonProperty("bookImageURL") String bookImageUrl
    ) {
    }
}
