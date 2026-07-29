package com.wakebook.bookshelf.controller;

import com.wakebook.bookshelf.dto.AddBookshelfBookRequest;
import com.wakebook.bookshelf.dto.BookshelfBookResponse;
import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfResponse;
import com.wakebook.bookshelf.dto.UpdateBookshelfRequest;
import com.wakebook.bookshelf.dto.UpdateBookshelfResponse;
import com.wakebook.bookshelf.dto.UpdateReadingStatusRequest;
import com.wakebook.bookshelf.service.BookshelfService;
import com.wakebook.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/{shelfId}/books")
    public ResponseEntity<ApiResponse<BookshelfBookResponse>> addBook(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shelfId,
            @Valid @RequestBody AddBookshelfBookRequest request
    ) {
        BookshelfBookResponse response =
                bookshelfService.addBook(jwt.getSubject(), shelfId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("책장에 도서가 저장되었습니다.", response));
    }

    @PatchMapping("/{shelfId}/books/{bookId}")
    public ResponseEntity<ApiResponse<BookshelfBookResponse>> updateReadingStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shelfId,
            @PathVariable Long bookId,
            @Valid @RequestBody UpdateReadingStatusRequest request
    ) {
        BookshelfBookResponse response = bookshelfService.updateReadingStatus(
                jwt.getSubject(),
                shelfId,
                bookId,
                request
        );
        return ResponseEntity.ok(
                ApiResponse.success("읽기 상태가 변경되었습니다.", response)
        );
    }

    @PatchMapping("/{shelfId}")
    public ResponseEntity<ApiResponse<UpdateBookshelfResponse>> updateBookshelf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shelfId,
            @Valid @RequestBody UpdateBookshelfRequest request
    ) {
        UpdateBookshelfResponse response =
                bookshelfService.updateBookshelf(jwt.getSubject(), shelfId, request);
        return ResponseEntity.ok(ApiResponse.success("컬렉션이 수정되었습니다.", response));
    }

    @DeleteMapping("/{shelfId}")
    public ResponseEntity<ApiResponse<Void>> deleteBookshelf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shelfId
    ) {
        bookshelfService.deleteBookshelf(jwt.getSubject(), shelfId);
        return ResponseEntity.ok(
                ApiResponse.<Void>success("컬렉션이 삭제되었습니다.", null)
        );
    }
}
