package com.wakebook.external.openai;

import com.wakebook.common.ApiException;
import com.wakebook.external.openai.dto.ChatCompletionApiRequest;
import com.wakebook.external.openai.dto.ChatCompletionApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiChatClient implements OpenAiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiChatClient(OpenAiProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.apiKey = properties.apiKey();
        this.model = properties.model();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        ChatCompletionApiRequest request = ChatCompletionApiRequest.jsonMode(model, systemPrompt, userPrompt);

        ChatCompletionApiResponse response;
        try {
            response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(ChatCompletionApiResponse.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 추천 생성에 실패했습니다.");
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
            || response.choices().get(0).message() == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 추천 생성에 실패했습니다.");
        }

        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 추천 생성에 실패했습니다.");
        }
        return content;
    }
}
