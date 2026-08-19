package com.wakebook.curation.repository;

import com.wakebook.curation.domain.Curation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CurationRepository extends JpaRepository<Curation, Long> {

    Page<Curation> findAllByUser_Id(Long userId, Pageable pageable);

    Optional<Curation> findByIdAndUser_Id(Long id, Long userId);

    Page<Curation> findAllByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    Optional<Curation> findByIdAndIsPublicTrue(Long id);

    List<Curation> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByUser_IdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
