package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookExistApiResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(@JsonProperty("result") Result result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
        @JsonProperty("hasBook") String hasBook,
        @JsonProperty("loanAvailable") String loanAvailable
    ) {
    }
}
