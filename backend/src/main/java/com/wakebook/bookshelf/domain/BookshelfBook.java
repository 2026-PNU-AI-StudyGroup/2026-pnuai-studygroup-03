package com.wakebook.bookshelf.domain;

import com.wakebook.book.domain.Book;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "bookshelf_books",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookshelf_books_shelf_isbn",
                columnNames = {"bookshelf_id", "isbn"}
        )
)
public class BookshelfBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookshelf_id", nullable = false)
    private Bookshelf bookshelf;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "isbn", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReadingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BookshelfBook() {
    }

    BookshelfBook(Bookshelf bookshelf, Book book, ReadingStatus status) {
        this.bookshelf = bookshelf;
        this.book = book;
        this.status = status;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateStatus(ReadingStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public ReadingStatus getStatus() {
        return status;
    }
}
