package com.wakebook.book.repository;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.dto.LibrarySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HiddenBookRepository extends JpaRepository<HiddenBook, Long> {

    List<HiddenBook> findAllByLibraryCode(String libraryCode);

    // 한 도서관의 후보군은 항상 통째로 교체되므로 도서관명·산출 근거가 행마다 같다. 그래서 함께 묶어도 안전하다.
    @Query("""
        SELECT new com.wakebook.book.dto.LibrarySummaryResponse(
            hiddenBook.libraryCode, hiddenBook.libraryName, hiddenBook.source, COUNT(hiddenBook)
        )
        FROM HiddenBook hiddenBook
        GROUP BY hiddenBook.libraryCode, hiddenBook.libraryName, hiddenBook.source
        ORDER BY COUNT(hiddenBook) DESC, hiddenBook.libraryCode ASC
        """)
    List<LibrarySummaryResponse> findLibrarySummaries();

    List<HiddenBook> findAllByLibraryCodeOrderByIdAsc(String libraryCode);

    /** 도서관의 잠자는 도서 목록. 정보 품질이 좋은 순으로 보여 준다. */
    Page<HiddenBook> findByLibraryCode(String libraryCode, Pageable pageable);

    @Query(value = "SELECT * FROM hidden_books WHERE library_code = :libraryCode ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Optional<HiddenBook> findRandomOneByLibraryCode(@Param("libraryCode") String libraryCode);

    /** 후보군은 통째로 교체되므로 한 도서관의 산출 근거는 행마다 같다. 덮어쓰기 권한 판정에 쓴다. */
    Optional<HiddenBook> findTopByLibraryCode(String libraryCode);

    long deleteAllByLibraryCode(String libraryCode);

    @Query("SELECT DISTINCT hiddenBook.libraryCode FROM HiddenBook hiddenBook ORDER BY hiddenBook.libraryCode")
    List<String> findDistinctLibraryCodes();
}
