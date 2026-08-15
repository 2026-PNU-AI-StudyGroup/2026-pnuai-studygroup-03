package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.common.config.CacheConfig;
import com.wakebook.external.library.dto.LoanItemSearchApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

@Component
public class Data4LibraryLoanRankingProvider implements LibraryLoanRankingProvider {

    private static final Logger log = LoggerFactory.getLogger(Data4LibraryLoanRankingProvider.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 정보나루가 도서관별 대출 순위를 5,000위까지만 준다(실측). pageSize 1000이 허용되므로 5회면 전량이다. */
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 5;

    /** 페이지 하나가 실패하면 산출 전체가 실패하므로, 일시적 오류는 재시도로 흡수한다. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 500;

    private final RestClient restClient;
    private final String authKey;

    public Data4LibraryLoanRankingProvider(Data4LibraryProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.authKey = properties.authKey();
    }

    /**
     * 이 집합은 "인기 있는 책"이고, 후보군은 장서에서 이 집합을 뺀 나머지다.
     * 그래서 <b>일부만 모으면 판정이 안전한 쪽이 아니라 위험한 쪽으로 틀어진다</b> —
     * 빠진 순위의 인기 도서가 그대로 "잠자는 책" 후보가 된다.
     * 페이지를 하나라도 못 받으면 부분 집합을 돌려주지 않고 실패시킨다.
     * (예외를 던지면 Spring이 캐시에 남기지 않으므로, 망가진 집합이 하루 동안 재사용되는 일도 없다.)
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.LIBRARY_LOAN_RANKING, key = "{#libraryCode, #startDt, #endDt}")
    public Set<String> fetchRankedIsbns(String libraryCode, LocalDate startDt, LocalDate endDt) {
        Set<String> rankedIsbns = new HashSet<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            LoanItemSearchApiResponse response = fetchPageWithRetry(libraryCode, startDt, endDt, pageNo);
            Data4LibraryErrors.check(response.response().errCode(), response.response().error());

            var docs = response.response().docs();
            if (docs == null || docs.isEmpty()) {
                // 더 줄 게 없다는 뜻이다. 5,000권보다 장서가 적은 도서관에서 정상적으로 나온다.
                break;
            }
            docs.stream()
                .map(LoanItemSearchApiResponse.DocWrapper::doc)
                .filter(doc -> doc != null && doc.isbn13() != null && !doc.isbn13().isBlank())
                .forEach(doc -> rankedIsbns.add(doc.isbn13().trim()));
            if (docs.size() < PAGE_SIZE) {
                break;
            }
        }
        return rankedIsbns;
    }

    /**
     * 한 페이지라도 못 받으면 산출 전체가 실패하므로, 일시적인 오류는 몇 번 다시 시도해 본다.
     *
     * 재시도로 못 살리는 실패도 있다. 한도 초과는 정상 응답(errCode)으로 오기 때문에 여기서 잡히지 않고
     * 호출부의 Data4LibraryErrors가 곧바로 503으로 끊는다 — 하루치가 소진된 상황에서 5번씩 더 두드리지 않는다.
     */
    private LoanItemSearchApiResponse fetchPageWithRetry(
        String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo
    ) {
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                LoanItemSearchApiResponse response = fetchPage(libraryCode, startDt, endDt, pageNo);
                if (response != null && response.response() != null) {
                    if (attempt > 1) {
                        log.info("대출 순위 {}페이지를 {}번째 시도에서 받았다 (libCode={})",
                            pageNo, attempt, libraryCode);
                    }
                    return response;
                }
                lastError = null;
                log.warn("대출 순위 응답이 비어 있다 (libCode={}, pageNo={}, 시도={}/{})",
                    libraryCode, pageNo, attempt, MAX_ATTEMPTS);
            } catch (RestClientException e) {
                lastError = e;
                log.warn("정보나루 loanItemSrch 호출 실패 (libCode={}, pageNo={}, 시도={}/{}): {}",
                    libraryCode, pageNo, attempt, MAX_ATTEMPTS, e.toString());
            }
            if (attempt < MAX_ATTEMPTS) {
                waitBeforeRetry(attempt);
            }
        }
        if (lastError != null) {
            log.error("대출 순위 {}페이지를 {}번 시도했지만 실패했다 (libCode={})",
                pageNo, MAX_ATTEMPTS, libraryCode, lastError);
        }
        throw incompleteRanking(libraryCode, pageNo);
    }

    /** 테스트에서 실제로 기다리지 않도록 package-private으로 둔다. */
    void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("대출 순위 조회가 중단됐습니다.", e);
        }
    }

    /** 테스트에서 HTTP 없이 페이지 응답을 갈아끼울 수 있도록 package-private으로 둔다. */
    LoanItemSearchApiResponse fetchPage(
        String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo
    ) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/loanItemSrch")
                    .queryParam("authKey", authKey)
                    .queryParam("libCode", libraryCode)
                    .queryParam("startDt", startDt.format(DATE_FORMAT))
                    .queryParam("endDt", endDt.format(DATE_FORMAT))
                    .queryParam("pageNo", pageNo)
                    .queryParam("pageSize", PAGE_SIZE)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(LoanItemSearchApiResponse.class);
    }

    private ApiException incompleteRanking(String libraryCode, int pageNo) {
        log.error("대출 순위를 일부만 받아 후보군 산출을 중단한다 (libCode={}, 실패한 pageNo={})",
            libraryCode, pageNo);
        return new ApiException(
            HttpStatus.BAD_GATEWAY, "BOOK_002",
            "도서관 대출 순위를 모두 받지 못해 후보군을 만들 수 없습니다. 잠시 후 다시 시도해 주세요."
        );
    }
}
