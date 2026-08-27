package com.wakebook.trend.service;

import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.trend.domain.DailyTrendBatch;
import com.wakebook.trend.domain.TrendBatchStatus;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import com.wakebook.trend.support.TrendProperties;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrendRefreshServiceTest {
    @Test
    void startupRequeuesBatchInterruptedWhileProcessing() {
        DailyTrendBatchRepository batchRepository = mock(DailyTrendBatchRepository.class);
        TrendBatchStateService stateService = mock(TrendBatchStateService.class);
        TrendBatchWorker worker = mock(TrendBatchWorker.class);
        DailyTrendBatch interrupted = mock(DailyTrendBatch.class);
        DailyTrendBatch queued = mock(DailyTrendBatch.class);
        LocalDate today = LocalDate.of(2026, 8, 22);
        when(interrupted.getStatus()).thenReturn(TrendBatchStatus.PROCESSING);
        when(batchRepository.findByRecommendationDateAndLibraryCode(today, "121018"))
            .thenReturn(Optional.of(interrupted));
        when(stateService.createOrQueue(today, "121018"))
            .thenReturn(new TrendBatchStateService.QueueResult(queued, true));
        when(queued.getId()).thenReturn(42L);
        TrendRefreshService service = new TrendRefreshService(
            mock(UserRepository.class), batchRepository, mock(HiddenBookRepository.class), stateService, worker,
            new TrendProperties(20, 5, 2, .70, .70, .60, 10, 3, 30)
        );

        service.requestOnStartup("121018", today);

        verify(stateService).createOrQueue(today, "121018");
        verify(worker).generate(42L);
    }

    @Test
    void startupSkipsGenerationWhenConcurrentRequestAlreadyWonRaceOnCreate() {
        DailyTrendBatchRepository batchRepository = mock(DailyTrendBatchRepository.class);
        TrendBatchStateService stateService = mock(TrendBatchStateService.class);
        TrendBatchWorker worker = mock(TrendBatchWorker.class);
        DailyTrendBatch winner = mock(DailyTrendBatch.class);
        LocalDate today = LocalDate.of(2026, 8, 22);
        when(batchRepository.findByRecommendationDateAndLibraryCode(today, "121018")).thenReturn(Optional.empty());
        when(stateService.createOrQueue(today, "121018"))
            .thenReturn(new TrendBatchStateService.QueueResult(winner, false));
        TrendRefreshService service = new TrendRefreshService(
            mock(UserRepository.class), batchRepository, mock(HiddenBookRepository.class), stateService, worker,
            new TrendProperties(20, 5, 2, .70, .70, .60, 10, 3, 30)
        );

        service.requestOnStartup("121018", today);

        verifyNoInteractions(worker);
    }

    @Test
    void 큐가_가득차_비동기_실행이_거절되면_배치를_실패로_기록하고_503을_반환한다() {
        DailyTrendBatchRepository batchRepository = mock(DailyTrendBatchRepository.class);
        TrendBatchStateService stateService = mock(TrendBatchStateService.class);
        TrendBatchWorker worker = mock(TrendBatchWorker.class);
        DailyTrendBatch queued = mock(DailyTrendBatch.class);
        LocalDate today = LocalDate.of(2026, 8, 22);
        when(batchRepository.findByRecommendationDateAndLibraryCode(today, "121018")).thenReturn(Optional.empty());
        when(stateService.createOrQueue(today, "121018"))
            .thenReturn(new TrendBatchStateService.QueueResult(queued, true));
        when(queued.getId()).thenReturn(42L);
        doThrow(new TaskRejectedException("queue full")).when(worker).generate(42L);
        TrendRefreshService service = new TrendRefreshService(
            mock(UserRepository.class), batchRepository, mock(HiddenBookRepository.class), stateService, worker,
            new TrendProperties(20, 5, 2, .70, .70, .60, 10, 3, 30)
        );

        assertThatThrownBy(() -> service.requestOnStartup("121018", today))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(stateService).fail(42L, "TREND_006");
    }

    @Test
    void startupKeepsCompletedBatchWithoutRegeneration() {
        DailyTrendBatchRepository batchRepository = mock(DailyTrendBatchRepository.class);
        TrendBatchStateService stateService = mock(TrendBatchStateService.class);
        TrendBatchWorker worker = mock(TrendBatchWorker.class);
        DailyTrendBatch completed = mock(DailyTrendBatch.class);
        LocalDate today = LocalDate.of(2026, 8, 22);
        when(completed.getStatus()).thenReturn(TrendBatchStatus.COMPLETED);
        when(batchRepository.findByRecommendationDateAndLibraryCode(today, "121018"))
            .thenReturn(Optional.of(completed));
        TrendRefreshService service = new TrendRefreshService(
            mock(UserRepository.class), batchRepository, mock(HiddenBookRepository.class), stateService, worker,
            new TrendProperties(20, 5, 2, .70, .70, .60, 10, 3, 30)
        );

        service.requestOnStartup("121018", today);

        verifyNoInteractions(stateService, worker);
    }
}
