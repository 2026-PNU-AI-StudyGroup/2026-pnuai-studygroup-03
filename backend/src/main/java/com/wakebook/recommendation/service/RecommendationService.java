package com.wakebook.recommendation.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.recommendation.dto.RecommendationRequest;
import com.wakebook.recommendation.dto.RecommendationResponse;
import com.wakebook.recommendation.support.ReadingMood;
import com.wakebook.recommendation.support.ReadingPurpose;
import com.wakebook.recommendation.support.RecommendationScorer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final HiddenBookRepository hiddenBookRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public RecommendationService(HiddenBookRepository hiddenBookRepository, OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.hiddenBookRepository = hiddenBookRepository;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public List<RecommendationResponse> recommend(RecommendationRequest request) {
        validateIsbn(request.isbn());
        String libraryCode = validateLibraryCode(request.libraryCode());
        ReadingPurpose purpose = ReadingPurpose.fromLabel(request.purpose());
        ReadingMood mood = ReadingMood.fromLabel(request.mood());

        List<HiddenBook> pool = hiddenBookRepository.findAllByLibraryCode(libraryCode);
        if (pool.isEmpty()) {
            return List.of();
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(request.keywords(), purpose, mood, pool);
        String content = openAiClient.complete(systemPrompt, userPrompt);
        Map<String, AiScorePayload> scoresByIsbn = parseScores(content);

        long minLoanCount = pool.stream().mapToLong(HiddenBook::getLoanCount).min().orElse(0);
        long maxLoanCount = pool.stream().mapToLong(HiddenBook::getLoanCount).max().orElse(0);

        return pool.stream()
            .map(book -> toResponse(book, scoresByIsbn.get(book.getIsbn()), minLoanCount, maxLoanCount))
            .filter(response -> response.keywordRelevance() >= MIN_KEYWORD_RELEVANCE)
            .sorted(Comparator.comparingInt(RecommendationResponse::score).reversed())
            .toList();
    }

    private static final int MIN_KEYWORD_RELEVANCE = 20;

    private RecommendationResponse toResponse(HiddenBook book, AiScorePayload aiScore, long minLoanCount, long maxLoanCount) {
        int keywordRelevance = aiScore != null ? clamp(aiScore.keywordRelevance()) : 0;
        int purposeMatch = aiScore != null ? clamp(aiScore.purposeMatch()) : 0;
        int moodMatch = aiScore != null ? clamp(aiScore.moodMatch()) : 0;
        String reason = aiScore != null ? aiScore.reason() : book.getReason();

        int discoveryValue = RecommendationScorer.discoveryValue(book.getLoanCount(), minLoanCount, maxLoanCount);
        int score = RecommendationScorer.finalScore(
            keywordRelevance, purposeMatch, moodMatch, book.getQualityScore(), discoveryValue
        );

        return new RecommendationResponse(
            book.getIsbn(), book.getTitle(), book.getAuthor(), book.getCover(),
            score, keywordRelevance, purposeMatch, moodMatch, discoveryValue,
            reason, book.getKeywords()
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

    private String buildSystemPrompt() {
        return """
            당신은 도서관 사서입니다. 후보 도서 목록과 사용자가 고른 키워드/목적/분위기를 보고
            각 후보 도서마다 keywordRelevance, purposeMatch, moodMatch(모두 0~100 정수)와
            reason(한 문장 추천 이유)을 산정합니다. 반드시 다음 JSON 형식으로만 답하세요:
            {"results": [{"isbn": "...", "keywordRelevance": 0, "purposeMatch": 0, "moodMatch": 0, "reason": "..."}]}
            """;
    }

    private String buildUserPrompt(
        List<String> keywords, ReadingPurpose purpose, ReadingMood mood, List<HiddenBook> pool
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("사용자 선택 키워드: ").append(String.join(", ", keywords)).append('\n');
        builder.append("독서 목적: ").append(purpose.label()).append('\n');
        builder.append("원하는 분위기: ").append(mood.label()).append('\n');
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

    private void validateIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "isbn은 필수입니다.");
        }
    }

    private String validateLibraryCode(String libraryCode) {
        if (libraryCode == null || libraryCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "libraryCode는 필수입니다.");
        }
        return libraryCode.trim();
    }

    private record AiScorePayload(
        @JsonProperty("isbn") String isbn,
        @JsonProperty("keywordRelevance") Integer keywordRelevance,
        @JsonProperty("purposeMatch") Integer purposeMatch,
        @JsonProperty("moodMatch") Integer moodMatch,
        @JsonProperty("reason") String reason
    ) {
    }

    private record AiScoreListPayload(@JsonProperty("results") List<AiScorePayload> results) {
    }
}
