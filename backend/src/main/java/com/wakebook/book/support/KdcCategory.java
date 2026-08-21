package com.wakebook.book.support;

import java.util.Arrays;

public enum KdcCategory {
    GENERAL("0", "총류"), PHILOSOPHY("1", "철학"), RELIGION("2", "종교"),
    SOCIAL_SCIENCE("3", "사회과학"), NATURAL_SCIENCE("4", "자연과학"),
    TECHNOLOGY("5", "기술과학"), ARTS("6", "예술"), LANGUAGE("7", "언어"),
    LITERATURE("8", "문학"), HISTORY("9", "역사"), UNKNOWN("UNKNOWN", "미분류");

    private final String code;
    private final String label;

    KdcCategory(String code, String label) { this.code = code; this.label = label; }
    public String code() { return code; }
    public String label() { return label; }

    public static KdcCategory from(String kdcCode, String className, String callNumber) {
        KdcCategory byCode = fromLeadingDigit(kdcCode);
        if (byCode != UNKNOWN) return byCode;
        if (className != null) {
            String normalized = className.strip();
            for (KdcCategory category : values()) {
                if (category != UNKNOWN && normalized.startsWith(category.label)) return category;
            }
        }
        return fromLeadingDigit(callNumber);
    }

    private static KdcCategory fromLeadingDigit(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        char first = value.strip().charAt(0);
        if (!Character.isDigit(first)) return UNKNOWN;
        String code = String.valueOf(first);
        return Arrays.stream(values()).filter(category -> category.code.equals(code)).findFirst().orElse(UNKNOWN);
    }
}
