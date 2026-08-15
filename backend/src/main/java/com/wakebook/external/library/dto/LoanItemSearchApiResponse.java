package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanItemSearchApiResponse(@JsonProperty("response") Response response) {

    /**
     * 한도 초과 응답은 `{"response":{"errCode":"outOflimit","error":"…"}}`뿐이라 건수 필드가 없다.
     * primitive로 두면 여기서 파싱이 먼저 터져 errCode 검사(Data4LibraryErrors)에 닿지 못한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        @JsonProperty("resultNum") Long resultNum,
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
