package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookSearchApiResponse(@JsonProperty("response") Response response) {

    /** 한도 초과 응답에는 numFound가 없다. primitive로 두면 errCode 검사 전에 파싱이 터진다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        @JsonProperty("numFound") Long numFound,
        @JsonProperty("docs") List<DocWrapper> docs,
        @JsonProperty("errCode") String errCode,
        @JsonProperty("error") String error
    ) {
        public long totalCount() {
            return numFound == null ? 0L : numFound;
        }
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
