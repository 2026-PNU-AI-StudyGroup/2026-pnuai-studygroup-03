package com.wakebook.external.library;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data4library")
public record Data4LibraryProperties(String baseUrl, String authKey, int defaultPeriodMonths) {
}
