package com.wakebook.external.aladin;

import com.wakebook.external.aladin.dto.AladinItemLookupApiResponse;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 알라딘 ItemLookUp으로 서지 정보를 읽는다.
 *
 * 후보군 산출 호출의 8~9할이 도서 상세 조회였고, 정보나루는 IP 미등록 시 하루 500건이라
 * 여기가 한도를 다 먹었다. 알라딘은 한도가 훨씬 넉넉하고, 후보군 50권 전수로 재 보니
 * 수록률 100%·품질 기준 통과 100%였다(2026-08-15).
 *
 * 실패해도 예외를 던지지 않고 비워서 돌려준다. 정보나루로 넘어가는 판단은
 * FallbackBookDetailProvider가 한다.
 */
@Component
public class AladinBookDetailProvider implements BookDetailProvider {

    private static final Logger log = LoggerFactory.getLogger(AladinBookDetailProvider.class);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    private final RestClient restClient;
    private final String ttbKey;

    public AladinBookDetailProvider(AladinProperties properties) {
        // 알라딘은 http로 부르면 301로 https에 넘긴다. RestClient 기본 설정은 리다이렉트를 따라가지 않아
        // aladin.base-url이 http인 환경에서는 조용히 전부 실패하고 정보나루 폴백으로 새어 나간다
        // (저장소의 application.properties가 아직 http다). 설정값과 무관하게 동작하도록 따라가게 한다.
        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.restClient = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
        this.ttbKey = properties.ttbKey();
    }

    @Override
    public Optional<BookDetail> fetch(String isbn) {
        if (isbn == null || isbn.isBlank() || ttbKey == null || ttbKey.isBlank()) {
            return Optional.empty();
        }

        AladinItemLookupApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/ItemLookUp.aspx")
                    .queryParam("ttbkey", ttbKey)
                    .queryParam("itemIdType", "ISBN13")
                    .queryParam("ItemId", isbn)
                    .queryParam("output", "js")
                    .queryParam("Version", "20131101")
                    .build())
                .retrieve()
                .body(AladinItemLookupApiResponse.class);
        } catch (RestClientException e) {
            log.warn("알라딘 ItemLookUp 호출 실패 (isbn={})", isbn, e);
            return Optional.empty();
        }

        if (response == null) {
            return Optional.empty();
        }
        if (response.errorCode() != null) {
            log.warn("알라딘 ItemLookUp 오류 응답 (isbn={}, errorCode={}, message={})",
                isbn, response.errorCode(), response.errorMessage());
            return Optional.empty();
        }
        if (response.item() == null || response.item().isEmpty() || response.item().get(0) == null) {
            return Optional.empty();
        }

        AladinItemLookupApiResponse.Item item = response.item().get(0);
        return Optional.of(new BookDetail(
            hasText(item.isbn13()) ? item.isbn13() : isbn,
            clean(item.title()),
            clean(item.author()),
            clean(item.publisher()),
            parseYear(item.pubDate()),
            emptyToNull(item.cover()),
            clean(item.description())
        ));
    }

    /** 알라딘은 검색어 강조 태그가 섞여 오는 경우가 있어 걷어낸다. */
    private String clean(String value) {
        if (value == null) {
            return null;
        }
        return emptyToNull(HTML_TAG.matcher(value).replaceAll("").trim());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** pubDate는 `2026-06-20` 형태다. 앞 4자리만 연도로 쓴다. */
    private Integer parseYear(String pubDate) {
        if (pubDate == null || pubDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(pubDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
