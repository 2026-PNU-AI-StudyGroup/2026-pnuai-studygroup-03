package com.wakebook.trend.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.trend.domain.DailyTrend;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrendBookMatcher {
    private static final Pattern TOKEN = Pattern.compile("[가-힣A-Za-z0-9]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
        "관련", "분야", "기술", "도입", "대한", "통해", "위한", "주목", "관심", "최근", "논의", "움직임"
    );
    private final ObjectMapper objectMapper;

    public TrendBookMatcher(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public List<DailyTrend> rankForLibrary(List<DailyTrend> trends, List<HiddenBook> books, int limit) {
        return trends.stream().map(trend -> new TrendScore(trend, bestScore(trend, books)))
            .filter(score -> score.bookScore() >= 0)
            .sorted(Comparator.comparingDouble(TrendScore::combinedScore).reversed())
            .limit(limit).map(TrendScore::trend).toList();
    }

    public List<BookMatch> shortlist(DailyTrend trend, List<HiddenBook> books, int limit) {
        List<List<String>> groups = conceptGroups(trend);
        Set<String> queryTokens = tokens(String.join(" ", safe(trend.getDisplayTopic()),
            safe(trend.getContextDescription()), safe(trend.getRetrievalIntent())));
        List<BookMatch> scored = books.stream().map(book -> score(book, queryTokens, groups)).toList();

        List<BookMatch> fullMatches = rank(scored, BookMatch::conceptGatePassed, limit);
        if (!fullMatches.isEmpty() || groups.size() <= 1) {
            return fullMatches;
        }
        // 개념군을 전부(AND) 만족하는 책이 하나도 없으면, 일부만 맞는 책이라도 후보로 살린다(완전히
        // 무관한 책은 계속 제외). 후보군이 작을 때 "완전 매칭"만 고집하면 후보가 아예 안 나오는 경우가
        // 잦았기 때문이다. 최종 관련성 판단은 TrendAiService.recommend()의 matchScore 기준
        // (minimumBookMatchScore)이 한 번 더 걸러주므로, 여기서 완전 매칭을 못 찾았을 때만 완화한다.
        return rank(scored, match -> !match.matchedConcepts().isEmpty(), limit);
    }

    private List<BookMatch> rank(List<BookMatch> scored, java.util.function.Predicate<BookMatch> gate, int limit) {
        return scored.stream().filter(gate)
            .sorted(Comparator.comparingDouble(BookMatch::serverScore).reversed()
                .thenComparing(match -> match.book().getQualityScore(), Comparator.reverseOrder()))
            .limit(limit).toList();
    }

    private double bestScore(DailyTrend trend, List<HiddenBook> books) {
        return shortlist(trend, books, 1).stream().mapToDouble(BookMatch::serverScore).findFirst().orElse(-1);
    }

    private BookMatch score(HiddenBook book, Set<String> queryTokens, List<List<String>> groups) {
        String bookText = String.join(" ", safe(book.getTitle()), safe(book.getDescription()),
            String.join(" ", book.getKeywords())).toLowerCase(Locale.ROOT);
        Set<String> bookTokens = tokens(bookText);
        long overlap = queryTokens.stream().filter(bookTokens::contains).count();
        double lexical = queryTokens.isEmpty() ? 0 : Math.min(1, (double) overlap / Math.min(10, queryTokens.size()));
        List<String> matchedGroups = new ArrayList<>();
        for (List<String> group : groups) {
            String matched = group.stream().filter(alternative -> matches(bookText, bookTokens, alternative)).findFirst().orElse(null);
            if (matched != null) matchedGroups.add(matched);
        }
        double groupCoverage = groups.isEmpty() ? 1 : (double) matchedGroups.size() / groups.size();
        boolean gate = groups.isEmpty() || matchedGroups.size() == groups.size();
        double score = Math.min(1, lexical * .70 + groupCoverage * .30);
        return new BookMatch(book, score, gate, matchedGroups);
    }

    private boolean matches(String bookText, Set<String> bookTokens, String alternative) {
        if (alternative == null || alternative.isBlank()) return false;
        String normalized = alternative.strip().toLowerCase(Locale.ROOT);
        if (bookText.contains(normalized)) return true;
        Set<String> alternativeTokens = tokens(normalized);
        return !alternativeTokens.isEmpty() && bookTokens.containsAll(alternativeTokens);
    }

    private List<List<String>> conceptGroups(DailyTrend trend) {
        if (trend.getRequiredConcepts() == null || trend.getRequiredConcepts().isBlank()) return List.of();
        try {
            List<List<String>> parsed = objectMapper.readValue(trend.getRequiredConcepts(), new TypeReference<>() {});
            return parsed == null ? List.of() : parsed.stream().filter(group -> group != null && !group.isEmpty()).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Set<String> tokens(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(safe(text).toLowerCase(Locale.ROOT));
        while (matcher.find()) if (!STOP_WORDS.contains(matcher.group())) result.add(matcher.group());
        return result;
    }
    private static String safe(String value) { return value == null ? "" : value; }

    public record BookMatch(HiddenBook book, double serverScore, boolean conceptGatePassed,
                            List<String> matchedConcepts) {}
    private record TrendScore(DailyTrend trend, double bookScore) {
        double combinedScore() { return trend.getFinalTrendScore() * .45 + Math.max(0, bookScore) * .55; }
    }
}
