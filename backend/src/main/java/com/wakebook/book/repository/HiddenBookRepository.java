package com.wakebook.book.repository;

import com.wakebook.book.domain.HiddenBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HiddenBookRepository extends JpaRepository<HiddenBook, Long> {

    List<HiddenBook> findAllByLibraryCode(String libraryCode);

    List<HiddenBook> findAllByLibraryCodeOrderByIdAsc(String libraryCode);

    @Query(value = "SELECT * FROM hidden_books WHERE library_code = :libraryCode ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Optional<HiddenBook> findRandomOneByLibraryCode(@Param("libraryCode") String libraryCode);

    long deleteAllByLibraryCode(String libraryCode);
}
