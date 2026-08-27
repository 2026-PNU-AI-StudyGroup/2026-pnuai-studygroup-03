package com.wakebook.external.aladin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 알라딘 ItemLookUp 응답.
 *
 * subInfo는 `{subTitle, originalTitle, itemPage}`만 오고 `OptResult`(Toc·fulldescription)는
 * 무시된다(2026-08-15 확인, docs/tasks.md). 그래서 서지 정보는 전부 item의 기본 필드에서 읽는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AladinItemLookupApiResponse(
    @JsonProperty("item") List<Item> item,
    @JsonProperty("errorCode") Integer errorCode,
    @JsonProperty("errorMessage") String errorMessage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        @JsonProperty("isbn13") String isbn13,
        @JsonProperty("title") String title,
        @JsonProperty("author") String author,
        @JsonProperty("publisher") String publisher,
        @JsonProperty("pubDate") String pubDate,
        @JsonProperty("cover") String cover,
        @JsonProperty("description") String description,
        @JsonProperty("subInfo") SubInfo subInfo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubInfo(@JsonProperty("toc") String toc) {
    }
}
