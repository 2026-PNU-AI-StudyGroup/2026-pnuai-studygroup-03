package com.wakebook.recommendation.support;

import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;

public enum ReadingTime {
    SHORT, MEDIUM, LONG, SLOW;

    public static ReadingTime fromValue(String value) {
        try {
            return ReadingTime.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "지원하지 않는 독서 시간입니다: " + value);
        }
    }
}
