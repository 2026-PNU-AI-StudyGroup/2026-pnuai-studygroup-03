package com.wakebook.bookshelf.controller;

import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.service.BookshelfService;
import com.wakebook.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/bookshelves")
public class BookshelfController {

    private final BookshelfService bookshelfService;

    public BookshelfController(BookshelfService bookshelfService) {
        this.bookshelfService = bookshelfService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BookshelfResponse>>> getBookshelves(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<BookshelfResponse> response = bookshelfService.getBookshelves(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("책장 목록을 조회했습니다.", response));
    }
}
