package com.wakebook.external.library;

import java.util.List;

public class FakePopularLoanBookProvider implements PopularLoanBookProvider {

    private PopularLoanBookCriteria lastCriteria;

    @Override
    public PopularLoanBookResult fetch(PopularLoanBookCriteria criteria) {
        this.lastCriteria = criteria;
        List<PopularLoanBookItem> items = List.of(
            new PopularLoanBookItem("9788996991342", "미움받을 용기", "기시미 이치로", "https://example.com/cover1.jpg", 1, 1284),
            new PopularLoanBookItem("9788960867450", "관계에도 연습이 필요합니다", "박상미", "https://example.com/cover2.jpg", 2, 980)
        );
        return new PopularLoanBookResult(items, 89);
    }

    public PopularLoanBookCriteria lastCriteria() {
        return lastCriteria;
    }
}
