package com.wakebook.curation.dto;

import java.util.List;

public record LibrarianDashboardResponse(
        long hiddenBookCount,
        long monthlyCurationCount,
        int exhibitionLoanRate,
        List<String> popularKeywords,
        List<CurationSummaryResponse> recentCurations
) {
}
