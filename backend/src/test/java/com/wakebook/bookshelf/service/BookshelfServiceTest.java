package com.wakebook.bookshelf.service;

import com.wakebook.book.domain.Book;
import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.BookshelfBook;
import com.wakebook.bookshelf.domain.BookshelfType;
import com.wakebook.bookshelf.domain.ReadingStatus;
import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfResponse;
import com.wakebook.bookshelf.repository.BookshelfRepository;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private UserRepository userRepository;

    private BookshelfService bookshelfService;

    @BeforeEach
    void setUp() {
        bookshelfService = new BookshelfService(bookshelfRepository, userRepository);
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
