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

    /**
     * 카탈로그 스캔은 후보군 크기에 따라 최대 40페이지를 연달아 부른다. 정보나루가 느려지는
     * 날엔 그중 한 페이지만 타임아웃나도 몇 분짜리 스캔 전체가 버려졌다(실측 재현됨, 2026-08-22).
     * 대출 순위 조회(Data4LibraryLoanRankingProvider)와 같은 재시도 정책을 적용한다.
     */
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 500;

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
        ItemSearchApiResponse response = fetchPageWithRetry(libraryCode, startDt, endDt, pageNo, pageSize);
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

    /** 한 페이지라도 못 받으면 스캔 전체가 실패하므로, 일시적인 오류는 몇 번 다시 시도해 본다. */
    private ItemSearchApiResponse fetchPageWithRetry(
        String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo, int pageSize
    ) {
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ItemSearchApiResponse response = fetchPage(libraryCode, startDt, endDt, pageNo, pageSize);
                if (response != null && response.response() != null) {
                    if (attempt > 1) {
                        log.info("장서 카탈로그 {}페이지를 {}번째 시도에서 받았다 (libCode={})",
                            pageNo, attempt, libraryCode);
                    }
                    return response;
                }
                lastError = null;
                log.warn("장서 카탈로그 응답이 비어 있다 (libCode={}, pageNo={}, 시도={}/{})",
                    libraryCode, pageNo, attempt, MAX_ATTEMPTS);
            } catch (RestClientException e) {
                lastError = e;
                log.warn("정보나루 itemSrch 호출 실패 (libCode={}, pageNo={}, 시도={}/{}): {}",
                    libraryCode, pageNo, attempt, MAX_ATTEMPTS, e.toString());
            }
            if (attempt < MAX_ATTEMPTS) {
                waitBeforeRetry(attempt);
            }
        }
        if (lastError != null) {
            log.error("장서 카탈로그 {}페이지를 {}번 시도했지만 실패했다 (libCode={})",
                pageNo, MAX_ATTEMPTS, libraryCode, lastError);
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "도서관 장서 조회에 실패했습니다.");
    }

    /** 테스트에서 실제로 기다리지 않도록 package-private으로 둔다. */
    void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("장서 카탈로그 조회가 중단됐습니다.", e);
        }
    }

    /** 테스트에서 HTTP 없이 페이지 응답을 갈아끼울 수 있도록 package-private으로 둔다. */
    ItemSearchApiResponse fetchPage(
        String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo, int pageSize
    ) {
        return restClient.get()
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
