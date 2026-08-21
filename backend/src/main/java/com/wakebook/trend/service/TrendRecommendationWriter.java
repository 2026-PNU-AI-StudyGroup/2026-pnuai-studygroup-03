package com.wakebook.trend.service;

import com.wakebook.common.ApiException;
import com.wakebook.trend.domain.*;
import com.wakebook.trend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrendRecommendationWriter {
    private final DailyTrendBatchRepository batchRepository;
    private final DailyTrendRepository trendRepository;
    private final DailyTrendRecommendationRepository recommendationRepository;
    public TrendRecommendationWriter(DailyTrendBatchRepository batchRepository, DailyTrendRepository trendRepository,
        DailyTrendRecommendationRepository recommendationRepository) {
        this.batchRepository = batchRepository; this.trendRepository = trendRepository;
        this.recommendationRepository = recommendationRepository;
    }
    @Transactional
    public void replace(Long batchId, List<TrendAiService.GeneratedRecommendation> generated) {
        DailyTrendBatch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TREND_001", "트렌드 생성 작업을 찾을 수 없습니다."));
        Map<Long, DailyTrend> trends = trendRepository.findAllById(generated.stream()
            .map(TrendAiService.GeneratedRecommendation::trendId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(DailyTrend::getId, Function.identity()));
        recommendationRepository.deleteByBatchId(batchId);
        List<DailyTrendRecommendation> entities = generated.stream().filter(g -> trends.containsKey(g.trendId()))
            .map(g -> new DailyTrendRecommendation(batch, trends.get(g.trendId()), batch.getLibraryCode(),
                g.book().getIsbn(), g.book().getTitle(), g.book().getAuthor(), g.book().getCover(),
                g.book().getLoanCount(), g.recommendationTitle(), g.reason(), g.order(), g.matchScore())).toList();
        recommendationRepository.saveAll(entities);
        batch.complete((int) entities.stream().map(e -> e.getDailyTrend().getId()).distinct().count());
    }
}
