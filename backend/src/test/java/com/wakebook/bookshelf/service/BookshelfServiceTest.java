package com.wakebook.bookshelf.service;

import com.wakebook.book.domain.Book;
import com.wakebook.book.repository.BookRepository;
import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.BookshelfBook;
import com.wakebook.bookshelf.domain.BookshelfType;
import com.wakebook.bookshelf.domain.ReadingStatus;
import com.wakebook.bookshelf.dto.AddBookshelfBookRequest;
import com.wakebook.bookshelf.dto.BookshelfBookResponse;
import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfResponse;
import com.wakebook.bookshelf.dto.UpdateBookshelfRequest;
import com.wakebook.bookshelf.dto.UpdateBookshelfResponse;
import com.wakebook.bookshelf.dto.UpdateReadingStatusRequest;
import com.wakebook.bookshelf.repository.BookshelfBookRepository;
import com.wakebook.bookshelf.repository.BookshelfRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookshelfServiceTest {

    @Mock
    private BookshelfRepository bookshelfRepository;

    @Mock
    private BookshelfBookRepository bookshelfBookRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookDetailProvider bookDetailProvider;

    @Mock
    private UserRepository userRepository;

    private BookshelfService bookshelfService;

    @BeforeEach
    void setUp() {
        bookshelfService = new BookshelfService(
                bookshelfRepository,
                bookshelfBookRepository,
                bookRepository,
                bookDetailProvider,
                userRepository
        );
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersBookshelvesInStableOrder() {
        User user = user(12L);
        Bookshelf custom = Bookshelf.createCustom(user, "마음을 돌보는 책", "천천히 읽을 책");
        ReflectionTestUtils.setField(custom, "id", 2L);

        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        Book book = new Book(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                "https://example.com/cover.jpg"
        );
        BookshelfBook entry = defaultShelf.addBook(book, ReadingStatus.WISH);
        ReflectionTestUtils.setField(entry, "id", 101L);

        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findAllWithBooksByUserId(12L))
                .thenReturn(List.of(custom, defaultShelf));

        List<BookshelfResponse> result = bookshelfService.getBookshelves("12");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).type()).isEqualTo(BookshelfType.DEFAULT);
        assertThat(result.get(0).bookCount()).isEqualTo(1);
        assertThat(result.get(0).books()).singleElement().satisfies(savedBook -> {
            assertThat(savedBook.id()).isEqualTo(101L);
            assertThat(savedBook.isbn()).isEqualTo("9788960867450");
            assertThat(savedBook.title()).isEqualTo("관계에도 연습이 필요합니다");
            assertThat(savedBook.status()).isEqualTo(ReadingStatus.WISH);
            assertThat(savedBook.cover()).isEqualTo("https://example.com/cover.jpg");
        });
        assertThat(result.get(1).id()).isEqualTo(2L);
        assertThat(result.get(1).type()).isEqualTo(BookshelfType.CUSTOM);

        verify(bookshelfRepository).findAllWithBooksByUserId(12L);
    }

    @Test
    void emptyDefaultBookshelfReturnsAnEmptyBookListAndZeroCount() {
        User user = user(12L);
        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findAllWithBooksByUserId(12L))
                .thenReturn(List.of(defaultShelf));

        BookshelfResponse result = bookshelfService.getBookshelves("12").getFirst();

        assertThat(result.name()).isEqualTo(Bookshelf.DEFAULT_NAME);
        assertThat(result.bookCount()).isZero();
        assertThat(result.books()).isEmpty();
    }

    @Test
    void invalidJwtSubjectIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> bookshelfService.getBookshelves("not-a-user-id"))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("로그인이 필요합니다.");

        verifyNoInteractions(userRepository, bookshelfRepository);
    }

    @Test
    void deletedOrUnknownUserIsRejectedBeforeBookshelfLookup() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookshelfService.getBookshelves("999"))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("로그인이 필요합니다.");

        verify(bookshelfRepository, never()).findAllWithBooksByUserId(999L);
    }

    @Test
    void createsTheDefaultBookshelfForANewUser() {
        User user = user(12L);
        ArgumentCaptor<Bookshelf> captor = ArgumentCaptor.forClass(Bookshelf.class);

        bookshelfService.createDefaultBookshelf(user);

        verify(bookshelfRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("읽고 싶은 책");
        assertThat(captor.getValue().getType()).isEqualTo(BookshelfType.DEFAULT);
        assertThat(captor.getValue().getBooks()).isEmpty();
    }

    @Test
    void createsACustomCollectionForTheAuthenticatedUser() {
        User user = user(12L);
        ArgumentCaptor<Bookshelf> captor = ArgumentCaptor.forClass(Bookshelf.class);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.save(any(Bookshelf.class))).thenAnswer(invocation -> {
            Bookshelf bookshelf = invocation.getArgument(0);
            ReflectionTestUtils.setField(bookshelf, "id", 2L);
            return bookshelf;
        });

        CreateBookshelfResponse result = bookshelfService.createBookshelf(
                "12",
                new CreateBookshelfRequest(
                        "  마음을 돌보는 책  ",
                        "  천천히 읽고 싶은 책 모음  "
                )
        );

        verify(bookshelfRepository).save(captor.capture());
        Bookshelf saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("마음을 돌보는 책");
        assertThat(saved.getDescription()).isEqualTo("천천히 읽고 싶은 책 모음");
        assertThat(saved.getType()).isEqualTo(BookshelfType.CUSTOM);
        assertThat(saved.getBooks()).isEmpty();
        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.name()).isEqualTo("마음을 돌보는 책");
        assertThat(result.description()).isEqualTo("천천히 읽고 싶은 책 모음");
        assertThat(result.type()).isEqualTo(BookshelfType.CUSTOM);
    }

    @Test
    void storesABlankOptionalDescriptionAsNull() {
        User user = user(12L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.save(any(Bookshelf.class))).thenAnswer(invocation -> {
            Bookshelf bookshelf = invocation.getArgument(0);
            ReflectionTestUtils.setField(bookshelf, "id", 2L);
            return bookshelf;
        });

        CreateBookshelfResponse result = bookshelfService.createBookshelf(
                "12",
                new CreateBookshelfRequest("새 컬렉션", "   ")
        );

        assertThat(result.description()).isNull();
    }

    @Test
    void doesNotCreateACollectionForAnUnknownUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookshelfService.createBookshelf(
                "999",
                new CreateBookshelfRequest("새 컬렉션", null)
        ))
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("로그인이 필요합니다.");

        verify(bookshelfRepository, never()).save(any());
    }

    @Test
    void addsAnExistingBookToAnOwnedBookshelf() {
        User user = user(12L);
        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        Book book = new Book(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                "https://example.com/cover.jpg"
        );
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(1L, 12L))
                .thenReturn(Optional.of(defaultShelf));
        when(bookshelfBookRepository.existsByBookshelf_IdAndBook_Isbn(
                1L,
                "9788960867450"
        )).thenReturn(false);
        when(bookRepository.findById("9788960867450")).thenReturn(Optional.of(book));
        when(bookshelfBookRepository.save(any(BookshelfBook.class)))
                .thenAnswer(invocation -> {
                    BookshelfBook entry = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entry, "id", 101L);
                    return entry;
                });

        BookshelfBookResponse result = bookshelfService.addBook(
                "12",
                1L,
                new AddBookshelfBookRequest("  9788960867450  ", ReadingStatus.WISH)
        );

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.isbn()).isEqualTo("9788960867450");
        assertThat(result.title()).isEqualTo("관계에도 연습이 필요합니다");
        assertThat(result.status()).isEqualTo(ReadingStatus.WISH);
        assertThat(result.cover()).isEqualTo("https://example.com/cover.jpg");
        assertThat(defaultShelf.getBooks()).hasSize(1);
        verifyNoInteractions(bookDetailProvider);
    }

    @Test
    void fetchesAndPersistsBookMetadataWhenTheBookIsNotStoredYet() {
        User user = user(12L);
        Bookshelf custom = Bookshelf.createCustom(user, "마음을 돌보는 책", null);
        ReflectionTestUtils.setField(custom, "id", 2L);
        BookDetail detail = new BookDetail(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                "박상미",
                "한빛라이프",
                2020,
                "https://example.com/cover.jpg",
                "건강한 관계를 위한 책"
        );
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(2L, 12L))
                .thenReturn(Optional.of(custom));
        when(bookshelfBookRepository.existsByBookshelf_IdAndBook_Isbn(
                2L,
                "9788960867450"
        )).thenReturn(false);
        when(bookRepository.findById("9788960867450")).thenReturn(Optional.empty());
        when(bookDetailProvider.fetch("9788960867450")).thenReturn(Optional.of(detail));
        when(bookRepository.save(any(Book.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(bookshelfBookRepository.save(any(BookshelfBook.class)))
                .thenAnswer(invocation -> {
                    BookshelfBook entry = invocation.getArgument(0);
                    ReflectionTestUtils.setField(entry, "id", 102L);
                    return entry;
                });

        BookshelfBookResponse result = bookshelfService.addBook(
                "12",
                2L,
                new AddBookshelfBookRequest("9788960867450", ReadingStatus.READING)
        );

        ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(bookCaptor.capture());
        assertThat(bookCaptor.getValue().getIsbn()).isEqualTo("9788960867450");
        assertThat(bookCaptor.getValue().getTitle()).isEqualTo("관계에도 연습이 필요합니다");
        assertThat(result.status()).isEqualTo(ReadingStatus.READING);
    }

    @Test
    void rejectsADuplicateBookInTheSameBookshelf() {
        User user = user(12L);
        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(1L, 12L))
                .thenReturn(Optional.of(defaultShelf));
        when(bookshelfBookRepository.existsByBookshelf_IdAndBook_Isbn(
                1L,
                "9788960867450"
        )).thenReturn(true);

        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> bookshelfService.addBook(
                        "12",
                        1L,
                        new AddBookshelfBookRequest(
                                "9788960867450",
                                ReadingStatus.WISH
                        )
                )
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getCode()).isEqualTo("BOOKSHELF_003");
        assertThat(exception).hasMessage("이미 해당 책장에 저장된 도서입니다.");
        verifyNoInteractions(bookRepository, bookDetailProvider);
        verify(bookshelfBookRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownBook() {
        User user = user(12L);
        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(1L, 12L))
                .thenReturn(Optional.of(defaultShelf));
        when(bookRepository.findById("0000000000000")).thenReturn(Optional.empty());
        when(bookDetailProvider.fetch("0000000000000")).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> bookshelfService.addBook(
                        "12",
                        1L,
                        new AddBookshelfBookRequest(
                                "0000000000000",
                                ReadingStatus.WISH
                        )
                )
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo("BOOK_001");
        verify(bookRepository, never()).save(any());
        verify(bookshelfBookRepository, never()).save(any());
    }

    @Test
    void updatesTheReadingStatusOfAnOwnedBookshelfBook() {
        User user = user(12L);
        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        Book book = new Book(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                "https://example.com/cover.jpg"
        );
        BookshelfBook entry = defaultShelf.addBook(book, ReadingStatus.WISH);
        ReflectionTestUtils.setField(entry, "id", 101L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(1L, 12L))
                .thenReturn(Optional.of(defaultShelf));
        when(bookshelfBookRepository.findByIdAndBookshelf_Id(101L, 1L))
                .thenReturn(Optional.of(entry));

        BookshelfBookResponse result = bookshelfService.updateReadingStatus(
                "12",
                1L,
                101L,
                new UpdateReadingStatusRequest(ReadingStatus.READING)
        );

        assertThat(entry.getStatus()).isEqualTo(ReadingStatus.READING);
        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.isbn()).isEqualTo("9788960867450");
        assertThat(result.status()).isEqualTo(ReadingStatus.READING);
        verify(bookshelfBookRepository, never()).save(any());
    }

    @Test
    void rejectsABookshelfBookThatDoesNotBelongToTheShelf() {
        User user = user(12L);
        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(1L, 12L))
                .thenReturn(Optional.of(defaultShelf));
        when(bookshelfBookRepository.findByIdAndBookshelf_Id(999L, 1L))
                .thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> bookshelfService.updateReadingStatus(
                        "12",
                        1L,
                        999L,
                        new UpdateReadingStatusRequest(ReadingStatus.COMPLETED)
                )
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo("BOOKSHELF_004");
        assertThat(exception).hasMessage("책장에 저장된 도서를 찾을 수 없습니다.");
    }

    @Test
    void updatesAnOwnedCustomCollection() {
        User user = user(12L);
        Bookshelf custom = Bookshelf.createCustom(user, "기존 이름", "기존 설명");
        ReflectionTestUtils.setField(custom, "id", 2L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(2L, 12L))
                .thenReturn(Optional.of(custom));

        UpdateBookshelfResponse result = bookshelfService.updateBookshelf(
                "12",
                2L,
                new UpdateBookshelfRequest(
                        "  천천히 읽을 책  ",
                        "  이번 달에 읽을 책  "
                )
        );

        assertThat(custom.getName()).isEqualTo("천천히 읽을 책");
        assertThat(custom.getDescription()).isEqualTo("이번 달에 읽을 책");
        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.name()).isEqualTo("천천히 읽을 책");
        assertThat(result.description()).isEqualTo("이번 달에 읽을 책");
        assertThat(result.type()).isEqualTo(BookshelfType.CUSTOM);
        verify(bookshelfRepository, never()).save(any());
    }

    @Test
    void deletesAnOwnedCustomCollection() {
        User user = user(12L);
        Bookshelf custom = Bookshelf.createCustom(user, "정리할 책", null);
        ReflectionTestUtils.setField(custom, "id", 2L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(2L, 12L))
                .thenReturn(Optional.of(custom));

        bookshelfService.deleteBookshelf("12", 2L);

        verify(bookshelfRepository).delete(custom);
    }

    @Test
    void hidesAnotherUsersOrMissingCollectionAsNotFound() {
        User user = user(12L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(99L, 12L))
                .thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> bookshelfService.updateBookshelf(
                        "12",
                        99L,
                        new UpdateBookshelfRequest("새 이름", null)
                )
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo("BOOKSHELF_001");
        assertThat(exception).hasMessage("컬렉션을 찾을 수 없습니다.");
        verify(bookshelfRepository, never()).delete(any());
    }

    @Test
    void preventsUpdatingOrDeletingTheDefaultBookshelf() {
        User user = user(12L);
        Bookshelf defaultShelf = Bookshelf.createDefault(user);
        ReflectionTestUtils.setField(defaultShelf, "id", 1L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookshelfRepository.findByIdAndUser_Id(1L, 12L))
                .thenReturn(Optional.of(defaultShelf));

        ApiException updateException = catchThrowableOfType(
                ApiException.class,
                () -> bookshelfService.updateBookshelf(
                        "12",
                        1L,
                        new UpdateBookshelfRequest("바꿀 이름", null)
                )
        );
        ApiException deleteException = catchThrowableOfType(
                ApiException.class,
                () -> bookshelfService.deleteBookshelf("12", 1L)
        );

        assertThat(updateException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(updateException.getCode()).isEqualTo("BOOKSHELF_002");
        assertThat(deleteException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deleteException.getCode()).isEqualTo("BOOKSHELF_002");
        assertThat(defaultShelf.getName()).isEqualTo(Bookshelf.DEFAULT_NAME);
        verify(bookshelfRepository, never()).delete(any());
    }

    private static User user(Long id) {
        User user = new User(
                UserRole.USER,
                "김독자",
                "reader@wakebook.kr",
                "encoded-password",
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
