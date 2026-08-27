package com.wakebook.trend.service;

import com.wakebook.common.ApiException;
import com.wakebook.trend.domain.*;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * 같은 (date, libraryCode) 배치를 두 요청이 동시에 처음 만들려고 하면, 둘 다 조회 시점엔
     * 기존 행이 없어서 각자 새로 만들다가 하나는 유니크 제약(uk_daily_trend_batches_date_library)에
     * 걸린다. 그 경우 예외를 던지는 대신 먼저 커밋된 쪽을 그대로 돌려주고, created=false로 표시해
     * 호출부가 다시 실행 요청을 하지 않도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QueueResult createOrQueue(LocalDate date, String libraryCode) {
        DailyTrendBatch existing = repository.findByRecommendationDateAndLibraryCode(date, libraryCode).orElse(null);
        if (existing != null) {
            existing.queueAgain();
            return new QueueResult(repository.save(existing), true);
        }
        try {
            return new QueueResult(repository.save(new DailyTrendBatch(date, libraryCode)), true);
        } catch (DataIntegrityViolationException e) {
            DailyTrendBatch winner = repository.findByRecommendationDateAndLibraryCode(date, libraryCode)
                .orElseThrow(() -> e);
            return new QueueResult(winner, false);
        }
    }

    public record QueueResult(DailyTrendBatch batch, boolean created) {}
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
