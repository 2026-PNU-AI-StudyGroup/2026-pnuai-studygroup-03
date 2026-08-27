package com.wakebook.trend.service;

import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.trend.domain.*;
import com.wakebook.trend.dto.TrendBatchResponse;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import com.wakebook.trend.support.TrendProperties;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
public class TrendRefreshService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final UserRepository userRepository;
    private final DailyTrendBatchRepository batchRepository;
    private final HiddenBookRepository hiddenBookRepository;
    private final TrendBatchStateService stateService;
    private final TrendBatchWorker worker;
    private final TrendProperties properties;
    public TrendRefreshService(UserRepository userRepository, DailyTrendBatchRepository batchRepository,
        HiddenBookRepository hiddenBookRepository,
        TrendBatchStateService stateService, TrendBatchWorker worker, TrendProperties properties) {
        this.userRepository = userRepository; this.batchRepository = batchRepository;
        this.hiddenBookRepository = hiddenBookRepository;
        this.stateService = stateService; this.worker = worker; this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RefreshResult request(String subject, boolean force) {
        User user = requireUser(subject);
        String libraryCode = requireLibraryCode(user);
        if (hiddenBookRepository.findTopByLibraryCode(libraryCode).isEmpty())
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "트렌드와 연결할 잠자는 도서 후보가 없습니다.");
        LocalDate today = LocalDate.now(SEOUL);
        DailyTrendBatch existing = batchRepository.findByRecommendationDateAndLibraryCode(today, libraryCode).orElse(null);
        if (existing != null && (existing.getStatus() == TrendBatchStatus.PENDING || existing.getStatus() == TrendBatchStatus.PROCESSING))
            throw stateService.running(existing);
        if (existing != null && existing.getStatus() == TrendBatchStatus.COMPLETED && !force)
            return new RefreshResult(TrendBatchResponse.from(existing), false);
        if (force && existing != null && existing.getCompletedAt() != null) enforceCooldown(existing);
        TrendBatchStateService.QueueResult queued = stateService.createOrQueue(today, libraryCode);
        if (!queued.created()) throw stateService.running(queued.batch());
        startGeneration(queued.batch());
        return new RefreshResult(TrendBatchResponse.from(queued.batch()), true);
    }

    @Transactional(readOnly = true)
    public TrendBatchResponse getOwned(String subject, Long batchId) {
        User user = requireUser(subject);
        return TrendBatchResponse.from(stateService.owned(batchId, requireLibraryCode(user)));
    }

    public void requestScheduled(String libraryCode, LocalDate date) {
        DailyTrendBatch existing = batchRepository.findByRecommendationDateAndLibraryCode(date, libraryCode).orElse(null);
        if (existing != null && existing.getStatus() == TrendBatchStatus.COMPLETED) return;
        if (existing != null && (existing.getStatus() == TrendBatchStatus.PENDING || existing.getStatus() == TrendBatchStatus.PROCESSING)) return;
        TrendBatchStateService.QueueResult queued = stateService.createOrQueue(date, libraryCode);
        if (queued.created()) startGeneration(queued.batch());
    }

    /** 애플리케이션 재시작 전에 중단된 PENDING/PROCESSING 배치도 다시 실행한다. */
    public void requestOnStartup(String libraryCode, LocalDate date) {
        DailyTrendBatch existing = batchRepository.findByRecommendationDateAndLibraryCode(date, libraryCode).orElse(null);
        if (existing != null && existing.getStatus() == TrendBatchStatus.COMPLETED) return;
        TrendBatchStateService.QueueResult queued = stateService.createOrQueue(date, libraryCode);
        if (queued.created()) startGeneration(queued.batch());
    }

    /**
     * 비동기 큐(최대 12건 동시 처리·대기)가 가득 차면 TaskRejectedException이 여기서 즉시 던져진다.
     * 배치는 이미 REQUIRES_NEW로 PENDING 저장됐으므로, 그대로 두면 이후 재요청이 "이미 생성 중"으로
     * 오판해 영원히 막힌다. FAILED로 기록해 재시도할 수 있게 한다.
     */
    private void startGeneration(DailyTrendBatch batch) {
        try {
            worker.generate(batch.getId());
        } catch (TaskRejectedException e) {
            stateService.fail(batch.getId(), "TREND_006");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TREND_006", "작업이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void enforceCooldown(DailyTrendBatch batch) {
        LocalDateTime next = batch.getCompletedAt().plusMinutes(properties.forceRefreshCooldownMinutes());
        long seconds = Duration.between(LocalDateTime.now(), next).toSeconds();
        if (seconds > 0) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TREND_004",
            "트렌드 추천은 %d분 후 다시 생성할 수 있습니다.".formatted(properties.forceRefreshCooldownMinutes()),
            Map.of("retryAfterSeconds", seconds));
    }
    private User requireUser(String subject) {
        try { return userRepository.findById(Long.parseLong(subject)).orElseThrow(AuthenticationRequiredException::new); }
        catch (NumberFormatException e) { throw new AuthenticationRequiredException(); }
    }
    private String requireLibraryCode(User user) {
        if (user.getLibraryCode() == null || user.getLibraryCode().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "사서 계정에 도서관 코드가 필요합니다.");
        return user.getLibraryCode();
    }
    public record RefreshResult(TrendBatchResponse response, boolean accepted) {}
}
