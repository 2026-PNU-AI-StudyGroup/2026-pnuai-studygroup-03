package com.wakebook.external.kakao;

import com.wakebook.external.kakao.dto.KakaoBookSearchApiResponse;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
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
 *
 * 후보군 도서 상세의 1차 공급자다. 카카오 ISBN 조회로 상세를 먼저 채워
 * 정보나루의 작은 일일 호출량이 후보군 생성에 소진되지 않게 한다.
 * KAKAO_API_KEY가 없으면 호출하지 않고 비어 있는 결과를 돌려주므로, 키를 설정하지 않은 환경에서도
 * 기존 동작 그대로 동작한다.
 */
@Component
public class KakaoBookDetailProvider implements BookDetailProvider {

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
        // 키가 없으면 매 후보마다 401을 받아 로그만 더럽힌다. 조용히 폴백 대상에서 빠진다.
        if (apiKey == null || apiKey.isBlank()) {
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
