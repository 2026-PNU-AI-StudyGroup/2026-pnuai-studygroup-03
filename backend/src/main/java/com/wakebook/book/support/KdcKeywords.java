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

    public static List<String> from(String className) {
        if (className == null || className.isBlank()) {
            return List.of();
        }
        return Arrays.stream(className.split(">"))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .distinct()
            .limit(MAX_KEYWORDS)
            .toList();
    }
}
