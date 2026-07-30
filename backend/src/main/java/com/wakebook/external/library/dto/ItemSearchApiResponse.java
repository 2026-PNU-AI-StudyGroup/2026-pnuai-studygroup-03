package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemSearchApiResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(@JsonProperty("docs") List<DocWrapper> docs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocWrapper(@JsonProperty("doc") Doc doc) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Doc(
        @JsonProperty("class_no") String classNo,
        @JsonProperty("callNumbers") List<CallNumberWrapper> callNumbers
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallNumberWrapper(@JsonProperty("callNumber") CallNumber callNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallNumber(@JsonProperty("book_code") String bookCode) {
    }
}
