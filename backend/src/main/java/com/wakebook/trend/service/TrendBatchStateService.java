package com.wakebook.trend.service;

import com.wakebook.common.ApiException;
import com.wakebook.trend.domain.*;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
public class TrendBatchStateService {
    private final DailyTrendBatchRepository repository;
    public TrendBatchStateService(DailyTrendBatchRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyTrendBatch createOrQueue(LocalDate date, String libraryCode) {
        DailyTrendBatch batch = repository.findByRecommendationDateAndLibraryCode(date, libraryCode)
            .orElseGet(() -> new DailyTrendBatch(date, libraryCode));
        if (batch.getId() != null) batch.queueAgain();
        return repository.save(batch);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(Long id) { repository.findById(id).ifPresent(DailyTrendBatch::start); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id, String code) { repository.findById(id).ifPresent(batch -> batch.fail(code)); }
    @Transactional(readOnly = true)
    public DailyTrendBatch owned(Long id, String libraryCode) {
        return repository.findById(id).filter(b -> b.getLibraryCode().equals(libraryCode))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TREND_001", "트렌드 생성 작업을 찾을 수 없습니다."));
    }
    public ApiException running(DailyTrendBatch batch) {
        return new ApiException(HttpStatus.CONFLICT, "TREND_003", "이미 트렌드 추천을 생성하고 있습니다.",
            Map.of("batchId", batch.getId()));
    }
}
