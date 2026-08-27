package com.wakebook.trend.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.external.library.Data4LibraryHoldingLookup;
import com.wakebook.trend.domain.DailyTrend;
import com.wakebook.trend.domain.DailyTrendBatch;
import com.wakebook.trend.domain.TrendEligibility;
import com.wakebook.trend.domain.TrendValidationStatus;
import com.wakebook.trend.repository.DailyTrendBatchRepository;
import com.wakebook.trend.support.TrendProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendBatchWorkerTest {

    private static final String TARGET_LIBRARY = "121020";

    @Mock private DailyTrendBatchRepository batchRepository;
    @Mock private HiddenBookRepository hiddenBookRepository;
    @Mock private DailyTrendPreparationService preparationService;
    @Mock private TrendAiService aiService;
    @Mock private TrendBookMatcher bookMatcher;
    @Mock private TrendRecommendationWriter writer;
    @Mock private TrendBatchStateService stateService;
    @Mock private Data4LibraryHoldingLookup holdingLookup;

    @Test
    void 대출가능_필터에서_탈락한_도서는_차순위_후보로_보충한다() {
        TrendProperties properties = new TrendProperties(20, 1, 2, .7, .7, .6, 10, 3, 30);
        TrendBatchWorker worker = new TrendBatchWorker(
            batchRepository, hiddenBookRepository, preparationService, aiService, bookMatcher,
            writer, stateService, properties, holdingLookup
        );
        DailyTrendBatch batch = new DailyTrendBatch(LocalDate.now(), TARGET_LIBRARY);
        DailyTrend trend = trend(1L);
        List<HiddenBook> books = List.of(
            book("9780000000001"), book("9780000000002"),
            book("9780000000003"), book("9780000000004")
        );
        List<TrendAiService.GeneratedRecommendation> recommendations = List.of(
            recommendation(trend, books.get(0), 1, .95),
            recommendation(trend, books.get(1), 2, .90),
            recommendation(trend, books.get(2), 3, .85),
            recommendation(trend, books.get(3), 4, .80)
        );

        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(hiddenBookRepository.findAll()).thenReturn(books);
        when(preparationService.prepare(batch.getRecommendationDate())).thenReturn(List.of(trend));
        when(bookMatcher.rankForLibrary(anyList(), eq(books), eq(10))).thenReturn(List.of(trend));
        when(aiService.recommend(eq(List.of(trend)), eq(books), eq(15))).thenReturn(recommendations);
        when(holdingLookup.findAvailability(books.get(0).getIsbn(), TARGET_LIBRARY)).thenReturn(false);
        when(holdingLookup.findAvailability(books.get(1).getIsbn(), TARGET_LIBRARY)).thenReturn(false);
        when(holdingLookup.findAvailability(books.get(2).getIsbn(), TARGET_LIBRARY)).thenReturn(true);
        when(holdingLookup.findAvailability(books.get(3).getIsbn(), TARGET_LIBRARY)).thenReturn(true);

        worker.generate(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrendAiService.GeneratedRecommendation>> captor = ArgumentCaptor.forClass(List.class);
        verify(writer).replace(eq(1L), captor.capture());
        assertThat(captor.getValue())
            .extracting(item -> item.book().getIsbn())
            .containsExactly("9780000000003", "9780000000004");
        assertThat(captor.getValue())
            .extracting(TrendAiService.GeneratedRecommendation::order)
            .containsExactly(1, 2);
        verify(stateService, never()).fail(eq(1L), eq("AI_001"));
    }

    private TrendAiService.GeneratedRecommendation recommendation(
        DailyTrend trend, HiddenBook book, int order, double score
    ) {
        return new TrendAiService.GeneratedRecommendation(
            trend.getId(), "환율을 읽는 책", book, "환율의 배경을 이해하도록 돕습니다.", order, score
        );
    }

    private HiddenBook book(String isbn) {
        return new HiddenBook(
            isbn, "999999", "다른 도서관", "환율의 이해", "저자", null,
            0, 90, null, List.of("경제", "환율")
        );
    }

    private DailyTrend trend(Long id) {
        DailyTrend trend = new DailyTrend(
            LocalDate.now(), "GOOGLE_TRENDS", "key", "환율 급등", "환율급등",
            "원·달러 환율 변동", .9, 1, "10K+", LocalDateTime.now(), null, "[]", "[]",
            "원·달러 환율의 변동성이 커지고 있습니다.", TrendEligibility.ELIGIBLE, .9,
            TrendValidationStatus.CONFIRMED, 1.5, .9,
            LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );
        setId(trend, id);
        return trend;
    }

    private void setId(DailyTrend trend, Long id) {
        try {
            var field = DailyTrend.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(trend, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
