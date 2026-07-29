package com.wakebook.bookshelf.controller;

import com.wakebook.bookshelf.domain.BookshelfType;
import com.wakebook.bookshelf.domain.ReadingStatus;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookshelfControllerTest {

    @Test
    void usesTheAuthenticatedJwtSubjectAndReturnsTheCommonResponse() {
        BookshelfService bookshelfService = mock(BookshelfService.class);
        BookshelfController controller = new BookshelfController(bookshelfService);
        BookshelfResponse shelf = new BookshelfResponse(
                1L,
                "읽고 싶은 책",
                BookshelfType.DEFAULT,
                0,
                List.of()
        );
        when(bookshelfService.getBookshelves("12")).thenReturn(List.of(shelf));
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("12")
                .build();

        ResponseEntity<ApiResponse<List<BookshelfResponse>>> response =
                controller.getBookshelves(jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("책장 목록을 조회했습니다.");
        assertThat(response.getBody().data()).containsExactly(shelf);
        verify(bookshelfService).getBookshelves("12");
    }

    @Test
    void createsACollectionForTheAuthenticatedJwtSubject() {
        BookshelfService bookshelfService = mock(BookshelfService.class);
        BookshelfController controller = new BookshelfController(bookshelfService);
        CreateBookshelfRequest request =
                new CreateBookshelfRequest("마음을 돌보는 책", "천천히 읽고 싶은 책 모음");
        CreateBookshelfResponse created = new CreateBookshelfResponse(
                2L,
                "마음을 돌보는 책",
                "천천히 읽고 싶은 책 모음",
                BookshelfType.CUSTOM
        );
        when(bookshelfService.createBookshelf("12", request)).thenReturn(created);
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("12")
                .build();

        ResponseEntity<ApiResponse<CreateBookshelfResponse>> response =
                controller.createBookshelf(jwt, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("컬렉션이 생성되었습니다.");
        assertThat(response.getBody().data()).isEqualTo(created);
        verify(bookshelfService).createBookshelf("12", request);
    }

    @Test
    void addsABookForTheAuthenticatedJwtSubject() {
        BookshelfService bookshelfService = mock(BookshelfService.class);
        BookshelfController controller = new BookshelfController(bookshelfService);
        AddBookshelfBookRequest request =
                new AddBookshelfBookRequest("9788960867450", ReadingStatus.WISH);
        BookshelfBookResponse saved = new BookshelfBookResponse(
                101L,
                "9788960867450",
                "관계에도 연습이 필요합니다",
                ReadingStatus.WISH,
                "https://example.com/cover.jpg"
        );
        when(bookshelfService.addBook("12", 1L, request)).thenReturn(saved);

        ResponseEntity<ApiResponse<BookshelfBookResponse>> response =
                controller.addBook(jwt("12"), 1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("책장에 도서가 저장되었습니다.");
        assertThat(response.getBody().data()).isEqualTo(saved);
        verify(bookshelfService).addBook("12", 1L, request);
    }

    @Test
    void updatesReadingStatusForTheAuthenticatedJwtSubject() {
        BookshelfService bookshelfService = mock(BookshelfService.class);
        BookshelfController controller = new BookshelfController(bookshelfService);
        UpdateReadingStatusRequest request =
                new UpdateReadingStatusRequest(ReadingStatus.READING);
        BookshelfBookResponse updated = new BookshelfBookResponse(
                101L,
                "9788960867450",
                "관계에도 연습이 필요합니다",
                ReadingStatus.READING,
                "https://example.com/cover.jpg"
        );
        when(bookshelfService.updateReadingStatus("12", 1L, 101L, request))
                .thenReturn(updated);

        ResponseEntity<ApiResponse<BookshelfBookResponse>> response =
                controller.updateReadingStatus(jwt("12"), 1L, 101L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("읽기 상태가 변경되었습니다.");
        assertThat(response.getBody().data()).isEqualTo(updated);
        verify(bookshelfService).updateReadingStatus("12", 1L, 101L, request);
    }

    @Test
    void updatesACollectionForTheAuthenticatedJwtSubject() {
        BookshelfService bookshelfService = mock(BookshelfService.class);
        BookshelfController controller = new BookshelfController(bookshelfService);
        UpdateBookshelfRequest request =
                new UpdateBookshelfRequest("천천히 읽을 책", "이번 달에 읽을 책");
        UpdateBookshelfResponse updated = new UpdateBookshelfResponse(
                2L,
                "천천히 읽을 책",
                "이번 달에 읽을 책",
                BookshelfType.CUSTOM
        );
        when(bookshelfService.updateBookshelf("12", 2L, request)).thenReturn(updated);
        Jwt jwt = jwt("12");

        ResponseEntity<ApiResponse<UpdateBookshelfResponse>> response =
                controller.updateBookshelf(jwt, 2L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("컬렉션이 수정되었습니다.");
        assertThat(response.getBody().data()).isEqualTo(updated);
        verify(bookshelfService).updateBookshelf("12", 2L, request);
    }

    @Test
    void deletesACollectionForTheAuthenticatedJwtSubject() {
        BookshelfService bookshelfService = mock(BookshelfService.class);
        BookshelfController controller = new BookshelfController(bookshelfService);
        Jwt jwt = jwt("12");

        ResponseEntity<ApiResponse<Void>> response =
                controller.deleteBookshelf(jwt, 2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("컬렉션이 삭제되었습니다.");
        assertThat(response.getBody().data()).isNull();
        verify(bookshelfService).deleteBookshelf("12", 2L);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(subject)
                .build();
    }
}
