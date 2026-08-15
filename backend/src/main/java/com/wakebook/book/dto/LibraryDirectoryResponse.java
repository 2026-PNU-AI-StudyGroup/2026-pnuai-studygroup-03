package com.wakebook.book.dto;

import com.wakebook.external.library.LibraryDirectoryItem;

public record LibraryDirectoryResponse(
    String libraryCode,
    String libraryName,
    String address,
    long bookCount
) {

    public static LibraryDirectoryResponse from(LibraryDirectoryItem item) {
        return new LibraryDirectoryResponse(
            item.libraryCode(), item.libraryName(), item.address(), item.bookCount()
        );
    }
}
