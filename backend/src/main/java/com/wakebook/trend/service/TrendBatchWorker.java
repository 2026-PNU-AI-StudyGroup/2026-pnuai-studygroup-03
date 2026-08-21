package com.wakebook.trend.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.Data4LibraryHoldingLookup;
import com.wakebook.trend.domain.DailyTrend;
import com.wakebook.trend.domain.DailyTrendBatch;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import com.wakebook.trend.support.TrendProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrendBatchWorker {
    private static final Logger log = LoggerFactory.getLogger(TrendBatchWorker.class);
    private final DailyTrendBatchRepository batchRepository;
    private final HiddenBookRepository hiddenBookRepository;
    private final DailyTrendPreparationService preparationService;
    private final TrendAiService aiService;
    private final TrendBookMatcher bookMatcher;
    private final TrendRecommendationWriter writer;
    private final TrendBatchStateService stateService;
    private final TrendProperties properties;
    private final Data4LibraryHoldingLookup holdingLookup;

    public TrendBatchWorker(DailyTrendBatchRepository batchRepository, HiddenBookRepository hiddenBookRepository,
        DailyTrendPreparationService preparationService, TrendAiService aiService, TrendBookMatcher bookMatcher,
        TrendRecommendationWriter writer, TrendBatchStateService stateService, TrendProperties properties,
        Data4LibraryHoldingLookup holdingLookup) {
        this.batchRepository = batchRepository;
        this.hiddenBookRepository = hiddenBookRepository;
        this.preparationService = preparationService;
        this.aiService = aiService;
        this.bookMatcher = bookMatcher;
        this.writer = writer;
        this.stateService = stateService;
        this.properties = properties;
        this.holdingLookup = holdingLookup;
    }

    @Async
    public void generate(Long batchId) {
        try {
            stateService.start(batchId);
            DailyTrendBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TREND_001", "배치를 찾을 수 없습니다."));
            // 한 도서관 후보군만으로 매칭하면 트렌드와 겹치는 책이 아예 없는 경우가 잦았다(실측).
            // 매칭은 전체 도서관 후보군에서 하고, 최종 후보만 이 도서관에서 실제로 빌릴 수 있는지 확인한다.
            List<HiddenBook> allBooks = hiddenBookRepository.findAll();
            if (allBooks.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "잠자는 도서 후보가 없습니다.");

            List<DailyTrend> libraryCandidates = bookMatcher.rankForLibrary(
                preparationService.prepare(batch.getRecommendationDate()), allBooks,
                properties.libraryTrendCandidateCount());
            if (libraryCandidates.isEmpty())
                throw new ApiException(HttpStatus.NOT_FOUND, "TREND_005", "이 도서관에서 트렌드와 연결할 도서를 찾지 못했습니다.");
            List<TrendAiService.GeneratedRecommendation> matched = selectFinalRecommendations(
                libraryCandidates, aiService.recommend(libraryCandidates, allBooks, properties.booksPerTrend()));
            List<TrendAiService.GeneratedRecommendation> generated =
                filterAvailableAtLibrary(matched, batch.getLibraryCode());
            if (generated.isEmpty())
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "관련성 기준을 통과한 도서를 생성하지 못했습니다.");
            writer.replace(batchId, generated);
        } catch (Exception e) {
            String code = e instanceof ApiException api ? api.getCode() : "TREND_002";
            log.error("트렌드 추천 배치 실패 (batchId={})", batchId, e);
            stateService.fail(batchId, code);
        }
    }

    /**
     * 추천된 책이 이 도서관 자체 후보군에서 나왔으면 이미 그 도서관 소장이 확인된 것이다.
     * 다른 도서관 후보군에서 나온 책만 정보나루 bookExist로 실제 대출 가능 여부를 확인한다
     * (결과가 캐싱되므로 같은 책을 여러 도서관이 반복 조회해도 비용이 크지 않다). 최종 후보
     * 개수(topTrendCount×booksPerTrend, 보통 10권 안팎)만 확인하므로 카탈로그 스캔보다 훨씬 싸다.
     */
    private List<TrendAiService.GeneratedRecommendation> filterAvailableAtLibrary(
        List<TrendAiService.GeneratedRecommendation> matched, String libraryCode
    ) {
        return matched.stream()
            .filter(item -> libraryCode.equals(item.book().getLibraryCode())
                || Boolean.TRUE.equals(holdingLookup.findAvailability(item.book().getIsbn(), libraryCode)))
            .toList();
    }

    private List<TrendAiService.GeneratedRecommendation> selectFinalRecommendations(
        List<DailyTrend> trends, List<TrendAiService.GeneratedRecommendation> generated
    ) {
        Map<Long, DailyTrend> trendsById = trends.stream()
            .collect(Collectors.toMap(DailyTrend::getId, Function.identity()));
        Map<Long, List<TrendAiService.GeneratedRecommendation>> byTrend = generated.stream()
            .collect(Collectors.groupingBy(TrendAiService.GeneratedRecommendation::trendId));
        List<Long> selectedTrendIds = byTrend.entrySet().stream()
            .sorted(Comparator.<Map.Entry<Long, List<TrendAiService.GeneratedRecommendation>>>comparingDouble(entry -> {
                DailyTrend trend = trendsById.get(entry.getKey());
                double match = entry.getValue().stream()
                    .mapToDouble(TrendAiService.GeneratedRecommendation::matchScore).max().orElse(0);
                return (trend == null ? 0 : trend.getFinalTrendScore()) * .45 + match * .55;
            }).reversed())
            .limit(properties.topTrendCount()).map(Map.Entry::getKey).toList();

        Set<String> usedIsbns = new HashSet<>();
        List<TrendAiService.GeneratedRecommendation> selected = new ArrayList<>();
        for (Long trendId : selectedTrendIds) {
            List<TrendAiService.GeneratedRecommendation> uniqueForTrend = byTrend.getOrDefault(trendId, List.of()).stream()
                .filter(item -> usedIsbns.add(item.book().getIsbn()))
                .limit(properties.booksPerTrend()).toList();
            selected.addAll(uniqueForTrend);
        }
        return selected;
    }
}
