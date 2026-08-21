package com.wakebook.trend.repository;

import com.wakebook.trend.domain.DailyTrendBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface DailyTrendBatchRepository extends JpaRepository<DailyTrendBatch, Long> {
    Optional<DailyTrendBatch> findByRecommendationDateAndLibraryCode(LocalDate date, String libraryCode);
    Optional<DailyTrendBatch> findTopByLibraryCodeAndCompletedAtIsNotNullAndRecommendationDateBetweenOrderByRecommendationDateDesc(
        String libraryCode, LocalDate from, LocalDate to);
}
