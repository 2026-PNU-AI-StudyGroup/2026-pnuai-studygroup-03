package com.wakebook.book.support;

import com.wakebook.book.domain.HiddenBook;

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
