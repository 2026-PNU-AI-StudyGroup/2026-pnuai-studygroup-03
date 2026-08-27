package com.wakebook.external.library.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LibSrchByBookApiResponse(@JsonProperty("response") Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
        @JsonProperty("libs") List<LibWrapper> libs,
        @JsonProperty("errCode") String errCode,
        @JsonProperty("error") String error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LibWrapper(@JsonProperty("lib") Lib lib) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lib(
        @JsonProperty("libCode") String libCode,
        @JsonProperty("libName") String libName
    ) {
    }
}
