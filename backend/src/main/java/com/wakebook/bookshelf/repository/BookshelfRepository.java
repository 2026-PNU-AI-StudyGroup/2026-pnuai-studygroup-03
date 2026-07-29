package com.wakebook.bookshelf.repository;

import com.wakebook.bookshelf.domain.Bookshelf;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookshelfRepository extends JpaRepository<Bookshelf, Long> {

    @EntityGraph(attributePaths = {"books", "books.book"})
    @Query("""
            SELECT DISTINCT bookshelf
            FROM Bookshelf bookshelf
            WHERE bookshelf.user.id = :userId
            """)
    List<Bookshelf> findAllWithBooksByUserId(@Param("userId") Long userId);

    Optional<Bookshelf> findByIdAndUser_Id(Long id, Long userId);
}
