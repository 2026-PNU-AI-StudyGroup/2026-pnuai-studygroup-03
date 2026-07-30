package com.wakebook.external.aladin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aladin")
public record AladinProperties(String baseUrl, String ttbKey) {
}
