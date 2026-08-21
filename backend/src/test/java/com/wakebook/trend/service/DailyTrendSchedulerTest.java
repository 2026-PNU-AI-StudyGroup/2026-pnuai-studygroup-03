package com.wakebook.trend.service;

import com.wakebook.book.repository.HiddenBookRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.*;

class DailyTrendSchedulerTest {
    @Test
    void requestsTodayForEveryLibraryOnStartup() {
        HiddenBookRepository repository = mock(HiddenBookRepository.class);
        TrendRefreshService refreshService = mock(TrendRefreshService.class);
        when(repository.findDistinctLibraryCodes()).thenReturn(List.of("121018", "121020"));
        DailyTrendScheduler scheduler = new DailyTrendScheduler(repository, refreshService);

        scheduler.generateMissingTodayOnStartup();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        verify(refreshService).requestOnStartup("121018", today);
        verify(refreshService).requestOnStartup("121020", today);
    }

    @Test
    void oneLibraryFailureDoesNotPreventOtherLibraries() {
        HiddenBookRepository repository = mock(HiddenBookRepository.class);
        TrendRefreshService refreshService = mock(TrendRefreshService.class);
        when(repository.findDistinctLibraryCodes()).thenReturn(List.of("broken", "121018"));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        doThrow(new IllegalStateException("boom")).when(refreshService).requestScheduled("broken", today);
        DailyTrendScheduler scheduler = new DailyTrendScheduler(repository, refreshService);

        scheduler.generateDaily();

        verify(refreshService).requestScheduled("121018", today);
    }
}
