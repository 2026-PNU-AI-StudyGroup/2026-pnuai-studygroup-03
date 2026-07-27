package com.wakebook.external.library;

import java.util.List;

public record PopularLoanBookResult(List<PopularLoanBookItem> items, long totalCount) {
}
