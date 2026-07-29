package com.wakebook.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CompareResponse(
    List<String> commonKeywords,
    String difference,
    BookProfile popularBookProfile,
    BookProfile hiddenBookProfile
) {

    public record BookProfile(
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("style") String style
    ) {
    }
}
