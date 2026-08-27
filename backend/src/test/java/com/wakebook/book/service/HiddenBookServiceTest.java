package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.dto.HiddenBookResponse;
import com.wakebook.book.dto.LibrarySummaryResponse;
import com.wakebook.common.PageResponse;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HiddenBookServiceTest {

    private static final String LIBRARY_CODE = "121018";

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    @Mock
    private HiddenBookReasonService reasonService;

    private HiddenBookService hiddenBookService;

    @BeforeEach
    void setUp() {
        hiddenBookService = new HiddenBookService(hiddenBookRepository, reasonService);
    }

    @Test
    void 오늘의_책은_날짜에_따라_해당_도서관_후보군_중_하나를_결정적으로_고른다() {
        List<HiddenBook> pool = List.of(hiddenBook("111"), hiddenBook("222"), hiddenBook("333"));
        when(hiddenBookRepository.findAllByLibraryCodeOrderByIdAsc(LIBRARY_CODE)).thenReturn(pool);

        when(reasonService.ensureReason(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HiddenBook result = hiddenBookService.getTodayBook(LIBRARY_CODE);

        long epochDay = LocalDate.now(ZoneId.of("Asia/Seoul")).toEpochDay();
        int expectedIndex = (int) (epochDay % pool.size());
        assertThat(result.getIsbn()).isEqualTo(pool.get(expectedIndex).getIsbn());
    }

    @Test
    void 후보군이_없으면_오늘의_책_조회시_BOOK_001_예외() {
        when(hiddenBookRepository.findAllByLibraryCodeOrderByIdAsc(LIBRARY_CODE)).thenReturn(List.of());

        assertThatThrownBy(() -> hiddenBookService.getTodayBook(LIBRARY_CODE))
                .isInstanceOf(ApiException.class)
                .hasMessage("오늘의 잠자는 책 후보가 없습니다.");
    }

    @Test
    void libraryCode가_없으면_VALIDATION_001_예외() {
        assertThatThrownBy(() -> hiddenBookService.getTodayBook(" "))
                .isInstanceOf(ApiException.class)
                .hasMessage("libraryCode는 필수입니다.");
    }

    @Test
    void 랜덤_발견은_해당_도서관의_랜덤_한권을_그대로_반환한다() {
        HiddenBook picked = hiddenBook("999");
        when(hiddenBookRepository.findRandomOneByLibraryCode(LIBRARY_CODE)).thenReturn(Optional.of(picked));

        when(reasonService.ensureReason(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HiddenBook result = hiddenBookService.getRandomBook(LIBRARY_CODE);

        assertThat(result.getIsbn()).isEqualTo("999");
    }

    @Test
    void 후보군이_없으면_랜덤_발견시_BOOK_001_예외() {
        when(hiddenBookRepository.findRandomOneByLibraryCode(LIBRARY_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hiddenBookService.getRandomBook(LIBRARY_CODE))
                .isInstanceOf(ApiException.class)
                .hasMessage("우연히 발견할 잠자는 책 후보가 없습니다.");
    }

    @Test
    void 잠자는_도서_목록은_저장된_후보군을_그대로_페이지로_반환한다() {
        List<HiddenBook> pool = List.of(hiddenBook("111"), hiddenBook("222"));
        when(hiddenBookRepository.findByLibraryCode(eq(LIBRARY_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(pool, PageRequest.of(0, 12), 30));

        PageResponse<HiddenBookResponse> result = hiddenBookService.getHiddenBooks(LIBRARY_CODE, 1, 12);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).isbn()).isEqualTo("111");
        assertThat(result.totalElements()).isEqualTo(30);
        assertThat(result.totalPages()).isEqualTo(3);
        // 목록에서는 AI를 부르지 않는다. 추천 이유가 비어 있어도 그대로 내려 준다.
        verifyNoInteractions(reasonService);
    }

    @Test
    void 도서관_목록은_후보군이_등록된_도서관만_그대로_반환한다() {
        List<LibrarySummaryResponse> summaries = List.of(
                new LibrarySummaryResponse(LIBRARY_CODE, "부산광역시 금정도서관", HiddenBookSource.CSV_UPLOAD, 24)
        );
        when(hiddenBookRepository.findLibrarySummaries()).thenReturn(summaries);

        assertThat(hiddenBookService.getLibraries()).isEqualTo(summaries);
    }

    private HiddenBook hiddenBook(String isbn) {
        return new HiddenBook(
                isbn, LIBRARY_CODE, "부산광역시 금정도서관", "제목-" + isbn, "저자",
                "https://example.com/cover.jpg", 1, 80, "추천 이유", List.of("키워드")
        );
    }
}
