package com.wakebook.book.controller;

import com.wakebook.book.dto.BookSearchResponse;
import com.wakebook.book.dto.PopularBookResponse;
import com.wakebook.book.service.BookService;
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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bookService = mock(BookService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookController(bookService))
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
}
