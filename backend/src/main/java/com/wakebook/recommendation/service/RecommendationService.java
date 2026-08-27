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
import com.wakebook.recommendation.dto.RecommendationRequest;
import com.wakebook.recommendation.dto.RecommendationResponse;
import com.wakebook.recommendation.support.ReadingMood;
import com.wakebook.recommendation.support.ReadingPurpose;
import com.wakebook.recommendation.support.ReadingAudienceClassifier;
import com.wakebook.recommendation.support.ReadingAudienceClassifier.Audience;
import com.wakebook.recommendation.support.RecommendationScorer;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
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

@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 15;
    private static final int MAX_LIMIT = 15;
    private static final int AI_CANDIDATE_LIMIT = 60;
    private static final int AI_FALLBACK_CANDIDATE_LIMIT = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 400;
    private static final int MIN_KEYWORD_RELEVANCE = 35;
    private static final int MIN_AI_RELEVANCE_WITHOUT_SERVER_MATCH = 70;
    private static final double PREFERRED_SERVER_RELEVANCE = .10;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[가-힣A-Za-z0-9]{2,}");

    private final HiddenBookRepository hiddenBookRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final BookDetailProvider bookDetailProvider;

    public RecommendationService(
        HiddenBookRepository hiddenBookRepository,
        OpenAiClient openAiClient,
        ObjectMapper objectMapper,
        BookDetailProvider bookDetailProvider
    ) {
        this.hiddenBookRepository = hiddenBookRepository;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.bookDetailProvider = bookDetailProvider;
    }

    @Cacheable(cacheNames = CacheConfig.AI_RECOMMENDATIONS, key = "#request", unless = "#result == null")
    public List<RecommendationResponse> recommend(RecommendationRequest request) {
        validateIsbn(request.isbn());
        String libraryCode = validateLibraryCode(request.libraryCode());
        ReadingPurpose purpose = ReadingPurpose.fromLabel(request.purpose());
        ReadingMood mood = ReadingMood.fromLabel(request.mood());
        int resultLimit = resolveLimit(request.limit());
        BookDetail sourceBook = bookDetailProvider.fetch(request.isbn().trim()).orElse(null);
        Audience sourceAudience = ReadingAudienceClassifier.source(sourceBook);

        List<HiddenBook> pool = hiddenBookRepository.findAllByLibraryCode(libraryCode);
        if (pool.isEmpty()) {
            return List.of();
        }
        List<ServerCandidate> candidates = shortlist(pool, request.isbn(), request.keywords(), sourceAudience);
        if (candidates.isEmpty()) {
            return List.of();
        }

        String systemPrompt = buildSystemPrompt(resultLimit);
        String userPrompt = buildUserPrompt(
            sourceBook, sourceAudience, request.keywords(), purpose, mood, candidates, resultLimit
        );
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
            .filter(response -> response.keywordRelevance() >= MIN_KEYWORD_RELEVANCE)
            .sorted(Comparator.comparingInt(RecommendationResponse::score).reversed())
            .limit(resultLimit)
            .toList();
    }

    /**
     * 도서관 후보군 200권은 유지하되, 외부 AI에는 관련성이 높은 최대 60권만 보낸다.
     * 이 단계는 저장된 제목·키워드·소개만 비교하므로 외부 API 호출이나 추가 비용이 없다.
     */
    private List<ServerCandidate> shortlist(
        List<HiddenBook> pool,
        String sourceIsbn,
        List<String> keywords,
        Audience sourceAudience
    ) {
        Set<String> queryPhrases = keywords.stream()
            .map(this::normalize)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> queryTokens = queryPhrases.stream()
            .flatMap(value -> tokens(value).stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ServerCandidate> ranked = pool.stream()
            .filter(book -> !book.getIsbn().equals(sourceIsbn))
            .map(book -> new ServerCandidate(
                book, serverRelevance(book, queryPhrases, queryTokens), ReadingAudienceClassifier.candidate(book)
            ))
            .filter(candidate -> ReadingAudienceClassifier.matches(sourceAudience, candidate.audience()))
            .sorted(Comparator.comparingDouble(ServerCandidate::relevance).reversed()
                .thenComparing(candidate -> candidate.book().getQualityScore(), Comparator.reverseOrder())
                .thenComparingLong(candidate -> candidate.book().getLoanCount())
                .thenComparing(candidate -> candidate.book().getTitle()))
            .toList();

        List<ServerCandidate> preferred = ranked.stream()
            .filter(candidate -> candidate.relevance() >= PREFERRED_SERVER_RELEVANCE)
            .limit(AI_CANDIDATE_LIMIT)
            .toList();
        if (preferred.size() >= AI_FALLBACK_CANDIDATE_LIMIT || ranked.size() <= preferred.size()) {
            return preferred;
        }

        LinkedHashSet<String> used = preferred.stream().map(candidate -> candidate.book().getIsbn())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        java.util.ArrayList<ServerCandidate> supplemented = new java.util.ArrayList<>(preferred);
        ranked.stream()
            .filter(candidate -> used.add(candidate.book().getIsbn()))
            .limit(AI_FALLBACK_CANDIDATE_LIMIT - preferred.size())
            .forEach(supplemented::add);
        return List.copyOf(supplemented);
    }

    private boolean isRelevant(ServerCandidate candidate, AiScorePayload aiScore) {
        return candidate.relevance() >= PREFERRED_SERVER_RELEVANCE
            || clamp(aiScore.keywordRelevance()) >= MIN_AI_RELEVANCE_WITHOUT_SERVER_MATCH;
    }

    private double serverRelevance(HiddenBook book, Set<String> queryPhrases, Set<String> queryTokens) {
        String title = normalize(book.getTitle());
        String description = normalize(HiddenBookPromptSummary.resolve(book));
        List<String> bookKeywords = book.getKeywords().stream().map(this::normalize).toList();
        Set<String> titleTokens = tokens(title);
        Set<String> descriptionTokens = tokens(description);
        Set<String> keywordTokens = bookKeywords.stream().flatMap(value -> tokens(value).stream())
            .collect(Collectors.toSet());

        double bestPhraseMatch = 0;
        for (String phrase : queryPhrases) {
            double phraseMatch = 0;
            if (bookKeywords.contains(phrase)) phraseMatch = 1;
            else if (title.contains(phrase)) phraseMatch = .9;
            else if (description.contains(phrase)) phraseMatch = .75;
            else if (bookKeywords.stream().anyMatch(value -> value.contains(phrase) || phrase.contains(value))) {
                phraseMatch = .65;
            }
            bestPhraseMatch = Math.max(bestPhraseMatch, phraseMatch);
        }
        long matchedTokens = 0;
        for (String token : queryTokens) {
            if (keywordTokens.contains(token) || titleTokens.contains(token) || descriptionTokens.contains(token)) {
                matchedTokens++;
            }
        }
        double tokenCoverage = queryTokens.isEmpty() ? 0 : (double) matchedTokens / queryTokens.size();
        return Math.min(1, bestPhraseMatch * .7 + tokenCoverage * .3);
    }

    private int resolveLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(requested, MAX_LIMIT));
    }

    private RecommendationResponse toResponse(
        ServerCandidate candidate,
        AiScorePayload aiScore,
        long minLoanCount,
        long maxLoanCount
    ) {
        HiddenBook book = candidate.book();
        int aiKeywordRelevance = aiScore != null ? clamp(aiScore.keywordRelevance()) : 0;
        int keywordRelevance = (int) Math.round(aiKeywordRelevance * .65 + candidate.relevance() * 100 * .35);
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
            reason, book.getKeywords(),
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

    private String buildSystemPrompt(int resultLimit) {
        return """
            당신은 도서관 사서입니다. 기준 인기 도서와 사용자가 고른 키워드/목적/분위기를 함께 보고
            가장 적합한 도서만 최대 %d권 선택하세요. 후보 전체의 평가 결과를 만들지 마세요.
            기준 도서와 후보 도서의 예상 독자층이 어긋나면 선택하지 마세요.
            GENERAL은 연령 제한 정보가 없는 후보이며 CHILD, TEEN, ADULT 모두와 호환됩니다.
            연령대, 주제 또는 맥락이 맞는 후보가 없으면 results를 빈 배열로 반환하세요. 권수를 억지로 채우지 마세요.
            선택한 도서에만 keywordRelevance, purposeMatch, moodMatch(모두 0~100 정수)와
            reason(한 문장 추천 이유)을 산정합니다. 후보 목록에 없는 ISBN을 만들지 마세요.
            반드시 다음 JSON 형식으로만 답하세요:
            {"results": [{"isbn": "...", "keywordRelevance": 0, "purposeMatch": 0, "moodMatch": 0, "reason": "..."}]}
            """.formatted(resultLimit);
    }

    private String buildUserPrompt(
        BookDetail sourceBook,
        Audience sourceAudience,
        List<String> keywords,
        ReadingPurpose purpose,
        ReadingMood mood,
        List<ServerCandidate> candidates,
        int resultLimit
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("기준 인기 도서 제목: ").append(sourceBook == null ? "정보 없음" : sourceBook.title()).append('\n');
        builder.append("기준 인기 도서 저자: ").append(sourceBook == null ? "정보 없음" : sourceBook.author()).append('\n');
        builder.append("기준 인기 도서 소개: ")
            .append(sourceBook == null ? "정보 없음" : safeText(sourceBook.description(), MAX_DESCRIPTION_LENGTH))
            .append('\n');
        builder.append("기준 도서 예상 독자층: ").append(sourceAudience).append('\n');
        builder.append("사용자 선택 키워드: ").append(String.join(", ", keywords)).append('\n');
        builder.append("독서 목적: ").append(purpose.label()).append('\n');
        builder.append("원하는 분위기: ").append(mood.label()).append('\n');
        builder.append("최대 추천 권수: ").append(resultLimit).append('\n');
        builder.append("후보 도서 목록:\n");
        for (ServerCandidate candidate : candidates) {
            HiddenBook book = candidate.book();
            builder.append("- isbn: ").append(book.getIsbn())
                .append(", 제목: ").append(book.getTitle())
                .append(", 키워드: ").append(String.join(", ", book.getKeywords()))
                .append(", 예상 독자층: ").append(candidate.audience())
                .append(", 서가: ").append(safeText(book.getShelfName(), 100))
                .append(", 서버 관련성: ").append(Math.round(candidate.relevance() * 100))
                .append(", 소개: ").append(safeText(HiddenBookPromptSummary.resolve(book), MAX_DESCRIPTION_LENGTH))
                .append('\n');
        }
        return builder.toString();
    }

    private Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private String safeText(String value, int maxLength) {
        String text = value == null ? "" : value.strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
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

    private record ServerCandidate(HiddenBook book, double relevance, Audience audience) {
    }
}
