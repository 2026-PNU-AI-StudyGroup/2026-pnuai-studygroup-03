package com.wakebook.recommendation.service;

import tools.jackson.databind.ObjectMapper;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.recommendation.dto.CompareRequest;
import com.wakebook.recommendation.dto.CompareResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BookComparisonService {

    private static final String SYSTEM_PROMPT = """
        당신은 도서관 사서입니다. 인기 도서와 잠자는 도서 두 권의 제목/설명을 비교해
        공통 키워드, 두 책의 차이점, 각 책의 난이도(difficulty: 쉬움/보통/어려움)와 문체(style)를 설명합니다.
        반드시 다음 JSON 형식으로만 답하세요:
        {"commonKeywords": ["...", "..."], "difference": "...",
         "popularBookProfile": {"difficulty": "...", "style": "..."},
         "hiddenBookProfile": {"difficulty": "...", "style": "..."}}
        """;

    private final BookDetailProvider bookDetailProvider;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public BookComparisonService(BookDetailProvider bookDetailProvider, OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.bookDetailProvider = bookDetailProvider;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public CompareResponse compare(CompareRequest request) {
        BookDetail popularBook = fetchDetail(request.popularBook());
        BookDetail hiddenBook = fetchDetail(request.hiddenBook());

        String userPrompt = """
            인기 도서 - 제목: %s, 설명: %s
            잠자는 도서 - 제목: %s, 설명: %s
            """.formatted(popularBook.title(), popularBook.description(), hiddenBook.title(), hiddenBook.description());

        String content = openAiClient.complete(SYSTEM_PROMPT, userPrompt);
        try {
            return objectMapper.readValue(content, CompareResponse.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 추천 생성에 실패했습니다.");
        }
    }

    private BookDetail fetchDetail(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "isbn은 필수입니다.");
        }
        return bookDetailProvider.fetch(isbn.trim())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "도서를 찾을 수 없습니다."));
    }
}
