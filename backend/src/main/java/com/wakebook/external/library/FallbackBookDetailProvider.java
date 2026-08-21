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
 * 도서 상세는 호출 여유가 큰 카카오에서 먼저 읽고, 부족하면 알라딘으로 보강한다.
 * 정보나루 상세 API는 일일 호출량이 작으므로 앞의 두 공급자가 모두 충분한 정보를 주지 못한 경우에만 사용한다.
 *
 * 소비자(BookService·HiddenBookCollector 등)는 BookDetailProvider만 알면 되도록 @Primary로 둔다.
 * 캐시도 여기에만 건다. 아래 provider에 각각 걸면 폴백으로 해결된 결과가 공통 캐시에 남지 않는다.
 */
@Primary
@Component
public class FallbackBookDetailProvider implements BookDetailProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackBookDetailProvider.class);

    private final BookDetailProvider kakaoProvider;
    private final BookDetailProvider aladinProvider;
    private final BookDetailProvider data4LibraryProvider;

    public FallbackBookDetailProvider(
        @Qualifier("kakaoBookDetailProvider") BookDetailProvider kakaoProvider,
        @Qualifier("aladinBookDetailProvider") BookDetailProvider aladinProvider,
        @Qualifier("data4LibraryBookDetailProvider") BookDetailProvider data4LibraryProvider
    ) {
        this.kakaoProvider = kakaoProvider;
        this.aladinProvider = aladinProvider;
        this.data4LibraryProvider = data4LibraryProvider;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.BOOK_DETAILS, key = "#isbn")
    public Optional<BookDetail> fetch(String isbn) {
        Optional<BookDetail> kakao = kakaoProvider.fetch(isbn);
        if (kakao.filter(FallbackBookDetailProvider::isUsable).isPresent()) {
            return kakao;
        }

        log.debug("카카오에서 충분한 상세를 얻지 못해 알라딘으로 넘어간다 (isbn={})", isbn);
        Optional<BookDetail> aladin = aladinProvider.fetch(isbn);
        if (aladin.filter(FallbackBookDetailProvider::isUsable).isPresent()) {
            return aladin;
        }

        log.debug("카카오와 알라딘에서 충분한 상세를 얻지 못해 정보나루를 최후 수단으로 호출한다 (isbn={})", isbn);
        Optional<BookDetail> data4Library = data4LibraryProvider.fetch(isbn);
        if (data4Library.filter(FallbackBookDetailProvider::isUsable).isPresent()) {
            return data4Library;
        }

        return kakao.or(() -> aladin).or(() -> data4Library);
    }

    /**
     * 후보군 품질 기준이 "소개글이 있는가"라서, 소개글 없는 응답은 폴백을 한 번 더 볼 값어치가 있다.
     * 길이 판정까지 여기서 하지는 않는다. 그 기준은 HiddenBookCollector가 갖고 있어야 한다.
     */
    private static boolean isUsable(BookDetail detail) {
        return hasText(detail.title()) && hasText(detail.author()) && hasText(detail.publisher())
            && hasText(detail.description());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
