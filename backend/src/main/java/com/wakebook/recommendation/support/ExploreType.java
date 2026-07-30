package com.wakebook.recommendation.support;

import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;

public enum ExploreType {
    SIMILAR_TOPIC, SAME_MOOD, EASIER, DEEPER, OPPOSITE_VIEW;

    public static ExploreType fromValue(String value) {
        try {
            return ExploreType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "지원하지 않는 재탐색 유형입니다: " + value);
        }
    }
}
