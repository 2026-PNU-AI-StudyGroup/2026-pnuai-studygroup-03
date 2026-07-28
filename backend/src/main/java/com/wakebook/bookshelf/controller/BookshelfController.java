package com.wakebook.bookshelf.controller;

import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfResponse;
import com.wakebook.bookshelf.service.BookshelfService;
import com.wakebook.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    public ResponseEntity<ApiResponse<CreateBookshelfResponse>> createBookshelf(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookshelfRequest request
    ) {
        CreateBookshelfResponse response =
                bookshelfService.createBookshelf(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("컬렉션이 생성되었습니다.", response));
    }
}
