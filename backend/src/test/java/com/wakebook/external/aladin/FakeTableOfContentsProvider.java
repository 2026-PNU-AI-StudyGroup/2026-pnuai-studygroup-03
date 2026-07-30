package com.wakebook.external.aladin;

import java.util.List;

public class FakeTableOfContentsProvider implements TableOfContentsProvider {

    private String lastIsbn;
    private List<String> tableOfContents = List.of("트라우마를 부정하라", "모든 고민은 인간관계에서 비롯된다");

    @Override
    public List<String> fetch(String isbn) {
        this.lastIsbn = isbn;
        return tableOfContents;
    }

    public void setTableOfContents(List<String> tableOfContents) {
        this.tableOfContents = tableOfContents;
    }

    public String lastIsbn() {
        return lastIsbn;
    }
}
