package com.wakebook.book.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 도서관별 후보군 산출 요청을 직렬화하기 위한 DB 잠금 행. */
@Entity
@Table(name = "hidden_book_collection_locks")
public class HiddenBookCollectionLock {

    @Id
    @Column(name = "library_code", length = 20)
    private String libraryCode;

    protected HiddenBookCollectionLock() {
    }
}
