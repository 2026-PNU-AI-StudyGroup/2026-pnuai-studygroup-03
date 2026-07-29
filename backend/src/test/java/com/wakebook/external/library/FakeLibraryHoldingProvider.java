package com.wakebook.external.library;

import java.util.List;

public class FakeLibraryHoldingProvider implements LibraryHoldingProvider {

    private String lastIsbn;
    private String lastRegion;

    @Override
    public List<LibraryHolding> findHoldings(String isbn, String region) {
        this.lastIsbn = isbn;
        this.lastRegion = region;
        return List.of(new LibraryHolding("부산시립시민도서관", "189.1-기58ㅁ", true));
    }

    public String lastIsbn() {
        return lastIsbn;
    }

    public String lastRegion() {
        return lastRegion;
    }
}
