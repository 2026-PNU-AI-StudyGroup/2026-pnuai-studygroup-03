package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanItemSearchApiResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        @JsonProperty("resultNum") long resultNum,
        @JsonProperty("numFound") long numFound,
        @JsonProperty("docs") List<DocWrapper> docs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocWrapper(@JsonProperty("doc") Doc doc) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Doc(
        @JsonProperty("ranking") String ranking,
        @JsonProperty("bookname") String bookname,
        @JsonProperty("authors") String authors,
        @JsonProperty("publisher") String publisher,
        @JsonProperty("publication_year") String publicationYear,
        @JsonProperty("isbn13") String isbn13,
        @JsonProperty("addition_symbol") String additionSymbol,
        @JsonProperty("class_no") String classNo,
        @JsonProperty("class_nm") String classNm,
        @JsonProperty("bookImageURL") String bookImageUrl,
        @JsonProperty("bookDtlUrl") String bookDtlUrl,
        @JsonProperty("loan_count") String loanCount
    ) {
    }
}
