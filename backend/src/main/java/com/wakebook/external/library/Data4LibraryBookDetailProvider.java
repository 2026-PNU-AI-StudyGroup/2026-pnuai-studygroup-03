package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.dto.BookDetailApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class Data4LibraryBookDetailProvider implements BookDetailProvider {

    private static final Logger log = LoggerFactory.getLogger(Data4LibraryBookDetailProvider.class);

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryBookDetailProvider(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    // 캐시는 FallbackBookDetailProvider에 걸려 있다. 카카오와 알라딘이 모두 부족할 때만 이 API를 호출한다.
    @Override
    public Optional<BookDetail> fetch(String isbn) {
        BookDetailApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/srchDtlList")
                    .queryParam("authKey", authKey)
                    .queryParam("isbn13", isbn)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(BookDetailApiResponse.class);
        } catch (RestClientException e) {
            log.error("정보나루 srchDtlList 호출 실패 (isbn={})", isbn, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "도서 상세 조회에 실패했습니다.");
        }

        if (response == null || response.response() == null) {
            return Optional.empty();
        }
        Data4LibraryErrors.check(response.response().errCode(), response.response().error());
        if (response.response().detail() == null || response.response().detail().isEmpty()) {
            return Optional.empty();
        }

        BookDetailApiResponse.Book book = response.response().detail().get(0).book();
        if (book == null) {
            return Optional.empty();
        }

        return Optional.of(new BookDetail(
            book.isbn13(),
            book.bookname(),
            book.authors(),
            book.publisher(),
            parseYear(book.publicationYear()),
            book.bookImageUrl(),
            book.description()
        ));
    }

    private Integer parseYear(String value) {
        try {
            return value == null ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
