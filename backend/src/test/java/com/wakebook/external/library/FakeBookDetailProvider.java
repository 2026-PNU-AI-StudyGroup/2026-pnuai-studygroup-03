package com.wakebook.external.library;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FakeBookDetailProvider implements BookDetailProvider {

    private String lastIsbn;
    private boolean empty = false;
    private BookDetail detail;
    private final Map<String, BookDetail> detailsByIsbn = new HashMap<>();
    private final Set<String> failingIsbns = new HashSet<>();
    private int callCount = 0;

    @Override
    public Optional<BookDetail> fetch(String isbn) {
        this.lastIsbn = isbn;
        this.callCount++;
        if (failingIsbns.contains(isbn)) {
            throw new IllegalStateException("simulated external API failure");
        }
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

    /** 상세를 지정하면 더 이상 빈 응답이 아니다. makeEmpty() 뒤에 다시 채워 넣는 경우를 위해 플래그를 푼다. */
    public void setDetail(BookDetail detail) {
        this.detail = detail;
        this.empty = false;
    }

    public void setDetailForIsbn(String isbn, BookDetail detail) {
        this.detailsByIsbn.put(isbn, detail);
    }

    public void failForIsbn(String isbn) {
        this.failingIsbns.add(isbn);
    }

    public String lastIsbn() {
        return lastIsbn;
    }

    public int callCount() {
        return callCount;
    }
}
