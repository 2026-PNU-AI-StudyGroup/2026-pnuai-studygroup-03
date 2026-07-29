package com.wakebook.recommendation.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.recommendation.dto.ExploreRequest;
import com.wakebook.recommendation.dto.ExploreResponse;
import com.wakebook.recommendation.support.ExploreType;
import com.wakebook.recommendation.support.RecommendationScorer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 4.4 연관 조건 재탐색. 명세(docs/API명세.md)에 응답 스키마가 없어 잠정적으로 정의했다 (docs/tasks.md 참고).
 */
@Service
public class RecommendationExploreService {

    private final HiddenBookRepository hiddenBookRepository;
    private final BookDetailProvider bookDetailProvider;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public RecommendationExploreService(
        HiddenBookRepository hiddenBookRepository,
        BookDetailProvider bookDetailProvider,
        OpenAiClient openAiClient,
        ObjectMapper objectMapper
    ) {
        this.hiddenBookRepository = hiddenBookRepository;
        this.bookDetailProvider = bookDetailProvider;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public List<ExploreResponse> explore(ExploreRequest request) {
        if (request.isbn() == null || request.isbn().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "isbn은 필수입니다.");
        }
        if (request.libraryCode() == null || request.libraryCode().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "libraryCode는 필수입니다.");
        }
        ExploreType type = ExploreType.fromValue(request.type());

        BookDetail baseBook = bookDetailProvider.fetch(request.isbn().trim())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "도서를 찾을 수 없습니다."));

        List<HiddenBook> pool = hiddenBookRepository.findAllByLibraryCode(request.libraryCode().trim()).stream()
            .filter(book -> !book.getIsbn().equals(baseBook.isbn()))
            .toList();
        if (pool.isEmpty()) {
            return List.of();
        }

        String systemPrompt = buildSystemPrompt(type);
        String userPrompt = buildUserPrompt(baseBook, pool);
        String content = openAiClient.complete(systemPrompt, userPrompt);
        Map<String, AiScorePayload> scoresByIsbn = parseScores(content);

        long minLoanCount = pool.stream().mapToLong(HiddenBook::getLoanCount).min().orElse(0);
        long maxLoanCount = pool.stream().mapToLong(HiddenBook::getLoanCount).max().orElse(0);

        return pool.stream()
            .map(book -> toResponse(book, scoresByIsbn.get(book.getIsbn()), minLoanCount, maxLoanCount))
            .sorted(Comparator.comparingInt(ExploreResponse::score).reversed())
            .toList();
    }

    private ExploreResponse toResponse(HiddenBook book, AiScorePayload aiScore, long minLoanCount, long maxLoanCount) {
        int relevance = aiScore != null ? clamp(aiScore.relevance()) : 0;
        String reason = aiScore != null ? aiScore.reason() : book.getReason();

        int discoveryValue = RecommendationScorer.discoveryValue(book.getLoanCount(), minLoanCount, maxLoanCount);
        int score = RecommendationScorer.finalScore(relevance, relevance, relevance, relevance, book.getQualityScore(), discoveryValue);

        return new ExploreResponse(
            book.getIsbn(), book.getTitle(), book.getAuthor(), book.getCover(),
            score, relevance, discoveryValue, reason, book.getKeywords()
        );
    }

    private Map<String, AiScorePayload> parseScores(String content) {
        try {
            AiScoreListPayload payload = objectMapper.readValue(content, AiScoreListPayload.class);
            return payload.results().stream()
                .filter(result -> result.isbn() != null)
                .collect(Collectors.toMap(AiScorePayload::isbn, result -> result, (a, b) -> a));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 추천 생성에 실패했습니다.");
        }
    }

    private String buildSystemPrompt(ExploreType type) {
        String criteria = switch (type) {
            case SIMILAR_TOPIC -> "기준 도서와 주제/키워드가 비슷한 책일수록 높은 점수";
            case SAME_MOOD -> "기준 도서와 분위기·문체가 비슷한 책일수록 높은 점수";
            case EASIER -> "기준 도서보다 더 쉽고 편하게 읽을 수 있는 책일수록 높은 점수";
            case DEEPER -> "기준 도서보다 더 깊이 있고 어려운 책일수록 높은 점수";
            case OPPOSITE_VIEW -> "기준 도서와 반대되는 관점을 제시하는 책일수록 높은 점수";
        };
        return """
            당신은 도서관 사서입니다. 기준 도서와 후보 도서 목록을 보고 다음 기준으로 relevance(0~100 정수)와
            reason(한 문장 이유)을 각 후보마다 산정합니다: %s
            반드시 다음 JSON 형식으로만 답하세요:
            {"results": [{"isbn": "...", "relevance": 0, "reason": "..."}]}
            """.formatted(criteria);
    }

    private String buildUserPrompt(BookDetail baseBook, List<HiddenBook> pool) {
        StringBuilder builder = new StringBuilder();
        builder.append("기준 도서 - 제목: ").append(baseBook.title())
            .append(", 설명: ").append(baseBook.description()).append('\n');
        builder.append("후보 도서 목록:\n");
        for (HiddenBook book : pool) {
            builder.append("- isbn: ").append(book.getIsbn())
                .append(", 제목: ").append(book.getTitle())
                .append(", 키워드: ").append(String.join(", ", book.getKeywords()))
                .append(", 소개: ").append(book.getReason())
                .append('\n');
        }
        return builder.toString();
    }

    private int clamp(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }

    private record AiScorePayload(
        @JsonProperty("isbn") String isbn,
        @JsonProperty("relevance") Integer relevance,
        @JsonProperty("reason") String reason
    ) {
    }

    private record AiScoreListPayload(@JsonProperty("results") List<AiScorePayload> results) {
    }
}
