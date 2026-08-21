package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookJobStatus;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookCollectionLockRepository;
import com.wakebook.book.repository.HiddenBookJobRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 작업 상태만 따로 짧은 트랜잭션으로 기록한다. 산출 작업 자체는 몇 분씩 걸리므로
 * 같은 트랜잭션에 묶으면 진행 상태를 조회할 수 없다.
 */
@Service
public class HiddenBookJobService {

    private static final List<HiddenBookJobStatus> ACTIVE_STATUSES =
        List.of(HiddenBookJobStatus.PENDING, HiddenBookJobStatus.RUNNING);

    /** 정보나루 일일 호출 한도를 지키기 위한 제한. 후보군은 자주 바뀌지 않아 재산출할 이유도 적다. */
    private static final int COOLDOWN_DAYS = 7;
    private static final int MAX_JOBS_PER_USER_PER_DAY = 3;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HiddenBookJobRepository jobRepository;
    private final HiddenBookCollectionLockRepository collectionLockRepository;
    private final Clock clock;

    @Autowired
    public HiddenBookJobService(
        HiddenBookJobRepository jobRepository,
        HiddenBookCollectionLockRepository collectionLockRepository
    ) {
        this(jobRepository, collectionLockRepository, Clock.systemUTC());
    }

    HiddenBookJobService(
        HiddenBookJobRepository jobRepository,
        HiddenBookCollectionLockRepository collectionLockRepository,
        Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.collectionLockRepository = collectionLockRepository;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HiddenBookJob create(
        String libraryCode, String libraryName, HiddenBookSource source, Long requestedBy, boolean applyLimits
    ) {
        collectionLockRepository.lock(libraryCode);
        if (!jobRepository.findByLibraryCodeAndStatusIn(libraryCode, ACTIVE_STATUSES).isEmpty()) {
            throw new ApiException(
                HttpStatus.CONFLICT, "JOB_001", "이 도서관의 후보군을 이미 만들고 있습니다. 잠시 후 다시 시도해 주세요."
            );
        }
        LocalDateTime requestedAt = seoulDateTime(clock.instant());
        // 사서가 자기 도서관 CSV를 올리는 건 스스로 정확한 데이터를 넣는 일이므로 제한하지 않는다.
        if (applyLimits) {
            ensureNotRecentlyCollected(libraryCode, requestedAt);
            ensureDailyQuota(requestedBy, requestedAt);
        }
        return jobRepository.save(new HiddenBookJob(
            libraryCode, libraryName, source, requestedBy, requestedAt
        ));
    }

    private void ensureNotRecentlyCollected(String libraryCode, LocalDateTime requestedAt) {
        jobRepository.findTopByLibraryCodeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            libraryCode, HiddenBookJobStatus.SUCCEEDED, requestedAt.minusDays(COOLDOWN_DAYS)
        ).ifPresent(job -> {
            throw new ApiException(
                HttpStatus.CONFLICT, "JOB_003",
                "이 도서관의 잠자는 도서는 최근에 만들어 두었습니다. 목록에서 바로 선택해 주세요."
            );
        });
    }

    private void ensureDailyQuota(Long requestedBy, LocalDateTime requestedAt) {
        if (requestedBy == null) {
            return;
        }
        LocalDateTime todayStart = requestedAt.toLocalDate().atStartOfDay();
        long today = jobRepository.countByRequestedByAndCreatedAtGreaterThanEqual(requestedBy, todayStart);
        if (today >= MAX_JOBS_PER_USER_PER_DAY) {
            throw new ApiException(
                HttpStatus.TOO_MANY_REQUESTS, "JOB_004",
                "하루에 만들 수 있는 도서관 수(%d곳)를 모두 사용했습니다. 내일 다시 시도해 주세요."
                    .formatted(MAX_JOBS_PER_USER_PER_DAY)
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(Long jobId, int totalCandidates) {
        LocalDateTime updatedAt = seoulDateTime(clock.instant());
        jobRepository.findById(jobId).ifPresent(job -> job.start(totalCandidates, updatedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(Long jobId, int processedCount, int savedCount) {
        LocalDateTime updatedAt = seoulDateTime(clock.instant());
        jobRepository.findById(jobId).ifPresent(job -> job.progress(processedCount, savedCount, updatedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long jobId, String libraryName, int savedCount, String message) {
        LocalDateTime updatedAt = seoulDateTime(clock.instant());
        jobRepository.findById(jobId).ifPresent(job -> job.succeed(libraryName, savedCount, message, updatedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, String message) {
        LocalDateTime updatedAt = seoulDateTime(clock.instant());
        jobRepository.findById(jobId).ifPresent(job -> job.fail(message, updatedAt));
    }

    @Transactional(readOnly = true)
    public HiddenBookJob get(Long jobId) {
        return jobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_002", "작업을 찾을 수 없습니다."));
    }

    /** 작업 상태와 실패 메시지는 요청자에게만 보여 준다. */
    @Transactional(readOnly = true)
    public HiddenBookJob getForRequester(Long jobId, String authenticatedUserId) {
        HiddenBookJob job = get(jobId);
        if (!Objects.equals(job.getRequestedBy(), parseUserId(authenticatedUserId))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_002", "이 작업을 조회할 권한이 없습니다.");
        }
        return job;
    }

    @Transactional(readOnly = true)
    public HiddenBookJob findLatestByLibrary(String libraryCode) {
        return jobRepository.findTopByLibraryCodeOrderByCreatedAtDesc(libraryCode).orElse(null);
    }

    private Long parseUserId(String authenticatedUserId) {
        try {
            long userId = Long.parseLong(authenticatedUserId);
            if (userId <= 0) {
                throw new AuthenticationRequiredException();
            }
            return userId;
        } catch (NumberFormatException e) {
            throw new AuthenticationRequiredException();
        }
    }

    private static LocalDateTime seoulDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, SEOUL);
    }
}
