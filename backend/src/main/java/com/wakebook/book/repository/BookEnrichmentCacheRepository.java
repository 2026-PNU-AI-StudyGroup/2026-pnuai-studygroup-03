package com.wakebook.book.repository;

import com.wakebook.book.domain.BookEnrichmentCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookEnrichmentCacheRepository extends JpaRepository<BookEnrichmentCache, String> {

    List<BookEnrichmentCache> findAllByIsbnIn(Collection<String> isbns);
}
