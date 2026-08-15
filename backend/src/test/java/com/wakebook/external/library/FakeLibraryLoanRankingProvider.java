package com.wakebook.external.library;

import java.time.LocalDate;
import java.util.Set;

public class FakeLibraryLoanRankingProvider implements LibraryLoanRankingProvider {

    private Set<String> rankedIsbns = Set.of();

    @Override
    public Set<String> fetchRankedIsbns(String libraryCode, LocalDate startDt, LocalDate endDt) {
        return rankedIsbns;
    }

    public void setRankedIsbns(Set<String> rankedIsbns) {
        this.rankedIsbns = rankedIsbns;
    }
}
