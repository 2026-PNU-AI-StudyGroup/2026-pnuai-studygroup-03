package com.wakebook.book.controller;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.dto.BookSearchResponse;
import com.wakebook.book.dto.PopularBookResponse;
import com.wakebook.book.service.BookService;
import com.wakebook.book.service.HiddenBookService;
import com.wakebook.common.ApiException;
import com.wakebook.common.PageResponse;
import com.wakebook.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookControllerTest {

    private BookService bookService;
    private HiddenBookService hiddenBookService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bookService = mock(BookService.class);
        hiddenBookService = mock(HiddenBookService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookController(bookService, hiddenBookService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void popularBooksUseDefaultsAndReturnTheCommonResponse() throws Exception {
        PageResponse<PopularBookResponse> response = new PageResponse<>(
                List.of(new PopularBookResponse(
                        "9788996991342",
                        "미움받을 용기",
                        "기시미 이치로",
                        "https://example.com/cover.jpg",
                        1,
                        1284
                )),
                1,
                1,
                1
        );
        when(bookService.getPopularBooks(1, 12, "ALL", "ALL", null))
                .thenReturn(response);

        mockMvc.perform(get("/books/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("요청이 완료되었습니다."))
                .andExpect(jsonPath("$.data.content[0].isbn").value("9788996991342"))
                .andExpect(jsonPath("$.data.content[0].rank").value(1))
                .andExpect(jsonPath("$.data.content[0].loanCount").value(1284))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(bookService).getPopularBooks(1, 12, "ALL", "ALL", null);
    }

    @Test
    void missingSearchKeywordReturnsTheCommonValidationError() throws Exception {
        mockMvc.perform(get("/books/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message").value("요청값 누락 또는 형식 오류입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void invalidPageTypeReturnsTheCommonValidationError() throws Exception {
        mockMvc.perform(get("/books/popular").queryParam("page", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message").value("요청값 누락 또는 형식 오류입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void externalApiFailureKeepsItsStatusAndErrorCode() throws Exception {
        when(bookService.searchBooks("심리", 1, 12))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "BOOK_002",
                        "도서 검색에 실패했습니다."
                ));

        mockMvc.perform(get("/books/search").queryParam("keyword", "심리"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BOOK_002"))
                .andExpect(jsonPath("$.message").value("도서 검색에 실패했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void searchReturnsTheCommonResponse() throws Exception {
        PageResponse<BookSearchResponse> response = new PageResponse<>(
                List.of(new BookSearchResponse(
                        "9788996991342",
                        "미움받을 용기",
                        "기시미 이치로",
                        "https://example.com/cover.jpg"
                )),
                1,
                1,
                1
        );
        when(bookService.searchBooks("심리", 1, 12)).thenReturn(response);

        mockMvc.perform(get("/books/search").queryParam("keyword", "심리"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("요청이 완료되었습니다."))
                .andExpect(jsonPath("$.data.content[0].isbn").value("9788996991342"))
                .andExpect(jsonPath("$.data.page").value(1));

        verify(bookService).searchBooks("심리", 1, 12);
    }

    @Test
    void todayBookReturnsTheHiddenBookResponse() throws Exception {
        HiddenBook hiddenBook = new HiddenBook(
                "9788960867450",
                "121018",
                "부산광역시 금정도서관",
                "관계에도 연습이 필요합니다",
                "박상미",
                "https://example.com/cover.jpg",
                1,
                80,
                "나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요.",
                List.of("인간관계", "심리")
        );
        when(hiddenBookService.getTodayBook("121018")).thenReturn(hiddenBook);

        mockMvc.perform(get("/books/today").queryParam("libraryCode", "121018"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isbn").value("9788960867450"))
                .andExpect(jsonPath("$.data.reason").value("나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요."))
                .andExpect(jsonPath("$.data.keywords[0]").value("인간관계"));
    }

    @Test
    void todayBookWithNoCandidatesReturnsBookNotFoundError() throws Exception {
        when(hiddenBookService.getTodayBook("121018"))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "오늘의 잠자는 책 후보가 없습니다."));

        mockMvc.perform(get("/books/today").queryParam("libraryCode", "121018"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BOOK_001"));
    }

    @Test
    void randomBookReturnsTheHiddenBookResponse() throws Exception {
        HiddenBook hiddenBook = new HiddenBook(
                "9788960867450",
                "121018",
                "부산광역시 금정도서관",
                "관계에도 연습이 필요합니다",
                "박상미",
                "https://example.com/cover.jpg",
                1,
                80,
                "나를 지키면서 타인과 건강하게 연결되는 연습을 만나 보세요.",
                List.of("인간관계", "심리")
        );
        when(hiddenBookService.getRandomBook("121018")).thenReturn(hiddenBook);

        mockMvc.perform(get("/books/random").queryParam("libraryCode", "121018"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isbn").value("9788960867450"));
    }
}
