package com.wakebook.recommendation.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.book.support.HiddenBookPromptSummary;
import com.wakebook.common.ApiException;
import com.wakebook.common.config.CacheConfig;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.recommendation.dto.ExploreRequest;
import com.wakebook.recommendation.dto.ExploreResponse;
import com.wakebook.recommendation.support.ExploreType;
import com.wakebook.recommendation.support.ReadingAudienceClassifier;
import com.wakebook.recommendation.support.ReadingAudienceClassifier.Audience;
import com.wakebook.recommendation.support.RecommendationScorer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 4.4 연관 조건 재탐색. 명세(docs/API명세.md)에 응답 스키마가 없어 잠정적으로 정의했다 (docs/tasks.md 참고).
 */
@Service
public class RecommendationExploreService {

    private static final int RESULT_LIMIT = 9;
    private static final int AI_CANDIDATE_LIMIT = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 400;
    private static final int MIN_RELEVANCE = 35;
    private static final int MIN_AI_RELEVANCE_WITHOUT_SERVER_MATCH = 70;
    private static final double PREFERRED_SERVER_RELEVANCE = .05;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[가-힣A-Za-z0-9]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
        "그리고", "그러나", "대한", "위한", "통해", "있는", "하는", "한다", "책은", "도서", "이야기",
        "작가", "저자", "우리", "그의", "그녀", "에서", "으로", "에게", "까지", "보다", "다시", "새로운"
    );

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

    @Cacheable(cacheNames = CacheConfig.AI_EXPLORE, key = "#request", unless = "#result == null")
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
        Audience sourceAudience = ReadingAudienceClassifier.source(baseBook);
        List<ExploreCandidate> candidates = shortlist(baseBook, sourceAudience, type, pool);
        if (candidates.isEmpty()) return List.of();

        String systemPrompt = buildSystemPrompt(type);
        String userPrompt = buildUserPrompt(baseBook, sourceAudience, candidates);
        String content = openAiClient.complete(systemPrompt, userPrompt);
        Map<String, AiScorePayload> scoresByIsbn = parseScores(content);

        long minLoanCount = pool.stream().mapToLong(HiddenBook::getLoanCount).min().orElse(0);
        long maxLoanCount = pool.stream().mapToLong(HiddenBook::getLoanCount).max().orElse(0);

        return candidates.stream()
            .filter(candidate -> scoresByIsbn.containsKey(candidate.book().getIsbn()))
            .filter(candidate -> isRelevant(candidate, scoresByIsbn.get(candidate.book().getIsbn())))
            .map(candidate -> toResponse(
                candidate, scoresByIsbn.get(candidate.book().getIsbn()), minLoanCount, maxLoanCount
            ))
            .filter(response -> response.relevance() >= MIN_RELEVANCE)
            .sorted(Comparator.comparingInt(ExploreResponse::score).reversed())
            .limit(RESULT_LIMIT)
            .toList();
    }

    private List<ExploreCandidate> shortlist(
        BookDetail baseBook,
        Audience sourceAudience,
        ExploreType type,
        List<HiddenBook> pool
    ) {
        Set<String> baseTokens = tokens(safe(baseBook.title()) + " " + safe(baseBook.description()));
        int baseLength = safe(baseBook.description()).length();
        return pool.stream()
            .map(book -> new ExploreCandidate(
                book,
                serverRelevance(baseTokens, baseLength, book, type),
                ReadingAudienceClassifier.candidate(book)
            ))
            .filter(candidate -> ReadingAudienceClassifier.matches(sourceAudience, candidate.audience()))
            .sorted(Comparator.comparingDouble(ExploreCandidate::serverRelevance).reversed()
                .thenComparing(candidate -> candidate.book().getQualityScore(), Comparator.reverseOrder())
                .thenComparingLong(candidate -> candidate.book().getLoanCount())
                .thenComparing(candidate -> candidate.book().getTitle()))
            .limit(AI_CANDIDATE_LIMIT)
            .toList();
    }

    private double serverRelevance(Set<String> baseTokens, int baseLength, HiddenBook book, ExploreType type) {
        String description = safe(HiddenBookPromptSummary.resolve(book));
        Set<String> candidateTokens = tokens(safe(book.getTitle()) + " "
            + String.join(" ", book.getKeywords()) + " " + description);
        long overlap = baseTokens.stream().filter(candidateTokens::contains).count();
        double topicScore = baseTokens.isEmpty() ? 0
            : Math.min(1, (double) overlap / Math.min(20, baseTokens.size()));
        double lengthScore = switch (type) {
            case EASIER -> relativeLengthScore(description.length(), baseLength, true);
            case DEEPER -> relativeLengthScore(description.length(), baseLength, false);
            default -> topicScore;
        };
        return switch (type) {
            case EASIER, DEEPER -> Math.min(1, topicScore * .75 + lengthScore * .25);
            default -> topicScore;
        };
    }

    private double relativeLengthScore(int candidateLength, int baseLength, boolean easier) {
        if (baseLength <= 0) return .5;
        double ratio = (double) candidateLength / baseLength;
        return easier ? Math.max(0, Math.min(1, 1.5 - ratio)) : Math.max(0, Math.min(1, ratio - .5));
    }

    private boolean isRelevant(ExploreCandidate candidate, AiScorePayload aiScore) {
        return candidate.serverRelevance() >= PREFERRED_SERVER_RELEVANCE
            || clamp(aiScore.relevance()) >= MIN_AI_RELEVANCE_WITHOUT_SERVER_MATCH;
    }

    private ExploreResponse toResponse(
        ExploreCandidate candidate,
        AiScorePayload aiScore,
        long minLoanCount,
        long maxLoanCount
    ) {
        HiddenBook book = candidate.book();
        int aiRelevance = aiScore != null ? clamp(aiScore.relevance()) : 0;
        int relevance = (int) Math.round(aiRelevance * .75 + candidate.serverRelevance() * 100 * .25);
        String reason = aiScore != null ? aiScore.reason() : book.getReason();

        int discoveryValue = RecommendationScorer.discoveryValue(book.getLoanCount(), minLoanCount, maxLoanCount);
        int score = RecommendationScorer.finalScore(relevance, relevance, relevance, book.getQualityScore(), discoveryValue);

        return new ExploreResponse(
            book.getIsbn(), book.getTitle(), book.getAuthor(), book.getCover(),
            score, relevance, discoveryValue, reason, book.getKeywords(),
            book.getLibraryName(), book.getCallNumber(), book.getShelfName()
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
            reason(한 문장 이유)을 산정합니다: %s
            후보 전체를 평가하지 말고 가장 적합한 책만 최대 9권 선택하세요.
            기준 도서와 후보의 예상 독자층이 어긋나면 선택하지 마세요. GENERAL은 모든 독자층과 호환됩니다.
            적합한 책이 없으면 results를 빈 배열로 반환하고, 후보 목록에 없는 ISBN은 만들지 마세요.
            반드시 다음 JSON 형식으로만 답하세요:
            {"results": [{"isbn": "...", "relevance": 0, "reason": "..."}]}
            """.formatted(criteria);
    }

    private String buildUserPrompt(BookDetail baseBook, Audience sourceAudience, List<ExploreCandidate> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("기준 도서 - 제목: ").append(baseBook.title())
            .append(", 예상 독자층: ").append(sourceAudience)
            .append(", 설명: ").append(truncate(baseBook.description(), MAX_DESCRIPTION_LENGTH)).append('\n');
        builder.append("후보 도서 목록:\n");
        for (ExploreCandidate candidate : candidates) {
            HiddenBook book = candidate.book();
            builder.append("- isbn: ").append(book.getIsbn())
                .append(", 제목: ").append(book.getTitle())
                .append(", 키워드: ").append(String.join(", ", book.getKeywords()))
                .append(", 예상 독자층: ").append(candidate.audience())
                .append(", 서가: ").append(truncate(book.getShelfName(), 100))
                .append(", 서버 관련성: ").append(Math.round(candidate.serverRelevance() * 100))
                .append(", 소개: ").append(truncate(HiddenBookPromptSummary.resolve(book), MAX_DESCRIPTION_LENGTH))
                .append('\n');
        }
        return builder.toString();
    }

    private Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(safe(text).toLowerCase(Locale.ROOT));
        while (matcher.find()) if (!STOP_WORDS.contains(matcher.group())) result.add(matcher.group());
        return result;
    }

    private String truncate(String value, int maxLength) {
        String text = safe(value).strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
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

    private record ExploreCandidate(HiddenBook book, double serverRelevance, Audience audience) {
    }
}
