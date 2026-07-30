package com.wakebook.external.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatCompletionApiRequest(
    String model,
    List<Message> messages,
    @JsonProperty("response_format") ResponseFormat responseFormat,
    Double temperature
) {

    public record Message(String role, String content) {
    }

    public record ResponseFormat(String type) {
    }

    /**
     * 추천 점수/채점 결과가 호출할 때마다 흔들리지 않도록 temperature를 0으로 고정한다.
     */
    public static ChatCompletionApiRequest jsonMode(String model, String systemPrompt, String userPrompt) {
        return new ChatCompletionApiRequest(
            model,
            List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
            new ResponseFormat("json_object"),
            0.0
        );
    }
}
