package com.wakebook.book.controller;

import com.wakebook.book.dto.BookDetailResponse;
import com.wakebook.book.dto.BookSearchResponse;
import com.wakebook.book.dto.HiddenBookResponse;
import com.wakebook.book.dto.PopularBookResponse;
import com.wakebook.book.service.BookService;
import com.wakebook.book.service.HiddenBookService;
import com.wakebook.common.PageResponse;
import com.wakebook.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;
    private final HiddenBookService hiddenBookService;

    public BookController(BookService bookService, HiddenBookService hiddenBookService) {
        this.bookService = bookService;
        this.hiddenBookService = hiddenBookService;
    }

    @GetMapping("/popular")
    public ApiResponse<PageResponse<PopularBookResponse>> getPopularBooks(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int size,
        @RequestParam(defaultValue = "ALL") String category,
        @RequestParam(defaultValue = "ALL") String gender,
        @RequestParam(required = false) Integer age
    ) {
        return ApiResponse.success(bookService.getPopularBooks(page, size, category, gender, age));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<BookSearchResponse>> searchBooks(
        @RequestParam String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.success(bookService.searchBooks(keyword, page, size));
    }

    @GetMapping("/today")
    public ApiResponse<HiddenBookResponse> getTodayBook(@RequestParam String libraryCode) {
        return ApiResponse.success(HiddenBookResponse.from(hiddenBookService.getTodayBook(libraryCode)));
    }

    @GetMapping("/random")
    public ApiResponse<HiddenBookResponse> getRandomBook(@RequestParam String libraryCode) {
        return ApiResponse.success(HiddenBookResponse.from(hiddenBookService.getRandomBook(libraryCode)));
    }

    @GetMapping("/{isbn}")
    public ApiResponse<BookDetailResponse> getBookDetail(
        @PathVariable String isbn,
        @RequestParam(required = false) String region
    ) {
        return ApiResponse.success(bookService.getBookDetail(isbn, region));
    }
}
