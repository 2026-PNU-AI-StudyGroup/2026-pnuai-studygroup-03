package com.wakebook.external.library;

public interface PopularLoanBookProvider {

    PopularLoanBookResult fetch(PopularLoanBookCriteria criteria);
}
