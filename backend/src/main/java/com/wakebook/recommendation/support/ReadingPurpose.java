package com.wakebook.recommendation.support;

import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum ReadingPurpose {
    MEANING_COMFORT("마음의 위로"),
    NEW_PERSPECTIVE("새로운 관점"),
    PRACTICAL_SOLUTION("실용적인 해결책"),
    DEEP_REFLECTION("깊이 있는 사유");

    private final String label;

    ReadingPurpose(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ReadingPurpose fromLabel(String value) {
        return Arrays.stream(values())
            .filter(purpose -> purpose.label.equals(value))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "지원하지 않는 독서 목적입니다: " + value));
    }
}
