package com.wakebook.external.naver;

import com.wakebook.trend.domain.TrendValidationStatus;

public record SearchTrendValidation(TrendValidationStatus status, Double spikeScore) {
    public static SearchTrendValidation unverified() {
        return new SearchTrendValidation(TrendValidationStatus.UNVERIFIED, null);
    }
}
