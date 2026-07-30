package com.wakebook.curation.domain;

import com.wakebook.book.domain.Book;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "curation_books",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_curation_books_curation_isbn",
                columnNames = {"curation_id", "isbn"}
        )
)
public class CurationBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curation_id", nullable = false)
    private Curation curation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "isbn", nullable = false)
    private Book book;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(length = 500)
    private String comment;

    protected CurationBook() {
    }

    public CurationBook(Curation curation, Book book, int displayOrder, String comment) {
        this.curation = curation;
        this.book = book;
        this.displayOrder = displayOrder;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getComment() {
        return comment;
    }
}
