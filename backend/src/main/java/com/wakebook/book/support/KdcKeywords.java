package com.wakebook.book.support;

import java.util.Arrays;
import java.util.List;

/**
 * 정보나루 KDC 분류명("문학 &gt; 한국문학 &gt; 소설")을 표시용 키워드로 바꾼다.
 * 후보 도서 수만큼 AI를 호출하지 않고도 최소한의 분류 키워드를 붙이기 위한 장치이며,
 * 실제 추천 문구는 추천 시점에 AI가 만든다.
 */
public final class KdcKeywords {

    private static final int MAX_KEYWORDS = 3;

    private KdcKeywords() {
    }

    public static List<String> from(String className, String kdcCode) {
        java.util.stream.Stream<String> names = className == null ? java.util.stream.Stream.empty()
            : Arrays.stream(className.split(">"));
        KdcCategory category = KdcCategory.from(kdcCode, className, null);
        java.util.stream.Stream<String> categoryName = category == KdcCategory.UNKNOWN
            ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(category.label());
        return java.util.stream.Stream.concat(categoryName, names)
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .distinct()
            .limit(MAX_KEYWORDS)
            .toList();
    }
}
