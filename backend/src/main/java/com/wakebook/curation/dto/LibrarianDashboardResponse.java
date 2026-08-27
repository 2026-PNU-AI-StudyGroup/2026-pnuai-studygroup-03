package com.wakebook.curation.dto;

import java.util.List;

/**
 * 전시 도서 대출률은 전시 이후 대출 이력을 수집하는 구조가 없어 계산할 수 없었다.
 * 상수 0을 지표처럼 내려 주는 대신 응답에서 제거했다(docs/tasks.md 참고).
 */
public record LibrarianDashboardResponse(
        long hiddenBookCount,
        long monthlyCurationCount,
        List<String> popularKeywords,
        List<CurationSummaryResponse> recentCurations
) {
}
