package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.dto.ItemSearchApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class Data4LibraryHoldingCatalogProvider implements LibraryHoldingCatalogProvider {

    private static final Logger log = LoggerFactory.getLogger(Data4LibraryHoldingCatalogProvider.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryHoldingCatalogProvider(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    @Override
    public HoldingCatalogResult fetch(
        String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo, int pageSize
    ) {
        ItemSearchApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/itemSrch")
                    .queryParam("authKey", authKey)
                    .queryParam("libCode", libraryCode)
                    .queryParam("startDt", startDt.format(DATE_FORMAT))
                    .queryParam("endDt", endDt.format(DATE_FORMAT))
                    .queryParam("pageNo", pageNo)
                    .queryParam("pageSize", pageSize)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(ItemSearchApiResponse.class);
        } catch (RestClientException e) {
            log.error("정보나루 itemSrch 호출 실패 (libCode={})", libraryCode, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "도서관 장서 조회에 실패했습니다.");
        }

        if (response == null || response.response() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "도서관 장서 조회에 실패했습니다.");
        }

        Data4LibraryErrors.check(response.response().errCode(), response.response().error());
        ItemSearchApiResponse.Response body = response.response();
        List<HoldingCatalogItem> items = body.docs() == null
            ? List.of()
            : body.docs().stream()
                .map(ItemSearchApiResponse.DocWrapper::doc)
                .filter(doc -> doc != null && doc.isbn13() != null && !doc.isbn13().isBlank())
                .map(this::toItem)
                .toList();

        return new HoldingCatalogResult(body.libraryName(), items, body.totalCount());
    }

    private HoldingCatalogItem toItem(ItemSearchApiResponse.Doc doc) {
        ItemSearchApiResponse.CallNumber callNumber = firstCallNumber(doc);
        return new HoldingCatalogItem(
            doc.isbn13().trim(),
            doc.bookname(),
            doc.authors(),
            doc.bookImageUrl(),
            doc.className(),
            buildCallNumber(doc, callNumber),
            callNumber == null ? null : callNumber.shelfLocationName()
        );
    }

    private ItemSearchApiResponse.CallNumber firstCallNumber(ItemSearchApiResponse.Doc doc) {
        if (doc.callNumbers() == null || doc.callNumbers().isEmpty()) {
            return null;
        }
        return doc.callNumbers().get(0).callNumber();
    }

    private String buildCallNumber(ItemSearchApiResponse.Doc doc, ItemSearchApiResponse.CallNumber callNumber) {
        if (doc.classNo() == null || doc.classNo().isBlank()) {
            return null;
        }
        if (callNumber == null || callNumber.bookCode() == null || callNumber.bookCode().isBlank()) {
            return doc.classNo().trim();
        }
        return doc.classNo().trim() + "-" + callNumber.bookCode().trim();
    }
}
