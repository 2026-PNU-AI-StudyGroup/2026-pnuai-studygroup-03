package com.wakebook.book.repository;

import com.wakebook.book.domain.HiddenBookCollectionLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HiddenBookCollectionLockRepository extends JpaRepository<HiddenBookCollectionLock, String> {

    @Modifying
    @Query(value = """
        INSERT INTO hidden_book_collection_locks (library_code) VALUES (:libraryCode)
        ON DUPLICATE KEY UPDATE library_code = VALUES(library_code)
        """, nativeQuery = true)
    int lock(@Param("libraryCode") String libraryCode);
}
