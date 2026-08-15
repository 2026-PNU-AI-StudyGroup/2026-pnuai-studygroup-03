package com.wakebook.book.repository;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HiddenBookJobRepository extends JpaRepository<HiddenBookJob, Long> {

    Optional<HiddenBookJob> findTopByLibraryCodeOrderByCreatedAtDesc(String libraryCode);

    List<HiddenBookJob> findByLibraryCodeAndStatusIn(String libraryCode, List<HiddenBookJobStatus> statuses);

    Optional<HiddenBookJob> findTopByLibraryCodeAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
        String libraryCode, HiddenBookJobStatus status, LocalDateTime createdAfter
    );

    long countByRequestedByAndCreatedAtAfter(Long requestedBy, LocalDateTime createdAfter);
}
