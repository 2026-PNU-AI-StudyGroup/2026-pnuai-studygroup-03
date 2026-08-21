package com.wakebook.book.repository;

import com.wakebook.book.domain.HiddenBookCollectionLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 후보군 산출은 도서관별로 한 작업만 허용한다. Job 행이 아직 없는 도서관도 있으므로,
 * hidden_book_jobs를 잠그는 대신 항상 존재하게 되는 도서관별 잠금 행을 사용한다.
 */
public interface HiddenBookCollectionLockRepository extends JpaRepository<HiddenBookCollectionLock, String> {

    /**
     * MySQL의 upsert는 같은 library_code 행에 배타 잠금을 잡는다. 이 트랜잭션이 끝날 때까지
     * 다른 요청은 여기서 대기하므로, 뒤이은 활성 Job 확인과 생성이 원자적으로 처리된다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO hidden_book_collection_locks (library_code) VALUES (:libraryCode)
        ON DUPLICATE KEY UPDATE library_code = VALUES(library_code)
        """, nativeQuery = true)
    int lock(@Param("libraryCode") String libraryCode);

}
