package com.wakebook.external.library;

import java.util.List;

public interface LibraryHoldingProvider {

    List<LibraryHolding> findHoldings(String isbn, String region);
}
