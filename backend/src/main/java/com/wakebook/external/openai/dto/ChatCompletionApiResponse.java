package com.wakebook.external.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionApiResponse(@JsonProperty("choices") List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(@JsonProperty("message") Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("content") String content) {
    }
}
