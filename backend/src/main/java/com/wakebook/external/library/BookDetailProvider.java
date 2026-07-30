package com.wakebook.external.library;

import java.util.Optional;

public interface BookDetailProvider {

    Optional<BookDetail> fetch(String isbn);
}
