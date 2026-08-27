package com.wakebook.external.library;

import java.time.LocalDate;
import java.util.List;

public class FakeLibraryHoldingCatalogProvider implements LibraryHoldingCatalogProvider {

    private String libraryName = "부산광역시 강서도서관";
    private List<HoldingCatalogItem> items = List.of();

    @Override
    public HoldingCatalogResult fetch(
        String libraryCode, LocalDate startDt, LocalDate endDt, int pageNo, int pageSize
    ) {
        // 한 페이지만 채워 두고 두 번째 페이지부터는 비워, 페이지 순회가 끝나도록 한다.
        if (pageNo > 1) {
            return new HoldingCatalogResult(libraryName, List.of(), items.size());
        }
        return new HoldingCatalogResult(libraryName, items, items.size());
    }

    public void setItems(List<HoldingCatalogItem> items) {
        this.items = items;
    }
}
