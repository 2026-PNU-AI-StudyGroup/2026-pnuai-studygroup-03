package com.wakebook.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @Column(nullable = false, length = 20)
    private String isbn;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2048)
    private String cover;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Book() {
    }

    public Book(String isbn, String title, String cover) {
        this.isbn = isbn;
        this.title = title;
        this.cover = cover;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getCover() {
        return cover;
    }
}
