package com.wakebook.bookshelf.domain;

import com.wakebook.book.domain.Book;
import com.wakebook.user.domain.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "bookshelves")
public class Bookshelf {

    public static final String DEFAULT_NAME = "읽고 싶은 책";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookshelfType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "bookshelf", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC, id DESC")
    private List<BookshelfBook> books = new ArrayList<>();

    protected Bookshelf() {
    }

    private Bookshelf(User user, String name, String description, BookshelfType type) {
        this.user = user;
        this.name = name;
        this.description = description;
        this.type = type;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Bookshelf createDefault(User user) {
        return new Bookshelf(user, DEFAULT_NAME, null, BookshelfType.DEFAULT);
    }

    public static Bookshelf createCustom(User user, String name, String description) {
        return new Bookshelf(user, name, description, BookshelfType.CUSTOM);
    }

    public BookshelfBook addBook(Book book, ReadingStatus status) {
        BookshelfBook bookshelfBook = new BookshelfBook(this, book, status);
        books.add(bookshelfBook);
        return bookshelfBook;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BookshelfType getType() {
        return type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<BookshelfBook> getBooks() {
        return Collections.unmodifiableList(books);
    }
}
