package com.wakebook.book.support;

import com.wakebook.book.domain.HiddenBook;

/** AI 프롬프트에는 추천 결과가 아닌 원래 도서 소개를 우선 제공한다. */
public final class HiddenBookPromptSummary {

    private HiddenBookPromptSummary() {
    }

    public static String resolve(HiddenBook book) {
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            return book.getDescription();
        }
        if (book.getReason() != null && !book.getReason().isBlank()) {
            return book.getReason();
        }
        return "";
    }
}
