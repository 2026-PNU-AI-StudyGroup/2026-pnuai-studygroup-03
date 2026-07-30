package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.dto.LoanItemSearchApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class Data4LibraryPopularBookProvider implements PopularLoanBookProvider {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryPopularBookProvider(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    @Override
    public PopularLoanBookResult fetch(PopularLoanBookCriteria criteria) {
        LoanItemSearchApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/loanItemSrch")
                        .queryParam("authKey", authKey)
                        .queryParam("startDt", criteria.startDt().format(DATE_FORMAT))
                        .queryParam("endDt", criteria.endDt().format(DATE_FORMAT))
                        .queryParam("pageNo", criteria.pageNo())
                        .queryParam("pageSize", criteria.pageSize())
                        .queryParam("format", "json");
                    if (criteria.gender() != null) {
                        uriBuilder.queryParam("gender", criteria.gender());
                    }
                    if (criteria.age() != null) {
                        uriBuilder.queryParam("age", criteria.age());
                    }
                    if (criteria.kdc() != null) {
                        uriBuilder.queryParam("kdc", criteria.kdc());
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(LoanItemSearchApiResponse.class);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "인기 도서 조회에 실패했습니다.");
        }

        if (response == null || response.response() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "인기 도서 조회에 실패했습니다.");
        }

        LoanItemSearchApiResponse.Response body = response.response();
        List<PopularLoanBookItem> items = body.docs() == null
            ? List.of()
            : body.docs().stream()
                .map(LoanItemSearchApiResponse.DocWrapper::doc)
                .map(this::toItem)
                .toList();

        return new PopularLoanBookResult(items, body.numFound());
    }

    private PopularLoanBookItem toItem(LoanItemSearchApiResponse.Doc doc) {
        return new PopularLoanBookItem(
            doc.isbn13(),
            doc.bookname(),
            doc.authors(),
            doc.bookImageUrl(),
            parseIntSafe(doc.ranking()),
            parseLongSafe(doc.loanCount())
        );
    }

    private int parseIntSafe(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLongSafe(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
