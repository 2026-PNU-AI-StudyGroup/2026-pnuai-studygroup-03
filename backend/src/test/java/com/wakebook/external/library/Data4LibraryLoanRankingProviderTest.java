package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.dto.LoanItemSearchApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("도서관 대출 순위 조회")
class Data4LibraryLoanRankingProviderTest {

    private static final String LIBRARY_CODE = "121024";
    private static final LocalDate START = LocalDate.of(2025, 8, 15);
    private static final LocalDate END = LocalDate.of(2026, 8, 15);
    private static final int PAGE_SIZE = 1000;

    @Test
    void 마지막_페이지가_가득_차지_않으면_거기서_정상적으로_끝난다() {
        var provider = providerReturning(Map.of(
            1, page(PAGE_SIZE, 0),
            2, page(300, PAGE_SIZE)
        ));

        Set<String> ranked = provider.fetchRankedIsbns(LIBRARY_CODE, START, END);

        assertThat(ranked).hasSize(1300);
    }

    @Test
    void 빈_페이지가_오면_거기서_정상적으로_끝난다() {
        var provider = providerReturning(Map.of(
            1, page(PAGE_SIZE, 0),
            2, page(0, 0)
        ));

        assertThat(provider.fetchRankedIsbns(LIBRARY_CODE, START, END)).hasSize(PAGE_SIZE);
    }

    /**
     * 후보군은 "장서 − 이 집합"이라, 집합이 모자라면 인기 도서가 잠자는 책으로 둔갑한다.
     * 부분 집합을 돌려주느니 실패하는 편이 낫다.
     */
    @Test
    void 중간_페이지를_못_받으면_부분_집합_대신_예외를_던진다() {
        var provider = providerReturning(Map.of(
            1, page(PAGE_SIZE, 0),
            2, page(PAGE_SIZE, PAGE_SIZE)
            // 3페이지는 응답 없음
        ));

        assertThatThrownBy(() -> provider.fetchRankedIsbns(LIBRARY_CODE, START, END))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("대출 순위를 모두 받지 못해");
    }

    @Test
    void 다섯_페이지를_모두_채우면_오천건을_모은다() {
        var pages = new java.util.HashMap<Integer, LoanItemSearchApiResponse>();
        for (int pageNo = 1; pageNo <= 5; pageNo++) {
            pages.put(pageNo, page(PAGE_SIZE, (pageNo - 1) * PAGE_SIZE));
        }

        assertThat(providerReturning(pages).fetchRankedIsbns(LIBRARY_CODE, START, END))
            .hasSize(5 * PAGE_SIZE);
    }

    @Test
    void 일시적으로_실패해도_재시도해서_살려낸다() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var provider = providerWith((libraryCode, startDt, endDt, pageNo) -> {
            if (pageNo == 1 && attempts.incrementAndGet() < 3) {
                throw new org.springframework.web.client.ResourceAccessException("일시적 오류");
            }
            return pageNo == 1 ? page(300, 0) : null;
        });

        assertThat(provider.fetchRankedIsbns(LIBRARY_CODE, START, END)).hasSize(300);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void 재시도를_다_써도_안_되면_그때_실패한다() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var provider = providerWith((libraryCode, startDt, endDt, pageNo) -> {
            attempts.incrementAndGet();
            throw new org.springframework.web.client.ResourceAccessException("계속 실패");
        });

        assertThatThrownBy(() -> provider.fetchRankedIsbns(LIBRARY_CODE, START, END))
            .isInstanceOf(ApiException.class);
        assertThat(attempts.get()).isEqualTo(3);
    }

    /** HTTP를 타지 않도록 페이지 응답만 갈아끼운다. */
    private Data4LibraryLoanRankingProvider providerReturning(
        Map<Integer, LoanItemSearchApiResponse> pages
    ) {
        return providerWith((libraryCode, startDt, endDt, pageNo) -> pages.get(pageNo));
    }

    private Data4LibraryLoanRankingProvider providerWith(PageStub stub) {
        Data4LibraryProperties properties =
            new Data4LibraryProperties("http://localhost", "test-key", 12);
        return new Data4LibraryLoanRankingProvider(properties) {
            @Override
            LoanItemSearchApiResponse fetchPage(
                String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo
            ) {
                return stub.fetch(libraryCode, startDt, endDt, pageNo);
            }

            /** 테스트가 재시도 대기로 느려지지 않도록 기다리지 않는다. */
            @Override
            void waitBeforeRetry(int attempt) {
            }
        };
    }

    @FunctionalInterface
    private interface PageStub {
        LoanItemSearchApiResponse fetch(String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo);
    }

    private LoanItemSearchApiResponse page(int count, int isbnOffset) {
        List<LoanItemSearchApiResponse.DocWrapper> docs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            docs.add(new LoanItemSearchApiResponse.DocWrapper(doc("978%010d".formatted(isbnOffset + i))));
        }
        return new LoanItemSearchApiResponse(
            new LoanItemSearchApiResponse.Response((long) count, 5000L, docs, null, null)
        );
    }

    private LoanItemSearchApiResponse.Doc doc(String isbn13) {
        return new LoanItemSearchApiResponse.Doc(
            "1", "제목", "저자", "출판사", "2026", isbn13,
            null, null, null, null, null, "10"
        );
    }
}
