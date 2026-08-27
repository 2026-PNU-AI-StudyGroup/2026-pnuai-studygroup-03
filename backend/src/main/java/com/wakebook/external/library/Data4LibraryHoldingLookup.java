package com.wakebook.external.library;

import com.wakebook.common.config.CacheConfig;
import com.wakebook.external.library.dto.BookExistApiResponse;
import com.wakebook.external.library.dto.ItemSearchApiResponse;
import com.wakebook.external.library.dto.LibSrchByBookApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * 소장 도서관 조회의 개별 정보나루 호출. 도서 상세 한 번에 `1 + 2N`(도서관 수 N)회가 나가서
 * 일일 한도(IP 미등록 시 500건)를 가장 빠르게 소진하는 지점이다.
 *
 * 캐시 프록시를 타야 하므로 호출부(Data4LibraryHoldingProvider)와 다른 빈으로 분리했다.
 * 같은 클래스 안에서 호출하면 @Cacheable이 적용되지 않는다.
 */
@Component
public class Data4LibraryHoldingLookup {

    private static final Logger log = LoggerFactory.getLogger(Data4LibraryHoldingLookup.class);

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryHoldingLookup(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    /** 어느 도서관이 소장하는지는 자주 바뀌지 않아 길게 캐싱한다. */
    @Cacheable(cacheNames = CacheConfig.BOOK_HOLDING_LIBRARIES, key = "#isbn + ':' + #region")
    public List<LibSrchByBookApiResponse.Lib> findLibraries(String isbn, String region) {
        LibSrchByBookApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/libSrchByBook")
                    .queryParam("authKey", authKey)
                    .queryParam("isbn", isbn)
                    .queryParam("region", region)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(LibSrchByBookApiResponse.class);
        } catch (RestClientException e) {
            log.error("정보나루 libSrchByBook 호출 실패 (isbn={}, region={})", isbn, region, e);
            return List.of();
        }

        if (response == null || response.response() == null) {
            return List.of();
        }
        Data4LibraryErrors.check(response.response().errCode(), response.response().error());
        if (response.response().libs() == null) {
            return List.of();
        }
        return response.response().libs().stream()
            .map(LibSrchByBookApiResponse.LibWrapper::lib)
            .filter(lib -> lib != null)
            .toList();
    }

    /** 청구기호는 거의 바뀌지 않으므로 길게 캐싱한다. */
    @Cacheable(cacheNames = CacheConfig.BOOK_CALL_NUMBERS, key = "#isbn + ':' + #libCode")
    public String findCallNumber(String isbn, String libCode) {
        ItemSearchApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/itemSrch")
                    .queryParam("authKey", authKey)
                    .queryParam("libCode", libCode)
                    .queryParam("isbn13", isbn)
                    .queryParam("type", "ALL")
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(ItemSearchApiResponse.class);
        } catch (RestClientException e) {
            return null;
        }

        if (response == null || response.response() == null) {
            return null;
        }
        Data4LibraryErrors.check(response.response().errCode(), response.response().error());
        if (response.response().docs() == null || response.response().docs().isEmpty()) {
            return null;
        }

        ItemSearchApiResponse.Doc doc = response.response().docs().get(0).doc();
        if (doc == null || doc.callNumbers() == null || doc.callNumbers().isEmpty()) {
            return null;
        }
        ItemSearchApiResponse.CallNumber callNumber = doc.callNumbers().get(0).callNumber();
        if (callNumber == null || callNumber.bookCode() == null) {
            return null;
        }
        return doc.classNo() + "-" + callNumber.bookCode().trim();
    }

    /** 대출 가능 여부는 실시간성이 의미 있으므로 짧게만 캐싱한다. */
    @Cacheable(cacheNames = CacheConfig.BOOK_AVAILABILITY, key = "#isbn + ':' + #libCode")
    public Boolean findAvailability(String isbn, String libCode) {
        BookExistApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/bookExist")
                    .queryParam("authKey", authKey)
                    .queryParam("libCode", libCode)
                    .queryParam("isbn13", isbn)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(BookExistApiResponse.class);
        } catch (RestClientException e) {
            return null;
        }

        if (response == null || response.response() == null) {
            return null;
        }
        Data4LibraryErrors.check(response.response().errCode(), response.response().error());
        if (response.response().result() == null) {
            return null;
        }
        return "Y".equalsIgnoreCase(response.response().result().loanAvailable());
    }
}
