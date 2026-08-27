package com.wakebook.trend.service;

import com.wakebook.trend.domain.DailyTrendBatch;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrendBatchStateServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 22);
    private static final String LIBRARY_CODE = "121018";

    @Test
    void 존재하지_않는_배치는_새로_만들고_created를_true로_돌려준다() {
        DailyTrendBatchRepository repository = mock(DailyTrendBatchRepository.class);
        when(repository.findByRecommendationDateAndLibraryCode(DATE, LIBRARY_CODE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TrendBatchStateService service = new TrendBatchStateService(repository);

        TrendBatchStateService.QueueResult result = service.createOrQueue(DATE, LIBRARY_CODE);

        assertThat(result.created()).isTrue();
        assertThat(result.batch().getLibraryCode()).isEqualTo(LIBRARY_CODE);
    }

    @Test
    void 동시_요청이_같은_배치를_먼저_만들었으면_유니크_제약_예외를_잡아_먼저_커밋된_행을_돌려준다() {
        // 두 요청이 동시에 조회 시점엔 배치가 없어서 각자 새로 저장을 시도하는 상황을 흉내낸다.
        // (recommendation_date, library_code) 유니크 제약(uk_daily_trend_batches_date_library)에
        // 이 요청이 두 번째로 걸린 경우, 예외 대신 먼저 커밋된 배치를 재조회해 돌려줘야 한다.
        DailyTrendBatchRepository repository = mock(DailyTrendBatchRepository.class);
        DailyTrendBatch winner = new DailyTrendBatch(DATE, LIBRARY_CODE);
        when(repository.findByRecommendationDateAndLibraryCode(DATE, LIBRARY_CODE))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(winner));
        when(repository.save(any(DailyTrendBatch.class)))
            .thenThrow(new DataIntegrityViolationException("uk_daily_trend_batches_date_library"));
        TrendBatchStateService service = new TrendBatchStateService(repository);

        TrendBatchStateService.QueueResult result = service.createOrQueue(DATE, LIBRARY_CODE);

        assertThat(result.created()).isFalse();
        assertThat(result.batch()).isSameAs(winner);
    }

    @Test
    void 유니크_제약_예외인데_재조회해도_없으면_원래_예외를_던진다() {
        DailyTrendBatchRepository repository = mock(DailyTrendBatchRepository.class);
        DataIntegrityViolationException original = new DataIntegrityViolationException("boom");
        when(repository.findByRecommendationDateAndLibraryCode(DATE, LIBRARY_CODE)).thenReturn(Optional.empty());
        when(repository.save(any(DailyTrendBatch.class))).thenThrow(original);
        TrendBatchStateService service = new TrendBatchStateService(repository);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createOrQueue(DATE, LIBRARY_CODE))
            .isSameAs(original);
    }
}
