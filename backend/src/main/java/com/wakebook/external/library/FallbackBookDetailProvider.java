package com.wakebook.external.library;

import com.wakebook.common.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 도서 상세를 알라딘에서 먼저 읽고, 쓸 수 없을 때 정보나루 → 카카오 순으로 넘어간다.
 *
 * 정보나루는 IP 미등록 시 하루 500건인데 후보군 산출 1회가 상세 조회만 90건 남짓을 쓴다.
 * 알라딘을 앞에 두면 그 90건이 정보나루 한도에서 빠진다. 카카오는 그 둘이 모두 실패했을 때만
 * 보는 마지막 수단이라, 키가 없으면 조용히 건너뛴다.
 *
 * 소비자(BookService·HiddenBookCollector 등)는 BookDetailProvider만 알면 되도록 @Primary로 둔다.
 * 캐시도 여기에만 건다. 아래 provider에 각각 걸면 알라딘으로 해결된 건이 캐시에 남지 않는다.
 */
@Primary
@Component
public class FallbackBookDetailProvider implements BookDetailProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackBookDetailProvider.class);

    private final BookDetailProvider primaryProvider;
    private final BookDetailProvider fallbackProvider;
    private final BookDetailProvider lastResortProvider;

    public FallbackBookDetailProvider(
        @Qualifier("aladinBookDetailProvider") BookDetailProvider primaryProvider,
        @Qualifier("data4LibraryBookDetailProvider") BookDetailProvider fallbackProvider,
        @Qualifier("kakaoBookDetailProvider") BookDetailProvider lastResortProvider
    ) {
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.lastResortProvider = lastResortProvider;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.BOOK_DETAILS, key = "#isbn")
    public Optional<BookDetail> fetch(String isbn) {
        Optional<BookDetail> primary = primaryProvider.fetch(isbn);
        if (primary.filter(FallbackBookDetailProvider::isUsable).isPresent()) {
            return primary;
        }

        // 알라딘에 없거나 소개글이 없으면 정보나루를 본다. 여기서 포기하면 후보 한 권을 통째로 잃는다.
        log.debug("알라딘에서 쓸 만한 상세를 얻지 못해 정보나루로 넘어간다 (isbn={})", isbn);
        Optional<BookDetail> fallback = fallbackProvider.fetch(isbn);
        if (fallback.filter(FallbackBookDetailProvider::isUsable).isPresent()) {
            return fallback;
        }

        // 정보나루도 실패하면 카카오까지 본다. 정보나루 한도를 다 쓴 날 후보군을 건지는 마지막 수단이다.
        log.debug("정보나루에서도 쓸 만한 상세를 얻지 못해 카카오로 넘어간다 (isbn={})", isbn);
        Optional<BookDetail> lastResort = lastResortProvider.fetch(isbn);
        if (lastResort.filter(FallbackBookDetailProvider::isUsable).isPresent()) {
            return lastResort;
        }

        // 셋 다 완전하지 않으면 소개글이 없더라도 먼저 받은 것을 순서대로 돌려준다.
        return fallback.or(() -> lastResort).or(() -> primary);
    }

    /**
     * 후보군 품질 기준이 "소개글이 있는가"라서, 소개글 없는 응답은 폴백을 한 번 더 볼 값어치가 있다.
     * 길이 판정까지 여기서 하지는 않는다. 그 기준은 HiddenBookCollector가 갖고 있어야 한다.
     */
    private static boolean isUsable(BookDetail detail) {
        return hasText(detail.title()) && hasText(detail.description());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
