package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.dto.BookSearchApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class Data4LibraryBookSearchProvider implements BookSearchProvider {

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryBookSearchProvider(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    @Override
    public BookSearchResult search(BookSearchCriteria criteria) {
        BookSearchApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/srchBooks")
                    .queryParam("authKey", authKey)
                    .queryParam("keyword", criteria.keyword())
                    .queryParam("pageNo", criteria.pageNo())
                    .queryParam("pageSize", criteria.pageSize())
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(BookSearchApiResponse.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "도서 검색에 실패했습니다.");
        }

        if (response == null || response.response() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "도서 검색에 실패했습니다.");
        }

        Data4LibraryErrors.check(response.response().errCode(), response.response().error());
        BookSearchApiResponse.Response body = response.response();
        List<BookSearchItem> items = body.docs() == null
            ? List.of()
            : body.docs().stream()
                .map(BookSearchApiResponse.DocWrapper::doc)
                .map(this::toItem)
                .toList();

        return new BookSearchResult(items, body.totalCount());
    }

    private BookSearchItem toItem(BookSearchApiResponse.Doc doc) {
        return new BookSearchItem(doc.isbn13(), doc.bookname(), doc.authors(), doc.bookImageUrl());
    }
}
