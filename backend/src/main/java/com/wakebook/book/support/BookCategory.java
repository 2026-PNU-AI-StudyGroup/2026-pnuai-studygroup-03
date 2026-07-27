package com.wakebook.book.support;

import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum BookCategory {
    TOTAL("총류", "0"),
    PHILOSOPHY("철학", "1"),
    RELIGION("종교", "2"),
    SOCIAL_SCIENCE("사회과학", "3"),
    NATURAL_SCIENCE("자연과학", "4"),
    TECHNOLOGY("기술과학", "5"),
    ART("예술", "6"),
    LANGUAGE("언어", "7"),
    LITERATURE("문학", "8"),
    HISTORY("역사", "9");

    private final String label;
    private final String kdcCode;

    BookCategory(String label, String kdcCode) {
        this.label = label;
        this.kdcCode = kdcCode;
    }

    public String kdcCode() {
        return kdcCode;
    }

    public static String toKdcCode(String category) {
        if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)) {
            return null;
        }
        return Arrays.stream(values())
            .filter(c -> c.label.equals(category))
            .findFirst()
            .map(BookCategory::kdcCode)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "지원하지 않는 분야입니다: " + category));
    }
}
