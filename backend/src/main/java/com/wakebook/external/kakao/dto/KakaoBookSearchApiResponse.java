package com.wakebook.external.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoBookSearchApiResponse(@JsonProperty("documents") List<Document> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
        @JsonProperty("title") String title,
        @JsonProperty("contents") String contents,
        @JsonProperty("authors") List<String> authors,
        @JsonProperty("publisher") String publisher,
        @JsonProperty("isbn") String isbn,
        @JsonProperty("datetime") String datetime,
        @JsonProperty("thumbnail") String thumbnail
    ) {
    }
}
