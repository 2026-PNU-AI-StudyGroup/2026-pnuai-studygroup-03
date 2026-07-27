package com.wakebook.bookshelf.controller;

import com.wakebook.bookshelf.domain.BookshelfType;
import com.wakebook.bookshelf.dto.BookshelfResponse;
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
}
