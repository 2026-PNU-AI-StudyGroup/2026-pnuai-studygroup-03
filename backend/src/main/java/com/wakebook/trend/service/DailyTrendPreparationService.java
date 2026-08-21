package com.wakebook.trend.service;

import com.wakebook.external.naver.NewsEvidenceProvider;
import com.wakebook.external.naver.SearchTrendValidation;
import com.wakebook.external.naver.SearchTrendValidator;
import com.wakebook.external.trend.NewsEvidence;
import com.wakebook.external.trend.TrendItem;
import com.wakebook.external.trend.TrendProvider;
import com.wakebook.trend.domain.*;
import com.wakebook.trend.repository.DailyTrendRepository;
import com.wakebook.trend.support.TrendProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DailyTrendPreparationService {
    private final TrendProvider trendProvider;
    private final NewsEvidenceProvider newsProvider;
    private final SearchTrendValidator validator;
    private final TrendAiService aiService;
    private final DailyTrendRepository repository;
    private final TrendProperties properties;
    private final ObjectMapper objectMapper;

    public DailyTrendPreparationService(TrendProvider trendProvider, NewsEvidenceProvider newsProvider,
        SearchTrendValidator validator, TrendAiService aiService, DailyTrendRepository repository,
        TrendProperties properties, ObjectMapper objectMapper) {
        this.trendProvider = trendProvider; this.newsProvider = newsProvider; this.validator = validator;
        this.aiService = aiService; this.repository = repository; this.properties = properties; this.objectMapper = objectMapper;
    }

    public synchronized List<DailyTrend> prepare(LocalDate date) {
        List<DailyTrend> existing = repository.findByTrendDateAndEligibilityOrderByFinalTrendScoreDesc(date, TrendEligibility.ELIGIBLE);
        if (repository.countByTrendDate(date) > 0)
            return existing.stream().limit(properties.candidateLimit()).toList();

        List<TrendItem> candidates = trendProvider.fetchDailyTrends("KR", properties.candidateLimit()).stream()
            .filter(item -> item.keyword() != null && item.keyword().matches(".*[가-힣].*"))
            .collect(Collectors.toMap(item -> normalize(item.keyword()), Function.identity(), (a, b) -> a,
                LinkedHashMap::new)).values().stream().toList();
        if (candidates.isEmpty())
            throw new com.wakebook.common.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "TREND_002", "사용할 수 있는 국내 트렌드 후보가 없습니다.");
        Map<String, List<NewsEvidence>> naverEvidence = new LinkedHashMap<>();
        for (TrendItem item : candidates) naverEvidence.put(item.sourceKey(), newsProvider.search(item.keyword(), 3));
        List<TrendAiService.EvidenceCandidate> inputs = candidates.stream()
            .map(item -> new TrendAiService.EvidenceCandidate(item, naverEvidence.get(item.sourceKey()))).toList();
        Map<String, TrendEnrichment> enriched = aiService.enrich(inputs).stream()
            .collect(Collectors.toMap(TrendEnrichment::sourceKey, Function.identity(), (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        List<DailyTrend> saved = new ArrayList<>();
        for (TrendItem item : candidates) {
            TrendEnrichment value = enriched.get(item.sourceKey());
            if (value == null) continue;
            SearchTrendValidation validation = validator.validate(item.keyword(), value.displayTopic());
            TrendEligibility eligibility = effectiveEligibility(value, validation);
            double googleScore = Math.max(0, 1.0 - ((double) item.sourceRank() - 1) / Math.max(1, properties.candidateLimit()));
            double finalScore = validation.spikeScore() == null
                ? googleScore * .60 + value.evidenceConsistencyScore() * .40
                : googleScore * .40 + value.evidenceConsistencyScore() * .35 + Math.min(1, validation.spikeScore() / 2) * .25;
            saved.add(new DailyTrend(date, "GOOGLE_TRENDS", item.sourceKey(), item.keyword(), normalize(item.keyword()),
                value.displayTopic(), value.topicConfidence(), item.sourceRank(), item.trafficLabel(), item.startedAt(),
                item.sourceUrl(), toJson(item.evidence()), toJson(naverEvidence.get(item.sourceKey())), value.contextDescription(),
                value.retrievalIntent(), toJson(value.requiredConceptGroups()),
                eligibility, value.evidenceConsistencyScore(), validation.status(), validation.spikeScore(), finalScore,
                now, naverEvidence.get(item.sourceKey()).isEmpty() ? null : now,
                validation.status() == TrendValidationStatus.UNVERIFIED && validation.spikeScore() == null ? null : now));
        }
        repository.saveAll(saved);
        return saved.stream().filter(t -> t.getEligibility() == TrendEligibility.ELIGIBLE)
            .sorted(Comparator.comparingDouble(DailyTrend::getFinalTrendScore).reversed())
            .limit(properties.candidateLimit()).toList();
    }

    private TrendEligibility effectiveEligibility(TrendEnrichment value, SearchTrendValidation validation) {
        if (value.eligibility() != TrendEligibility.ELIGIBLE) return value.eligibility();
        if (value.topicConfidence() < properties.minimumTopicConfidence()
            || value.evidenceConsistencyScore() < properties.minimumEvidenceConsistency()
            || validation.status() == TrendValidationStatus.CONTRADICTED) return TrendEligibility.EVIDENCE_MISMATCH;
        return TrendEligibility.ELIGIBLE;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[^0-9a-z가-힣]", "");
    }
    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ignored) { return "[]"; }
    }
}
