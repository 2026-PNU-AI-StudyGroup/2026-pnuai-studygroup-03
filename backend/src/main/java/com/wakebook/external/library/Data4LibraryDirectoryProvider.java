package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.common.config.CacheConfig;
import com.wakebook.external.library.dto.LibrarySearchApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class Data4LibraryDirectoryProvider implements LibraryDirectoryProvider {

    private static final Logger log = LoggerFactory.getLogger(Data4LibraryDirectoryProvider.class);
    private static final int MAX_PAGE_SIZE = 500;

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryDirectoryProvider(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.LIBRARY_DIRECTORY, key = "#region")
    public List<LibraryDirectoryItem> findByRegion(String region) {
        LibrarySearchApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/libSrch")
                    .queryParam("authKey", authKey)
                    .queryParam("region", region)
                    .queryParam("pageNo", 1)
                    .queryParam("pageSize", MAX_PAGE_SIZE)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(LibrarySearchApiResponse.class);
        } catch (RestClientException e) {
            log.error("정보나루 libSrch 호출 실패 (region={})", region, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_002", "도서관 목록 조회에 실패했습니다.");
        }

        if (response == null || response.response() == null) {
            return List.of();
        }
        Data4LibraryErrors.check(response.response().errCode(), response.response().error());
        if (response.response().libs() == null) {
            return List.of();
        }

        return response.response().libs().stream()
            .map(LibrarySearchApiResponse.LibWrapper::lib)
            .filter(lib -> lib != null && lib.libCode() != null)
            .map(lib -> new LibraryDirectoryItem(
                lib.libCode(), lib.libName(), lib.address(), parseCount(lib.bookCount())
            ))
            .toList();
    }

    private long parseCount(String value) {
        try {
            return value == null || value.isBlank() ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
