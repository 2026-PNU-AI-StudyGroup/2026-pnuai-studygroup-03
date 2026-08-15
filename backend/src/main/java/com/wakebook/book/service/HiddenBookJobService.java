package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookJobStatus;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookJobRepository;
import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    private final HiddenBookJobRepository jobRepository;

    public HiddenBookJobService(HiddenBookJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HiddenBookJob create(
        String libraryCode, String libraryName, HiddenBookSource source, Long requestedBy, boolean applyLimits
    ) {
        if (!jobRepository.findByLibraryCodeAndStatusIn(libraryCode, ACTIVE_STATUSES).isEmpty()) {
            throw new ApiException(
                HttpStatus.CONFLICT, "JOB_001", "이 도서관의 후보군을 이미 만들고 있습니다. 잠시 후 다시 시도해 주세요."
            );
        }
        // 사서가 자기 도서관 CSV를 올리는 건 스스로 정확한 데이터를 넣는 일이므로 제한하지 않는다.
        if (applyLimits) {
            ensureNotRecentlyCollected(libraryCode);
            ensureDailyQuota(requestedBy);
        }
        return jobRepository.save(new HiddenBookJob(libraryCode, libraryName, source, requestedBy));
    }

    private void ensureNotRecentlyCollected(String libraryCode) {
        jobRepository.findTopByLibraryCodeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            libraryCode, HiddenBookJobStatus.SUCCEEDED, LocalDateTime.now().minusDays(COOLDOWN_DAYS)
        ).ifPresent(job -> {
            throw new ApiException(
                HttpStatus.CONFLICT, "JOB_003",
                "이 도서관의 잠자는 도서는 최근에 만들어 두었습니다. 목록에서 바로 선택해 주세요."
            );
        });
    }

    private void ensureDailyQuota(Long requestedBy) {
        if (requestedBy == null) {
            return;
        }
        long today = jobRepository.countByRequestedByAndCreatedAtAfter(
            requestedBy, LocalDateTime.now().minusDays(1)
        );
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
        jobRepository.findById(jobId).ifPresent(job -> job.start(totalCandidates));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(Long jobId, int processedCount, int savedCount) {
        jobRepository.findById(jobId).ifPresent(job -> job.progress(processedCount, savedCount));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long jobId, String libraryName, int savedCount, String message) {
        jobRepository.findById(jobId).ifPresent(job -> job.succeed(libraryName, savedCount, message));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, String message) {
        jobRepository.findById(jobId).ifPresent(job -> job.fail(message));
    }

    @Transactional(readOnly = true)
    public HiddenBookJob get(Long jobId) {
        return jobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_002", "작업을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public HiddenBookJob findLatestByLibrary(String libraryCode) {
        return jobRepository.findTopByLibraryCodeOrderByCreatedAtDesc(libraryCode).orElse(null);
    }
}
