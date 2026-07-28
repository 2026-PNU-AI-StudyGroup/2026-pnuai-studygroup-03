package com.wakebook.external.library;

import java.util.Optional;

public class FakeBookDetailProvider implements BookDetailProvider {

    private String lastIsbn;
    private boolean empty = false;

    @Override
    public Optional<BookDetail> fetch(String isbn) {
        this.lastIsbn = isbn;
        if (empty) {
            return Optional.empty();
        }
        return Optional.of(new BookDetail(
            isbn, "미움받을 용기", "기시미 이치로", "인플루엔셜", 2014,
            "https://example.com/cover1.jpg", "아들러 심리학을 바탕으로..."
        ));
    }

    public void makeEmpty() {
        this.empty = true;
    }

    public String lastIsbn() {
        return lastIsbn;
    }
}
