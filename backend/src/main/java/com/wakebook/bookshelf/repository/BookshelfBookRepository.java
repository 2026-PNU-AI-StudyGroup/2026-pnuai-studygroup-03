package com.wakebook.bookshelf.repository;

import com.wakebook.bookshelf.domain.BookshelfBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookshelfBookRepository extends JpaRepository<BookshelfBook, Long> {

    boolean existsByBookshelf_IdAndBook_Isbn(Long bookshelfId, String isbn);
}
