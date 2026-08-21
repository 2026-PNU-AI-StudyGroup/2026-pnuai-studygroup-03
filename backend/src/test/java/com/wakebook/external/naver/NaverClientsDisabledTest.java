package com.wakebook.external.naver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaverClientsDisabledTest {
    private final NaverApiProperties properties = new NaverApiProperties("https://example.invalid", "", "");

    @Test
    void missingCredentialsSkipNewsAndMarkSearchTrendUnverified() {
        assertThat(new NaverNewsSearchClient(properties).search("환율", 3)).isEmpty();
        assertThat(new NaverSearchTrendClient(properties).validate("환율", "원달러 환율").status())
            .isEqualTo(com.wakebook.trend.domain.TrendValidationStatus.UNVERIFIED);
    }
}
