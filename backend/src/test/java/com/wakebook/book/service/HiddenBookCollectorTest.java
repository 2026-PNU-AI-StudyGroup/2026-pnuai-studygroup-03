package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.support.HiddenBookProperties;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.FakeBookDetailProvider;
import com.wakebook.external.library.FakeLibraryHoldingCatalogProvider;
import com.wakebook.external.library.FakeLibraryLoanRankingProvider;
import com.wakebook.external.library.HoldingCatalogItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HiddenBookCollectorTest {

    private static final String LIBRARY_CODE = "121020";
    private static final String QUALITY_DESCRIPTION =
        "나를 지키면서 타인과 건강하게 연결되는 연습을 다루는 책으로, 관계에 지친 이들에게 실질적인 도움을 준다.";

    @Mock
    private HiddenBookPoolWriter poolWriter;

    @Mock
    private HiddenBookJobService jobService;

    private FakeBookDetailProvider bookDetailProvider;
    private FakeLibraryHoldingCatalogProvider catalogProvider;
    private FakeLibraryLoanRankingProvider rankingProvider;
    private HiddenBookCollector collector;

    @BeforeEach
    void setUp() {
        bookDetailProvider = new FakeBookDetailProvider();
        catalogProvider = new FakeLibraryHoldingCatalogProvider();
        rankingProvider = new FakeLibraryLoanRankingProvider();
        collector = new HiddenBookCollector(
            bookDetailProvider,
            catalogProvider,
            rankingProvider,
            poolWriter,
            jobService,
            new HiddenBookProperties(2, 30, 12)
        );
    }

    @Test
    void CSV_경로는_대출건수가_기준_이하인_후보만_저장한다() {
        bookDetailProvider.setDetail(detail("9788960867450"));
        List<HiddenBookCandidate> rows = List.of(
            HiddenBookCandidate.fromCsv("9788960867450", "관계에도 연습이 필요합니다", "박상미", 1),
            HiddenBookCandidate.fromCsv("9999999999999", "대출많은책", "아무개", 100)
        );

        collector.collectFromCsv(1L, LIBRARY_CODE, "부산광역시 금정도서관", rows);

        List<HiddenBook> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getIsbn()).isEqualTo("9788960867450");
        assertThat(saved.get(0).getSource()).isEqualTo(HiddenBookSource.CSV_UPLOAD);
        assertThat(saved.get(0).getLoanCount()).isEqualTo(1);
        // 후보군 산출 단계에서는 AI를 부르지 않으므로 추천 이유가 비어 있고, 소개글만 저장된다.
        assertThat(saved.get(0).hasReason()).isFalse();
        assertThat(saved.get(0).getDescription()).isEqualTo(QUALITY_DESCRIPTION);
    }

    @Test
    void API_경로는_대출_순위에_든_장서를_후보에서_제외한다() {
        bookDetailProvider.setDetail(detail("9788960867450"));
        catalogProvider.setItems(List.of(
            holding("9788960867450", "관계에도 연습이 필요합니다"),
            holding("9791111111111", "많이 대출된 책")
        ));
        rankingProvider.setRankedIsbns(java.util.Set.of("9791111111111"));

        collector.collectFromLibraryApi(2L, LIBRARY_CODE);

        List<HiddenBook> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getIsbn()).isEqualTo("9788960867450");
        assertThat(saved.get(0).getSource()).isEqualTo(HiddenBookSource.LIBRARY_API);
        assertThat(saved.get(0).getLibraryName()).isEqualTo("부산광역시 강서도서관");
        assertThat(saved.get(0).getCallNumber()).isEqualTo("813.7-박51ㄱ");
        assertThat(saved.get(0).getShelfName()).isEqualTo("종합자료실");
        // KDC 분류명으로 AI 없이 기본 키워드를 만든다.
        assertThat(saved.get(0).getKeywords()).containsExactly("문학", "한국문학", "소설");
    }

    @Test
    void 소개글이_없어_품질검증을_통과하지_못하면_기존_후보군을_지우지_않는다() {
        bookDetailProvider.setDetail(new BookDetail(
            "9788960867450", "제목", "저자", "출판사", 2020, "https://example.com/c.jpg", "짧음"
        ));

        collector.collectFromCsv(3L, LIBRARY_CODE, "부산광역시 금정도서관",
            List.of(HiddenBookCandidate.fromCsv("9788960867450", "제목", "저자", 0)));

        // 0건으로 교체하면 멀쩡히 쓰던 기존 후보군까지 사라지므로 아예 교체하지 않는다.
        verify(poolWriter, org.mockito.Mockito.never()).replace(any(), any());
        verify(jobService).succeed(eq(3L), any(), eq(0), any());
    }

    @Test
    void 대출_순위_밖_장서가_없으면_작업을_0건으로_마친다() {
        catalogProvider.setItems(List.of(holding("9791111111111", "많이 대출된 책")));
        rankingProvider.setRankedIsbns(java.util.Set.of("9791111111111"));

        collector.collectFromLibraryApi(4L, LIBRARY_CODE);

        verify(jobService).succeed(eq(4L), eq("부산광역시 강서도서관"), eq(0), any());
        verify(poolWriter, org.mockito.Mockito.never()).replace(any(), any());
    }

    @Test
    void 한_권의_외부_상세_조회가_실패해도_다음_책을_저장하고_작업을_성공으로_마친다() {
        String failedIsbn = "9780000000001";
        String savedIsbn = "9788960867450";
        bookDetailProvider.failForIsbn(failedIsbn);
        bookDetailProvider.setDetailForIsbn(savedIsbn, detail(savedIsbn));

        collector.collectFromCsv(5L, LIBRARY_CODE, "부산광역시 금정도서관", List.of(
            HiddenBookCandidate.fromCsv(failedIsbn, "조회 실패 도서", "저자", 0),
            HiddenBookCandidate.fromCsv(savedIsbn, "관계에도 연습이 필요합니다", "박상미", 1)
        ));

        List<HiddenBook> saved = captureSaved();
        assertThat(saved).extracting(HiddenBook::getIsbn).containsExactly(savedIsbn);
        verify(jobService).succeed(eq(5L), eq("부산광역시 금정도서관"), eq(1), any());
        verify(jobService, never()).fail(eq(5L), any());
    }

    private List<HiddenBook> captureSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HiddenBook>> captor = ArgumentCaptor.forClass(List.class);
        verify(poolWriter).replace(eq(LIBRARY_CODE), captor.capture());
        return captor.getValue();
    }

    private BookDetail detail(String isbn) {
        return new BookDetail(
            isbn, "관계에도 연습이 필요합니다", "박상미", "빌리버튼", 2018,
            "https://example.com/cover.jpg", QUALITY_DESCRIPTION
        );
    }

    private HoldingCatalogItem holding(String isbn, String title) {
        return new HoldingCatalogItem(
            isbn, title, "박상미", "https://example.com/cover.jpg",
            "문학 > 한국문학 > 소설", "813.7-박51ㄱ", "종합자료실"
        );
    }

}
