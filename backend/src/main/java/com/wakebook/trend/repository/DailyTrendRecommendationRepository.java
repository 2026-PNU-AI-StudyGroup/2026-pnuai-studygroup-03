package com.wakebook.trend.repository;

import com.wakebook.trend.domain.DailyTrendRecommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DailyTrendRecommendationRepository extends JpaRepository<DailyTrendRecommendation, Long> {
    @EntityGraph(attributePaths = {"dailyTrend", "batch"})
    List<DailyTrendRecommendation> findByBatchIdOrderByDailyTrendFinalTrendScoreDescDisplayOrderAsc(Long batchId);
    void deleteByBatchId(Long batchId);
}
