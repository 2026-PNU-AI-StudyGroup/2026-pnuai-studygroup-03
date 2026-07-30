package com.wakebook.bookshelf.repository;

import com.wakebook.bookshelf.domain.BookshelfBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookshelfBookRepository extends JpaRepository<BookshelfBook, Long> {

    boolean existsByBookshelf_IdAndBook_Isbn(Long bookshelfId, String isbn);

    Optional<BookshelfBook> findByIdAndBookshelf_Id(Long id, Long bookshelfId);
}
