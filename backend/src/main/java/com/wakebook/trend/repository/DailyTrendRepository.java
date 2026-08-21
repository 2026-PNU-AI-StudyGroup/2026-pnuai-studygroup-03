package com.wakebook.trend.repository;

import com.wakebook.trend.domain.DailyTrend;
import com.wakebook.trend.domain.TrendEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DailyTrendRepository extends JpaRepository<DailyTrend, Long> {
    List<DailyTrend> findByTrendDateAndEligibilityOrderByFinalTrendScoreDesc(LocalDate date, TrendEligibility eligibility);
    long countByTrendDate(LocalDate date);
}
