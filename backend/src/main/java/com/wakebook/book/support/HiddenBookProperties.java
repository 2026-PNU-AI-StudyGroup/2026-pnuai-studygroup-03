package com.wakebook.book.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxLoanCount CSV 경로에서 후보로 볼 최대 대출건수.
 * @param candidatePoolSize 도서관당 저장할 후보 도서 수.
 * @param apiPeriodMonths 정보나루 API 경로에서 장서·대출 순위를 조회할 기간(개월).
 */
@ConfigurationProperties(prefix = "hidden-book")
public record HiddenBookProperties(int maxLoanCount, int candidatePoolSize, int apiPeriodMonths) {
}
