package com.wakebook.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 캐싱 대상은 두 가지다.
 *
 * 1) OpenAI 응답 — 같은 입력이면 결과가 달라질 이유가 없는데 매번 다시 부르면 비용이 그대로 늘어난다.
 * 2) 정보나루 응답 — 일일 호출 한도가 IP 미등록 시 500건이라 캐싱 없이는 금방 소진된다.
 *    특히 도서 상세 한 번에 소장 도서관 수만큼 호출이 나가므로 여기가 가장 크다.
 *
 * 갱신 주기가 다른 데이터를 한 설정으로 묶으면 실시간성이나 호출량 중 하나를 잃는다.
 * 그래서 캐시마다 TTL을 따로 잡는다. 재시작하면 비는 인메모리 캐시다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String AI_KEYWORDS = "aiKeywords";
    public static final String AI_RECOMMENDATIONS = "aiRecommendations";
    public static final String AI_EXPLORE = "aiExplore";
    public static final String BOOK_COMPARISONS = "bookComparisons";
    public static final String BOOK_DETAILS = "bookDetails";
    public static final String BOOK_HOLDING_LIBRARIES = "bookHoldingLibraries";
    public static final String BOOK_CALL_NUMBERS = "bookCallNumbers";
    public static final String BOOK_AVAILABILITY = "bookAvailability";
    public static final String POPULAR_BOOKS = "popularBooks";
    public static final String LIBRARY_DIRECTORY = "libraryDirectory";
    public static final String LIBRARY_LOAN_RANKING = "libraryLoanRanking";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setAllowNullValues(true);

        // AI 응답: 같은 도서·같은 조합이면 결과가 바뀌지 않는다.
        register(cacheManager, AI_KEYWORDS, Duration.ofDays(7), 2_000);
        // 동일 조건 추천은 프론트의 더 보기와 재방문에서 재사용한다. 후보군 갱신 반영을 위해 짧게 유지한다.
        register(cacheManager, AI_RECOMMENDATIONS, Duration.ofMinutes(15), 1_000);
        register(cacheManager, AI_EXPLORE, Duration.ofMinutes(15), 1_000);
        register(cacheManager, BOOK_COMPARISONS, Duration.ofDays(7), 2_000);

        // 도서 서지 정보: 사실상 불변. 후보군 산출·추천·책장이 모두 재사용한다.
        register(cacheManager, BOOK_DETAILS, Duration.ofDays(7), 10_000);

        // 소장 도서관·청구기호: 자주 바뀌지 않는다.
        register(cacheManager, BOOK_HOLDING_LIBRARIES, Duration.ofDays(3), 5_000);
        register(cacheManager, BOOK_CALL_NUMBERS, Duration.ofDays(7), 10_000);
        // 대출 가능 여부만 실시간성이 의미 있어 짧게 잡는다.
        register(cacheManager, BOOK_AVAILABILITY, Duration.ofMinutes(10), 5_000);

        // 인기 도서는 월 단위 통계, 도서관 목록은 거의 불변, 대출 순위는 후보군 재산출에만 쓰인다.
        register(cacheManager, POPULAR_BOOKS, Duration.ofHours(12), 500);
        register(cacheManager, LIBRARY_DIRECTORY, Duration.ofDays(7), 100);
        register(cacheManager, LIBRARY_LOAN_RANKING, Duration.ofDays(1), 100);

        return cacheManager;
    }

    private void register(CaffeineCacheManager cacheManager, String name, Duration ttl, int maximumSize) {
        cacheManager.registerCustomCache(name, Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maximumSize)
            .build());
    }
}
