package com.wakebook.bookshelf.service;

import com.wakebook.book.domain.Book;
import com.wakebook.book.repository.BookRepository;
import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.BookshelfType;
import com.wakebook.bookshelf.dto.AddBookshelfBookRequest;
import com.wakebook.bookshelf.dto.BookshelfBookResponse;
import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfResponse;
import com.wakebook.bookshelf.dto.UpdateBookshelfRequest;
import com.wakebook.bookshelf.dto.UpdateBookshelfResponse;
import com.wakebook.bookshelf.repository.BookshelfBookRepository;
import com.wakebook.bookshelf.repository.BookshelfRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookshelfService {

    private static final Comparator<Bookshelf> BOOKSHELF_ORDER =
            Comparator.comparingInt((Bookshelf bookshelf) ->
                            bookshelf.getType() == BookshelfType.DEFAULT ? 0 : 1)
                    .thenComparing(Bookshelf::getCreatedAt)
                    .thenComparing(Bookshelf::getId);

    private final BookshelfRepository bookshelfRepository;
    private final BookshelfBookRepository bookshelfBookRepository;
    private final BookRepository bookRepository;
    private final BookDetailProvider bookDetailProvider;
    private final UserRepository userRepository;

    public BookshelfService(
            BookshelfRepository bookshelfRepository,
            BookshelfBookRepository bookshelfBookRepository,
            BookRepository bookRepository,
            BookDetailProvider bookDetailProvider,
            UserRepository userRepository
    ) {
        this.bookshelfRepository = bookshelfRepository;
        this.bookshelfBookRepository = bookshelfBookRepository;
        this.bookRepository = bookRepository;
        this.bookDetailProvider = bookDetailProvider;
        this.userRepository = userRepository;
    }

    public List<BookshelfResponse> getBookshelves(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);

        return bookshelfRepository.findAllWithBooksByUserId(userId).stream()
                .sorted(BOOKSHELF_ORDER)
                .map(BookshelfResponse::from)
                .toList();
    }

    @Transactional
    public void createDefaultBookshelf(User user) {
        bookshelfRepository.save(Bookshelf.createDefault(user));
    }

    @Transactional
    public CreateBookshelfResponse createBookshelf(
            String authenticatedUserId,
            CreateBookshelfRequest request
    ) {
        Long userId = parseUserId(authenticatedUserId);
        User user = userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        Bookshelf bookshelf = Bookshelf.createCustom(
                user,
                request.name().strip(),
                nullableStrip(request.description())
        );

        return CreateBookshelfResponse.from(bookshelfRepository.save(bookshelf));
    }

    @Transactional
    public BookshelfBookResponse addBook(
            String authenticatedUserId,
            Long shelfId,
            AddBookshelfBookRequest request
    ) {
        Long userId = requireAuthenticatedUserId(authenticatedUserId);
        Bookshelf bookshelf = findOwnedBookshelf(shelfId, userId);
        String isbn = request.isbn().strip();
        ensureBookIsNotAlreadySaved(bookshelf.getId(), isbn);
        Book book = findOrCreateBook(isbn);

        return BookshelfBookResponse.from(
                bookshelfBookRepository.save(bookshelf.addBook(book, request.status()))
        );
    }

    @Transactional
    public UpdateBookshelfResponse updateBookshelf(
            String authenticatedUserId,
            Long shelfId,
            UpdateBookshelfRequest request
    ) {
        Long userId = requireAuthenticatedUserId(authenticatedUserId);
        Bookshelf bookshelf = findOwnedBookshelf(shelfId, userId);
        ensureCustomBookshelf(bookshelf);
        bookshelf.update(
                request.name().strip(),
                nullableStrip(request.description())
        );
        return UpdateBookshelfResponse.from(bookshelf);
    }

    @Transactional
    public void deleteBookshelf(String authenticatedUserId, Long shelfId) {
        Long userId = requireAuthenticatedUserId(authenticatedUserId);
        Bookshelf bookshelf = findOwnedBookshelf(shelfId, userId);
        ensureCustomBookshelf(bookshelf);
        bookshelfRepository.delete(bookshelf);
    }

    private Long requireAuthenticatedUserId(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        return userId;
    }

    private Bookshelf findOwnedBookshelf(Long shelfId, Long userId) {
        return bookshelfRepository.findByIdAndUser_Id(shelfId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BOOKSHELF_001",
                        "컬렉션을 찾을 수 없습니다."
                ));
    }

    private static void ensureCustomBookshelf(Bookshelf bookshelf) {
        if (bookshelf.getType() != BookshelfType.CUSTOM) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "BOOKSHELF_002",
                    "기본 책장은 수정하거나 삭제할 수 없습니다."
            );
        }
    }

    private void ensureBookIsNotAlreadySaved(Long shelfId, String isbn) {
        if (bookshelfBookRepository.existsByBookshelf_IdAndBook_Isbn(shelfId, isbn)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BOOKSHELF_003",
                    "이미 해당 책장에 저장된 도서입니다."
            );
        }
    }

    private Book findOrCreateBook(String isbn) {
        return bookRepository.findById(isbn)
                .orElseGet(() -> createBook(isbn));
    }

    private Book createBook(String isbn) {
        BookDetail detail = bookDetailProvider.fetch(isbn)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BOOK_001",
                        "도서를 찾을 수 없습니다."
                ));
        return bookRepository.save(new Book(isbn, detail.title(), detail.cover()));
    }

    private static Long parseUserId(String authenticatedUserId) {
        try {
            long userId = Long.parseLong(authenticatedUserId);
            if (userId <= 0) {
                throw new AuthenticationRequiredException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new AuthenticationRequiredException();
        }
    }

    private static String nullableStrip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
