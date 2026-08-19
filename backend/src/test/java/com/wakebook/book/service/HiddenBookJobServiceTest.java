package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookJobStatus;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookJobRepository;
import com.wakebook.book.repository.HiddenBookCollectionLockRepository;
import com.wakebook.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 후보군 산출 1회는 정보나루를 수십 번 호출한다. 일일 한도(IP 미등록 시 500건)를 지키려면
 * 재산출과 사용자별 요청 수를 막아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class HiddenBookJobServiceTest {

    private static final String LIBRARY_CODE = "121020";

    @Mock
    private HiddenBookJobRepository jobRepository;
    @Mock
    private HiddenBookCollectionLockRepository collectionLockRepository;

    private HiddenBookJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new HiddenBookJobService(jobRepository, collectionLockRepository);
        lenient().when(jobRepository.findByLibraryCodeAndStatusIn(any(), any())).thenReturn(List.of());
        lenient().when(jobRepository.findTopByLibraryCodeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 산출_중인_도서관이면_JOB_001_예외() {
        when(jobRepository.findByLibraryCodeAndStatusIn(eq(LIBRARY_CODE), any()))
            .thenReturn(List.of(job()));

        assertThatThrownBy(() -> create(1L))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("이미 만들고 있습니다");
    }

    @Test
    void 최근에_산출한_도서관이면_JOB_003_예외() {
        when(jobRepository.findTopByLibraryCodeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            eq(LIBRARY_CODE), eq(HiddenBookJobStatus.SUCCEEDED), any(LocalDateTime.class)
        )).thenReturn(Optional.of(job()));

        assertThatThrownBy(() -> create(1L))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("최근에 만들어 두었습니다");
    }

    @Test
    void 하루_산출_횟수를_넘기면_JOB_004_예외() {
        when(jobRepository.countByRequestedByAndCreatedAtGreaterThanEqual(eq(1L), any(LocalDateTime.class)))
            .thenReturn(3L);

        assertThatThrownBy(() -> create(1L))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("하루에 만들 수 있는");
    }

    @Test
    void 일일_한도는_최근_24시간이_아닌_한국_시간_자정부터_센다() {
        create(1L);
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(jobRepository).countByRequestedByAndCreatedAtGreaterThanEqual(eq(1L), startCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay());
    }

    @Test
    void 제한을_적용하지_않는_CSV_업로드는_쿨다운과_일일_횟수를_확인하지_않는다() {
        HiddenBookJob created = jobService.create(
            LIBRARY_CODE, "부산광역시 강서도서관", HiddenBookSource.CSV_UPLOAD, 1L, false
        );

        assertThat(created.getLibraryCode()).isEqualTo(LIBRARY_CODE);
        assertThat(created.getStatus()).isEqualTo(HiddenBookJobStatus.PENDING);
        verify(jobRepository, never())
            .findTopByLibraryCodeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(any(), any(), any());
        verify(jobRepository, never()).countByRequestedByAndCreatedAtGreaterThanEqual(any(), any());
    }

    @Test
    void 작업_생성_전에_도서관별_DB_잠금을_획득한다() {
        create(1L);

        verify(collectionLockRepository).lock(LIBRARY_CODE);
    }

    @Test
    void 요청자는_자신이_만든_작업만_조회할_수_있다() {
        HiddenBookJob job = job();
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThat(jobService.getForRequester(1L, "1")).isSameAs(job);
    }

    @Test
    void 다른_사용자의_작업_조회는_AUTH_002_예외() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job()));

        assertThatThrownBy(() -> jobService.getForRequester(1L, "2"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("조회할 권한");
    }

    private HiddenBookJob create(Long requestedBy) {
        return jobService.create(LIBRARY_CODE, null, HiddenBookSource.LIBRARY_API, requestedBy, true);
    }

    private HiddenBookJob job() {
        return new HiddenBookJob(LIBRARY_CODE, "부산광역시 강서도서관", HiddenBookSource.LIBRARY_API, 1L);
    }
}
