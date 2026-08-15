package com.wakebook.recommendation.service;

import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wakebook.common.ApiException;
import com.wakebook.common.config.CacheConfig;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.recommendation.dto.KeywordsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeywordGenerationService {

    private static final String SYSTEM_PROMPT = """
        당신은 도서관 사서입니다. 주어진 책의 제목과 설명을 바탕으로 핵심 키워드 5개를 뽑습니다.
        반드시 다음 JSON 형식으로만 답하세요:
        {"keywords": ["키워드1", "키워드2", "키워드3", "키워드4", "키워드5"]}
        """;

    private final BookDetailProvider bookDetailProvider;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public KeywordGenerationService(BookDetailProvider bookDetailProvider, OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.bookDetailProvider = bookDetailProvider;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = CacheConfig.AI_KEYWORDS, key = "#isbn", unless = "#result == null")
    public KeywordsResponse generateKeywords(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "isbn은 필수입니다.");
        }

        BookDetail detail = bookDetailProvider.fetch(isbn.trim())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "도서를 찾을 수 없습니다."));

        String userPrompt = "제목: %s\n설명: %s".formatted(detail.title(), detail.description());
        String content = openAiClient.complete(SYSTEM_PROMPT, userPrompt);

        try {
            AiKeywordsPayload payload = objectMapper.readValue(content, AiKeywordsPayload.class);
            return new KeywordsResponse(payload.keywords());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 추천 생성에 실패했습니다.");
        }
    }

    private record AiKeywordsPayload(@JsonProperty("keywords") List<String> keywords) {
    }
}
