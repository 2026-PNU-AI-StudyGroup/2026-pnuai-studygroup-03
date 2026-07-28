package com.wakebook.external.library;

import com.wakebook.external.library.dto.BookExistApiResponse;
import com.wakebook.external.library.dto.ItemSearchApiResponse;
import com.wakebook.external.library.dto.LibSrchByBookApiResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

@Component
public class Data4LibraryHoldingProvider implements LibraryHoldingProvider {

    private static final int MAX_LIBRARIES = 5;

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryHoldingProvider(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    @Override
    public List<LibraryHolding> findHoldings(String isbn, String region) {
        List<LibSrchByBookApiResponse.Lib> libs = fetchLibraries(isbn, region);

        return libs.stream()
            .map(lib -> toHolding(isbn, lib))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<LibSrchByBookApiResponse.Lib> fetchLibraries(String isbn, String region) {
        LibSrchByBookApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/libSrchByBook")
                    .queryParam("authKey", authKey)
                    .queryParam("isbn", isbn)
                    .queryParam("region", region)
                    .queryParam("pageSize", MAX_LIBRARIES)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(LibSrchByBookApiResponse.class);
        } catch (RestClientException e) {
            return List.of();
        }

        if (response == null || response.response() == null || response.response().libs() == null) {
            return List.of();
        }
        return response.response().libs().stream()
            .map(LibSrchByBookApiResponse.LibWrapper::lib)
            .toList();
    }

    /**
     * libSrchByBook만으로는 청구기호·대출가능여부를 알 수 없어 도서관마다 bookExist, itemSrch를
     * 추가로 호출한다. 한 도서관 조회가 실패해도 나머지 결과는 보여줄 수 있도록 개별 실패는 건너뛴다.
     */
    private LibraryHolding toHolding(String isbn, LibSrchByBookApiResponse.Lib lib) {
        Boolean available = fetchAvailability(isbn, lib.libCode());
        if (available == null) {
            return null;
        }
        String callNumber = fetchCallNumber(isbn, lib.libCode());
        return new LibraryHolding(lib.libName(), callNumber, available);
    }

    private Boolean fetchAvailability(String isbn, String libCode) {
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

        if (response == null || response.response() == null || response.response().result() == null) {
            return null;
        }
        return "Y".equalsIgnoreCase(response.response().result().loanAvailable());
    }

    private String fetchCallNumber(String isbn, String libCode) {
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

        if (response == null || response.response() == null || response.response().docs() == null
            || response.response().docs().isEmpty()) {
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
}
