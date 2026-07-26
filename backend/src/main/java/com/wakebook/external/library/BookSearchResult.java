package com.wakebook.external.library;

import java.util.List;

public record BookSearchResult(List<BookSearchItem> items, long totalCount) {
}
