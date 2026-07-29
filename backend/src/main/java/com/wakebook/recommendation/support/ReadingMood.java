package com.wakebook.recommendation.support;

import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum ReadingMood {
    WARM("따뜻한"),
    PLAIN("담백한"),
    CHEERFUL("유쾌한"),
    CONTEMPLATIVE("사색적인");

    private final String label;

    ReadingMood(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ReadingMood fromLabel(String value) {
        return Arrays.stream(values())
            .filter(mood -> mood.label.equals(value))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "지원하지 않는 분위기입니다: " + value));
    }
}
