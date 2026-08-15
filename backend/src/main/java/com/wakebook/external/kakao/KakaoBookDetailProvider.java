package com.wakebook.external.kakao;

import com.wakebook.external.kakao.dto.KakaoBookSearchApiResponse;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.HiddenBookDetailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 카카오 책 검색 API로 잠자는 도서 후보의 표지·소개 문구를 보강한다. {@code target=isbn}으로
 * 검색하면 정보나루 srchDtlList처럼 ISBN을 정확히 지정해 조회할 수 있어(네이버 책 검색과 달리
 * 제목 키워드 검색 후 결과에서 골라내는 우회가 필요 없음), 첫 번째 결과를 그대로 채택한다.
 *
 * 외부 API 호출 실패는 후보 하나만 건너뛰고 업로드 전체를 실패시키지 않는다(목차 조회용
 * AladinTableOfContentsProvider와 같은 방침).
 */
@Component
public class KakaoBookDetailProvider implements HiddenBookDetailProvider {

    private static final Logger log = LoggerFactory.getLogger(KakaoBookDetailProvider.class);

    private final RestClient restClient;
    private final String apiKey;
    private final AtomicInteger callCount = new AtomicInteger(0);

    public KakaoBookDetailProvider(KakaoProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.apiKey = properties.apiKey();
    }

    @Override
    public Optional<BookDetail> fetch(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return Optional.empty();
        }
        int n = callCount.incrementAndGet();

        KakaoBookSearchApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v3/search/book")
                    .queryParam("target", "isbn")
                    .queryParam("query", isbn)
                    .build())
                .header("Authorization", "KakaoAK " + apiKey)
                .retrieve()
                .body(KakaoBookSearchApiResponse.class);
        } catch (RestClientException e) {
            log.warn("카카오 호출 #{} 실패 isbn={}", n, isbn, e);
            return Optional.empty();
        }

        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            log.info("카카오 호출 #{} not-found isbn={}", n, isbn);
            return Optional.empty();
        }

        log.info("카카오 호출 #{} found isbn={}", n, isbn);
        return Optional.of(toBookDetail(isbn, response.documents().get(0)));
    }

    private BookDetail toBookDetail(String isbn, KakaoBookSearchApiResponse.Document document) {
        return new BookDetail(
            isbn,
            stripTags(document.title()),
            document.authors() == null ? null : String.join(", ", document.authors()),
            document.publisher(),
            parseYear(document.datetime()),
            document.thumbnail(),
            stripTags(document.contents())
        );
    }

    private String stripTags(String value) {
        return value == null ? null : value.replaceAll("<[^>]+>", "").trim();
    }

    private Integer parseYear(String datetime) {
        if (datetime == null || datetime.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(datetime.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
