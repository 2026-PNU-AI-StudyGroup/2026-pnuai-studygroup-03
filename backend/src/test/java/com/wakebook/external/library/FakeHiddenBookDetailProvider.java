package com.wakebook.external.library;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FakeHiddenBookDetailProvider implements HiddenBookDetailProvider {

    private String lastIsbn;
    private boolean empty = false;
    private BookDetail detail;
    private final Map<String, BookDetail> detailsByIsbn = new HashMap<>();
    private int callCount = 0;

    @Override
    public Optional<BookDetail> fetch(String isbn) {
        this.lastIsbn = isbn;
        this.callCount++;
        if (empty) {
            return Optional.empty();
        }
        if (detailsByIsbn.containsKey(isbn)) {
            return Optional.of(detailsByIsbn.get(isbn));
        }
        if (detail != null) {
            return Optional.of(detail);
        }
        return Optional.of(new BookDetail(
            isbn, "미움받을 용기", "기시미 이치로", "인플루엔셜", 2014,
            "https://example.com/cover1.jpg", "아들러 심리학을 바탕으로..."
        ));
    }

    public void makeEmpty() {
        this.empty = true;
    }

    public void setDetail(BookDetail detail) {
        this.detail = detail;
    }

    public void setDetailForIsbn(String isbn, BookDetail detail) {
        this.detailsByIsbn.put(isbn, detail);
    }

    public String lastIsbn() {
        return lastIsbn;
    }

    public int callCount() {
        return callCount;
    }
}
