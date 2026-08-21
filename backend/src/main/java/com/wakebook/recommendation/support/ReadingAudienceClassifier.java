package com.wakebook.recommendation.support;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.support.HiddenBookPromptSummary;
import com.wakebook.external.library.BookDetail;

import java.util.List;
import java.util.Locale;

/** 기준 도서와 도서관 서가 정보에서 추천용 예상 독자층을 일관되게 판정한다. */
public final class ReadingAudienceClassifier {

    private static final List<String> CHILD_SHELF_CUES = List.of(
        "어린이", "아동", "유아", "초등", "새싹", "꿈나무", "만화코너"
    );
    private static final List<String> CHILD_TEXT_CUES = List.of(
        "어린이를 위한", "어린이 독자", "어린이들이", "아이들을 위한", "초등학생", "초등 교육",
        "학습만화", "학습 만화", "그림책", "아동 도서", "유아 도서", "어린이 동화"
    );
    private static final List<String> TEEN_CUES = List.of(
        "청소년", "중학생", "고등학생", "십대", "10대", "청소년자료"
    );
    private static final List<String> ADULT_SHELF_CUES = List.of(
        "종합자료실", "종합실", "일반자료실", "성인자료실"
    );

    private ReadingAudienceClassifier() {
    }

    public static Audience source(BookDetail book) {
        if (book == null) return Audience.GENERAL;
        String text = normalize(safe(book.title()) + " " + safe(book.description()));
        if (containsAny(text, CHILD_TEXT_CUES)) return Audience.CHILD;
        if (containsAny(text, TEEN_CUES)) return Audience.TEEN;
        return Audience.ADULT;
    }

    public static Audience candidate(HiddenBook book) {
        String shelf = normalize(book.getShelfName());
        String description = normalize(HiddenBookPromptSummary.resolve(book));
        if (containsAny(shelf, CHILD_SHELF_CUES) || containsAny(description, CHILD_TEXT_CUES)) {
            return Audience.CHILD;
        }
        if (containsAny(shelf, TEEN_CUES) || containsAny(description, TEEN_CUES)) return Audience.TEEN;
        if (containsAny(shelf, ADULT_SHELF_CUES)) return Audience.ADULT;
        return Audience.GENERAL;
    }

    public static boolean matches(Audience source, Audience candidate) {
        return switch (source) {
            case ADULT -> candidate != Audience.CHILD;
            case CHILD -> candidate == Audience.CHILD || candidate == Audience.GENERAL;
            case TEEN -> candidate != Audience.CHILD;
            case GENERAL -> true;
        };
    }

    private static boolean containsAny(String text, List<String> cues) {
        return cues.stream().anyMatch(text::contains);
    }

    private static String normalize(String value) {
        return safe(value).strip().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public enum Audience {
        CHILD, TEEN, ADULT, GENERAL
    }
}
