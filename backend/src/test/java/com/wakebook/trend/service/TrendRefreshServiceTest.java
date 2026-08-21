package com.wakebook.trend.service;

import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.trend.domain.DailyTrendBatch;
import com.wakebook.trend.domain.TrendBatchStatus;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import com.wakebook.trend.support.TrendProperties;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

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
        when(stateService.createOrQueue(today, "121018")).thenReturn(queued);
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
