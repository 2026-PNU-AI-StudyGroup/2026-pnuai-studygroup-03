package com.wakebook.external.library;

import java.util.List;

public record HoldingCatalogResult(String libraryName, List<HoldingCatalogItem> items, long totalCount) {
}
