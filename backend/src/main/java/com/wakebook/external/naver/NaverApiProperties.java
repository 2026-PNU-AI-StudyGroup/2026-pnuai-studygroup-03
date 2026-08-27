package com.wakebook.external.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver-api")
public record NaverApiProperties(String baseUrl, String clientId, String clientSecret) {
    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
