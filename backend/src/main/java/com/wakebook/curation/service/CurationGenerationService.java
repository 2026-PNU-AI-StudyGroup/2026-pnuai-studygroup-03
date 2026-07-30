package com.wakebook.curation.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.curation.dto.CurationGenerateRequest;
import com.wakebook.curation.dto.CurationGenerateResponse;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CurationGenerationService {

    private static final int DEFAULT_BOOK_COUNT = 5;

    private final UserRepository userRepository;
    private final HiddenBookRepository hiddenBookRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public CurationGenerationService(
            UserRepository userRepository,
            HiddenBookRepository hiddenBookRepository,
            OpenAiClient openAiClient,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.hiddenBookRepository = hiddenBookRepository;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public CurationGenerateResponse generate(String authenticatedUserId, CurationGenerateRequest request) {
        String topic = validateTopic(request.topic());
        User user = requireLibrarian(authenticatedUserId);

        List<HiddenBook> pool = poolFor(user);
        if (pool.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "추천할 후보 도서가 없습니다.");
        }

        List<HiddenBook> candidates = excludeByKeywords(pool, request.excludedKeywords());
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "추천할 후보 도서가 없습니다.");
        }

        int bookCount = clamp(request.bookCount() != null ? request.bookCount() : DEFAULT_BOOK_COUNT, candidates.size());

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(topic, request, bookCount, candidates);
        String content = openAiClient.complete(systemPrompt, userPrompt);
        AiCurationPayload payload = parsePayload(content);

        Map<String, HiddenBook> candidatesByIsbn = candidates.stream()
                .collect(Collectors.toMap(HiddenBook::getIsbn, book -> book, (a, b) -> a));

        List<AiBookPayload> aiBooks = payload.books() != null ? payload.books() : List.of();
        List<CurationGenerateResponse.BookItem> books = aiBooks.stream()
                .filter(item -> item.isbn() != null && candidatesByIsbn.containsKey(item.isbn()))
                .map(item -> {
                    HiddenBook book = candidatesByIsbn.get(item.isbn());
                    return new CurationGenerateResponse.BookItem(book.getIsbn(), book.getTitle(), item.reason());
                })
                .toList();

        if (books.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 큐레이션 생성에 실패했습니다.");
        }

        return new CurationGenerateResponse(payload.title(), payload.description(), payload.hashtags(), books);
    }

    private List<HiddenBook> poolFor(User user) {
        if (user.getLibraryCode() == null) {
            return List.of();
        }
        return hiddenBookRepository.findAllByLibraryCode(user.getLibraryCode());
    }

    private List<HiddenBook> excludeByKeywords(List<HiddenBook> pool, List<String> excludedKeywords) {
        if (excludedKeywords == null || excludedKeywords.isEmpty()) {
            return pool;
        }
        Set<String> excluded = excludedKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::strip)
                .collect(Collectors.toSet());
        if (excluded.isEmpty()) {
            return pool;
        }
        return pool.stream()
                .filter(book -> book.getKeywords().stream().noneMatch(excluded::contains))
                .toList();
    }

    private String buildSystemPrompt() {
        return """
            당신은 도서관 사서입니다. 주어진 주제와 조건, 후보 도서 목록을 보고 전시/큐레이션 초안을 만듭니다.
            큐레이션 전체의 title, description, hashtags(3~5개, '#'로 시작)와
            선정한 도서마다 isbn, reason(한 문장 추천 이유)을 산정합니다. 반드시 다음 JSON 형식으로만 답하세요:
            {"title": "...", "description": "...", "hashtags": ["#...", "#..."], "books": [{"isbn": "...", "reason": "..."}]}
            """;
    }

    private String buildUserPrompt(
            String topic, CurationGenerateRequest request, int bookCount, List<HiddenBook> candidates
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("주제: ").append(topic).append('\n');
        appendIfPresent(builder, "대상 연령대", request.targetAge());
        appendIfPresent(builder, "분위기", request.mood());
        appendIfPresent(builder, "분야", request.category());
        appendIfPresent(builder, "목적", request.purpose());
        builder.append("선정할 도서 수: ").append(bookCount).append('\n');
        builder.append("후보 도서 목록:\n");
        for (HiddenBook book : candidates) {
            builder.append("- isbn: ").append(book.getIsbn())
                    .append(", 제목: ").append(book.getTitle())
                    .append(", 키워드: ").append(String.join(", ", book.getKeywords()))
                    .append(", 소개: ").append(book.getReason())
                    .append('\n');
        }
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(": ").append(value).append('\n');
        }
    }

    private AiCurationPayload parsePayload(String content) {
        try {
            return objectMapper.readValue(content, AiCurationPayload.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 큐레이션 생성에 실패했습니다.");
        }
    }

    private int clamp(int value, int poolSize) {
        return Math.max(1, Math.min(value, poolSize));
    }

    private String validateTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "topic은 필수입니다.");
        }
        return topic.strip();
    }

    private User requireLibrarian(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        return userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);
    }

    private static Long parseUserId(String authenticatedUserId) {
        try {
            long userId = Long.parseLong(authenticatedUserId);
            if (userId <= 0) {
                throw new AuthenticationRequiredException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new AuthenticationRequiredException();
        }
    }

    private record AiBookPayload(
            @JsonProperty("isbn") String isbn,
            @JsonProperty("reason") String reason
    ) {
    }

    private record AiCurationPayload(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("hashtags") List<String> hashtags,
            @JsonProperty("books") List<AiBookPayload> books
    ) {
    }
}
