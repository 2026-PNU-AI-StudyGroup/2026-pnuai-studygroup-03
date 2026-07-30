package com.wakebook.book.service;

import com.wakebook.common.ApiException;
import com.wakebook.common.PageResponse;
import com.wakebook.book.dto.BookDetailResponse;
import com.wakebook.book.dto.BookSearchResponse;
import com.wakebook.book.dto.PopularBookResponse;
import com.wakebook.external.aladin.FakeTableOfContentsProvider;
import com.wakebook.external.library.Data4LibraryProperties;
import com.wakebook.external.library.FakeBookDetailProvider;
import com.wakebook.external.library.FakeBookSearchProvider;
import com.wakebook.external.library.FakeLibraryHoldingProvider;
import com.wakebook.external.library.FakePopularLoanBookProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookServiceTest {

    private FakePopularLoanBookProvider fakeProvider;
    private FakeBookSearchProvider fakeSearchProvider;
    private FakeBookDetailProvider fakeDetailProvider;
    private FakeLibraryHoldingProvider fakeHoldingProvider;
    private FakeTableOfContentsProvider fakeTableOfContentsProvider;
    private BookService bookService;

    @BeforeEach
    void setUp() {
        fakeProvider = new FakePopularLoanBookProvider();
        fakeSearchProvider = new FakeBookSearchProvider();
        fakeDetailProvider = new FakeBookDetailProvider();
        fakeHoldingProvider = new FakeLibraryHoldingProvider();
        fakeTableOfContentsProvider = new FakeTableOfContentsProvider();
        Data4LibraryProperties properties = new Data4LibraryProperties("http://data4library.kr/api", "dummy-key", 12);
        bookService = new BookService(
            fakeProvider, fakeSearchProvider, fakeDetailProvider, fakeHoldingProvider, fakeTableOfContentsProvider, properties
        );
    }

    @Test
    void 인기_도서_목록을_조회한다() {
        PageResponse<PopularBookResponse> result = bookService.getPopularBooks(1, 12, "ALL", "ALL", null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).isbn()).isEqualTo("9788996991342");
        assertThat(result.totalElements()).isEqualTo(89);
        assertThat(result.page()).isEqualTo(1);
    }

    @Test
    void 분야를_KDC_코드로_변환하여_전달한다() {
        bookService.getPopularBooks(1, 12, "문학", "ALL", null);

        assertThat(fakeProvider.lastCriteria().kdc()).isEqualTo("8");
    }

    @Test
    void 성별_코드를_변환하여_전달한다() {
        bookService.getPopularBooks(1, 12, "ALL", "F", null);

        assertThat(fakeProvider.lastCriteria().gender()).isEqualTo("1");
    }

    @Test
    void 지원하지_않는_분야면_예외가_발생한다() {
        assertThatThrownBy(() -> bookService.getPopularBooks(1, 12, "만화", "ALL", null))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void 지원하지_않는_연령대면_예외가_발생한다() {
        assertThatThrownBy(() -> bookService.getPopularBooks(1, 12, "ALL", "ALL", 25))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void size가_최대값을_초과하면_예외가_발생한다() {
        assertThatThrownBy(() -> bookService.getPopularBooks(1, 51, "ALL", "ALL", null))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void 키워드로_도서를_검색한다() {
        PageResponse<BookSearchResponse> result = bookService.searchBooks("심리", 1, 12);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).isbn()).isEqualTo("9788996991342");
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(fakeSearchProvider.lastCriteria().keyword()).isEqualTo("심리");
    }

    @Test
    void 검색_키워드가_비어있으면_예외가_발생한다() {
        assertThatThrownBy(() -> bookService.searchBooks(" ", 1, 12))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void region_없이_도서_상세를_조회하면_소장정보는_비어있다() {
        BookDetailResponse result = bookService.getBookDetail("9788996991342", null);

        assertThat(result.title()).isEqualTo("미움받을 용기");
        assertThat(result.libraries()).isEmpty();
        assertThat(result.availability()).isEqualTo("UNKNOWN");
        assertThat(result.tableOfContents()).containsExactly("트라우마를 부정하라", "모든 고민은 인간관계에서 비롯된다");
        assertThat(fakeTableOfContentsProvider.lastIsbn()).isEqualTo("9788996991342");
    }

    @Test
    void 목차_조회에_실패하면_빈_목록을_반환한다() {
        fakeTableOfContentsProvider.setTableOfContents(List.of());

        BookDetailResponse result = bookService.getBookDetail("9788996991342", null);

        assertThat(result.tableOfContents()).isEmpty();
    }

    @Test
    void region이_있으면_소장_도서관_정보를_함께_조회한다() {
        BookDetailResponse result = bookService.getBookDetail("9788996991342", "21");

        assertThat(fakeHoldingProvider.lastRegion()).isEqualTo("21");
        assertThat(result.libraries()).hasSize(1);
        assertThat(result.libraries().get(0).callNumber()).isEqualTo("189.1-기58ㅁ");
        assertThat(result.availability()).isEqualTo("AVAILABLE");
    }

    @Test
    void 존재하지_않는_도서면_예외가_발생한다() {
        fakeDetailProvider.makeEmpty();

        assertThatThrownBy(() -> bookService.getBookDetail("0000000000000", null))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void isbn이_비어있으면_예외가_발생한다() {
        assertThatThrownBy(() -> bookService.getBookDetail(" ", null))
            .isInstanceOf(ApiException.class);
    }
}
