package com.wakebook.external.library;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.dto.ItemSearchApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("도서관 장서 카탈로그 조회")
class Data4LibraryHoldingCatalogProviderTest {

    private static final String LIBRARY_CODE = "121018";
    private static final LocalDate START = LocalDate.of(2025, 8, 15);
    private static final LocalDate END = LocalDate.of(2026, 8, 15);

    /**
     * 카탈로그 스캔은 최대 40페이지를 연달아 부르는데, 재시도가 없으면 그중 한 페이지만
     * 일시적으로 실패해도 몇 분짜리 스캔 전체가 버려졌다(실측 재현됨, 2026-08-22).
     */
    @Test
    void 일시적으로_실패해도_재시도해서_살려낸다() {
        var attempts = new AtomicInteger();
        var provider = providerWith((libraryCode, startDt, endDt, pageNo, pageSize) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new org.springframework.web.client.ResourceAccessException("일시적 오류");
            }
            return page(3);
        });

        HoldingCatalogResult result = provider.fetch(LIBRARY_CODE, START, END, 1, 50);

        assertThat(result.items()).hasSize(3);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void 재시도를_다_써도_안_되면_그때_실패한다() {
        var attempts = new AtomicInteger();
        var provider = providerWith((libraryCode, startDt, endDt, pageNo, pageSize) -> {
            attempts.incrementAndGet();
            throw new org.springframework.web.client.ResourceAccessException("계속 실패");
        });

        assertThatThrownBy(() -> provider.fetch(LIBRARY_CODE, START, END, 1, 50))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("도서관 장서 조회에 실패했습니다");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void 정상_응답이면_재시도_없이_바로_돌려준다() {
        var attempts = new AtomicInteger();
        var provider = providerWith((libraryCode, startDt, endDt, pageNo, pageSize) -> {
            attempts.incrementAndGet();
            return page(5);
        });

        HoldingCatalogResult result = provider.fetch(LIBRARY_CODE, START, END, 1, 50);

        assertThat(result.items()).hasSize(5);
        assertThat(attempts.get()).isEqualTo(1);
    }

    private Data4LibraryHoldingCatalogProvider providerWith(PageStub stub) {
        Data4LibraryProperties properties = new Data4LibraryProperties("http://localhost", "test-key", 12);
        return new Data4LibraryHoldingCatalogProvider(properties) {
            @Override
            ItemSearchApiResponse fetchPage(
                String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo, int pageSize
            ) {
                return stub.fetch(libraryCode, startDt, endDt, pageNo, pageSize);
            }

            /** 테스트가 재시도 대기로 느려지지 않도록 기다리지 않는다. */
            @Override
            void waitBeforeRetry(int attempt) {
            }
        };
    }

    @FunctionalInterface
    private interface PageStub {
        ItemSearchApiResponse fetch(String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo, int pageSize);
    }

    private ItemSearchApiResponse page(int count) {
        List<ItemSearchApiResponse.DocWrapper> docs = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            docs.add(new ItemSearchApiResponse.DocWrapper(doc("978%010d".formatted(i))));
        }
        return new ItemSearchApiResponse(
            new ItemSearchApiResponse.Response("테스트도서관", (long) count, docs, null, null)
        );
    }

    private ItemSearchApiResponse.Doc doc(String isbn13) {
        return new ItemSearchApiResponse.Doc(isbn13, "제목", "저자", null, "813.7", "한국문학", List.of());
    }
}
