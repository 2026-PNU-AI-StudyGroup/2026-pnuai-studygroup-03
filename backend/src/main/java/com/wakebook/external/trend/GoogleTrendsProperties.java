package com.wakebook.external.trend;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trend.google")
public record GoogleTrendsProperties(String baseUrl, String region) {}
