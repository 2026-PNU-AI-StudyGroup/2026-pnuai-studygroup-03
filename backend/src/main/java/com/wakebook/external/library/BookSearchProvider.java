package com.wakebook.external.library;

public interface BookSearchProvider {

    BookSearchResult search(BookSearchCriteria criteria);
}
