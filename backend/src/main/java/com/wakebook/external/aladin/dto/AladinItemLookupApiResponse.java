package com.wakebook.external.aladin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AladinItemLookupApiResponse(@JsonProperty("item") List<Item> item) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        @JsonProperty("isbn13") String isbn13,
        @JsonProperty("subInfo") SubInfo subInfo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubInfo(@JsonProperty("toc") String toc) {
    }
}
