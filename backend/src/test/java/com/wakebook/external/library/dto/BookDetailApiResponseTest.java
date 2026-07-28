package com.wakebook.external.library.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class BookDetailApiResponseTest {

    /**
     * 정보나루 srchDtlList는 detail을 객체가 아니라 배열로 내려준다.
     * 실제 응답 형태와 다르게 파싱하면 조용히 502로만 나타나 발견이 늦어지므로 고정해둔다.
     */
    @Test
    void detail이_배열_형태여도_파싱된다() {
        String json = """
            {
              "response": {
                "detail": [
                  {
                    "book": {
                      "bookname": "미움받을 용기",
                      "authors": "기시미 이치로",
                      "publisher": "인플루엔셜",
                      "publication_year": "2014",
                      "isbn13": "9788996991342",
                      "description": "아들러 심리학을 바탕으로...",
                      "bookImageURL": "https://example.com/cover.jpg"
                    }
                  }
                ]
              }
            }
            """;

        BookDetailApiResponse response = new ObjectMapper().readValue(json, BookDetailApiResponse.class);

        BookDetailApiResponse.Book book = response.response().detail().get(0).book();
        assertThat(book.bookname()).isEqualTo("미움받을 용기");
        assertThat(book.isbn13()).isEqualTo("9788996991342");
    }
}
