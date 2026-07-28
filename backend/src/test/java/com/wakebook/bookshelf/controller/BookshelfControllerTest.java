package com.wakebook.bookshelf.controller;

import com.wakebook.bookshelf.domain.BookshelfType;
import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfResponse;
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
}
