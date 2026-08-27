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
    // TrendAiService가 트렌드별로 서버 shortlist에 넣는 최대 후보 수와 맞춘다.
    private static final int BACKFILL_CANDIDATES_PER_TREND = 15;
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
            List<TrendAiService.GeneratedRecommendation> matched = aiService.recommend(
                libraryCandidates, allBooks,
                Math.max(properties.booksPerTrend(), BACKFILL_CANDIDATES_PER_TREND));
            List<TrendAiService.GeneratedRecommendation> generated = selectAvailableRecommendations(
                libraryCandidates, matched, batch.getLibraryCode());
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
     * AI가 정렬한 후보를 순서대로 확인하고, 도서관 필터에서 탈락한 후보는 권수에 포함하지 않는다.
     * 목표 권수를 채울 때까지만 차순위 후보를 확인하며, 한 트렌드의 후보가 전부 탈락하면 다음
     * 순위 트렌드로 넘어간다. 도서관 자체 후보는 기존과 같이 외부 소장 조회 없이 사용한다.
     */
    private List<TrendAiService.GeneratedRecommendation> selectAvailableRecommendations(
        List<DailyTrend> trends, List<TrendAiService.GeneratedRecommendation> generated, String libraryCode
    ) {
        Map<Long, DailyTrend> trendsById = trends.stream()
            .collect(Collectors.toMap(DailyTrend::getId, Function.identity()));
        Map<Long, List<TrendAiService.GeneratedRecommendation>> byTrend = generated.stream()
            .collect(Collectors.groupingBy(TrendAiService.GeneratedRecommendation::trendId));
        List<Long> rankedTrendIds = byTrend.entrySet().stream()
            .sorted(Comparator.<Map.Entry<Long, List<TrendAiService.GeneratedRecommendation>>>comparingDouble(entry -> {
                DailyTrend trend = trendsById.get(entry.getKey());
                double match = entry.getValue().stream()
                    .mapToDouble(TrendAiService.GeneratedRecommendation::matchScore).max().orElse(0);
                return (trend == null ? 0 : trend.getFinalTrendScore()) * .45 + match * .55;
            }).reversed())
            .map(Map.Entry::getKey).toList();

        Set<String> usedIsbns = new HashSet<>();
        List<TrendAiService.GeneratedRecommendation> selected = new ArrayList<>();
        int selectedTrendCount = 0;
        for (Long trendId : rankedTrendIds) {
            int displayOrder = 0;
            for (TrendAiService.GeneratedRecommendation item : byTrend.getOrDefault(trendId, List.of())) {
                if (displayOrder >= properties.booksPerTrend()) break;
                String isbn = item.book().getIsbn();
                if (usedIsbns.contains(isbn) || !isAvailableAtLibrary(item, libraryCode)) continue;
                usedIsbns.add(isbn);
                selected.add(new TrendAiService.GeneratedRecommendation(
                    item.trendId(), item.recommendationTitle(), item.book(), item.reason(),
                    ++displayOrder, item.matchScore()));
            }
            if (displayOrder > 0 && ++selectedTrendCount >= properties.topTrendCount()) break;
        }
        return selected;
    }

    private boolean isAvailableAtLibrary(TrendAiService.GeneratedRecommendation item, String libraryCode) {
        return libraryCode.equals(item.book().getLibraryCode())
            || Boolean.TRUE.equals(holdingLookup.findAvailability(item.book().getIsbn(), libraryCode));
    }
}
