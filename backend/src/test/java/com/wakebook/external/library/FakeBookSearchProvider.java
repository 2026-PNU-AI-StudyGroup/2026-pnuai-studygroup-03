package com.wakebook.external.library;

import java.util.List;

public class FakeBookSearchProvider implements BookSearchProvider {

    private BookSearchCriteria lastCriteria;

    @Override
    public BookSearchResult search(BookSearchCriteria criteria) {
        this.lastCriteria = criteria;
        List<BookSearchItem> items = List.of(
            new BookSearchItem("9788996991342", "미움받을 용기", "기시미 이치로", "https://example.com/cover1.jpg"),
            new BookSearchItem("9788960867450", "관계에도 연습이 필요합니다", "박상미", "https://example.com/cover2.jpg")
        );
        return new BookSearchResult(items, 2);
    }

    public BookSearchCriteria lastCriteria() {
        return lastCriteria;
    }
}
