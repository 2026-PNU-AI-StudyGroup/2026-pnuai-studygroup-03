package com.wakebook.book.service;

import com.wakebook.common.ApiException;
import com.wakebook.common.PageResponse;
import com.wakebook.book.dto.BookSearchResponse;
import com.wakebook.book.dto.PopularBookResponse;
import com.wakebook.external.library.Data4LibraryProperties;
import com.wakebook.external.library.FakeBookSearchProvider;
import com.wakebook.external.library.FakePopularLoanBookProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookServiceTest {

    private FakePopularLoanBookProvider fakeProvider;
    private FakeBookSearchProvider fakeSearchProvider;
    private BookService bookService;

    @BeforeEach
    void setUp() {
        fakeProvider = new FakePopularLoanBookProvider();
        fakeSearchProvider = new FakeBookSearchProvider();
        Data4LibraryProperties properties = new Data4LibraryProperties("http://data4library.kr/api", "dummy-key", 12);
        bookService = new BookService(fakeProvider, fakeSearchProvider, properties);
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
}
