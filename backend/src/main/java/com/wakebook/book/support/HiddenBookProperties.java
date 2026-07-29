package com.wakebook.book.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hidden-book")
public record HiddenBookProperties(int maxLoanCount, int candidatePoolSize) {
}
