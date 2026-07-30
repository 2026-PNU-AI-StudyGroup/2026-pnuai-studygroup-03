package com.wakebook.curation.service;

import com.wakebook.book.domain.Book;
import com.wakebook.book.repository.BookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.PageResponse;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.curation.domain.Curation;
import com.wakebook.curation.domain.CurationBook;
import com.wakebook.curation.dto.CurationBookRequest;
import com.wakebook.curation.dto.CurationResponse;
import com.wakebook.curation.dto.CurationSummaryResponse;
import com.wakebook.curation.dto.SaveCurationRequest;
import com.wakebook.curation.repository.CurationRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurationServiceTest {

    @Mock
    private CurationRepository curationRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookDetailProvider bookDetailProvider;

    @Mock
    private UserRepository userRepository;

    private CurationService curationService;

    @BeforeEach
    void setUp() {
        curationService = new CurationService(curationRepository, bookRepository, bookDetailProvider, userRepository);
    }

    @Test
    void 존재하는_도서로_큐레이션을_저장한다() {
        User user = librarian(12L);
        Book book = new Book("9788960867450", "관계에도 연습이 필요합니다", "cover");
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookRepository.findById("9788960867450")).thenReturn(Optional.of(book));
        when(curationRepository.save(any(Curation.class))).thenAnswer(invocation -> {
            Curation curation = invocation.getArgument(0);
            ReflectionTestUtils.setField(curation, "id", 5L);
            return curation;
        });

        CurationResponse response = curationService.create("12", new SaveCurationRequest(
                "괜찮지 않아도 괜찮은 우리에게", "설명", true,
                List.of(new CurationBookRequest("9788960867450", 1, "코멘트"))
        ));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.title()).isEqualTo("괜찮지 않아도 괜찮은 우리에게");
        assertThat(response.bookCount()).isEqualTo(1);
        assertThat(response.books()).singleElement().satisfies(item -> {
            assertThat(item.isbn()).isEqualTo("9788960867450");
            assertThat(item.comment()).isEqualTo("코멘트");
            assertThat(item.displayOrder()).isEqualTo(1);
        });
        verify(bookDetailProvider, never()).fetch(any());
    }

    @Test
    void books_테이블에_없는_도서는_외부_조회로_새로_만든다() {
        User user = librarian(12L);
        BookDetail detail = new BookDetail(
                "9788960867450", "관계에도 연습이 필요합니다", "박상미", "한빛라이프", 2020, "cover", "설명"
        );
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookRepository.findById("9788960867450")).thenReturn(Optional.empty());
        when(bookDetailProvider.fetch("9788960867450")).thenReturn(Optional.of(detail));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(curationRepository.save(any(Curation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        curationService.create("12", new SaveCurationRequest(
                "제목", null, null,
                List.of(new CurationBookRequest("9788960867450", 1, null))
        ));

        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void 존재하지_않는_도서면_BOOK_001_예외() {
        User user = librarian(12L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(bookRepository.findById("0000000000000")).thenReturn(Optional.empty());
        when(bookDetailProvider.fetch("0000000000000")).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> curationService.create("12", new SaveCurationRequest(
                        "제목", null, null,
                        List.of(new CurationBookRequest("0000000000000", 1, null))
                ))
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo("BOOK_001");
    }

    @Test
    void 내_큐레이션_목록을_페이지로_조회한다() {
        User user = librarian(12L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        Curation curation = curation(user, 5L, "괜찮지 않아도 괜찮은 우리에게");
        when(curationRepository.findAllByUser_Id(12L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(curation), PageRequest.of(0, 10), 1));

        PageResponse<CurationSummaryResponse> response = curationService.getCurations("12", 1, 10);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(item ->
                assertThat(item.title()).isEqualTo("괜찮지 않아도 괜찮은 우리에게"));
    }

    @Test
    void 다른_사서의_큐레이션은_조회할_수_없다() {
        User user = librarian(12L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(curationRepository.findByIdAndUser_Id(99L, 12L)).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> curationService.getCuration("12", 99L)
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getCode()).isEqualTo("CURATION_001");
    }

    @Test
    void 큐레이션을_수정하면_도서_구성이_전체_교체된다() {
        User user = librarian(12L);
        Curation curation = curation(user, 5L, "기존 제목");
        Book existingBook = new Book("9788960867450", "기존책", "cover");
        curation.addBook(new CurationBook(curation, existingBook, 1, null));
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(curationRepository.findByIdAndUser_Id(5L, 12L)).thenReturn(Optional.of(curation));
        when(curationRepository.saveAndFlush(any(Curation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Book newBook = new Book("9999999999999", "새책", "cover2");
        when(bookRepository.findById("9999999999999")).thenReturn(Optional.of(newBook));

        CurationResponse response = curationService.update("12", 5L, new SaveCurationRequest(
                "새 제목", "새 설명", false,
                List.of(new CurationBookRequest("9999999999999", 1, "새 코멘트"))
        ));

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.isPublic()).isFalse();
        assertThat(response.books()).singleElement().satisfies(item ->
                assertThat(item.isbn()).isEqualTo("9999999999999"));
    }

    @Test
    void 큐레이션을_삭제한다() {
        User user = librarian(12L);
        Curation curation = curation(user, 5L, "제목");
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(curationRepository.findByIdAndUser_Id(5L, 12L)).thenReturn(Optional.of(curation));

        curationService.delete("12", 5L);

        ArgumentCaptor<Curation> captor = ArgumentCaptor.forClass(Curation.class);
        verify(curationRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(5L);
    }

    @Test
    void 유효하지_않은_jwt_subject는_인증오류를_던진다() {
        assertThatThrownBy(() -> curationService.getCuration("not-a-user-id", 1L))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    private static User librarian(Long id) {
        User user = new User(
                UserRole.LIBRARIAN, "김도서", "librarian@wakebook.kr", "encoded-password",
                "책지기", "부산광역시 금정도서관", "121018", "자료운영팀"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Curation curation(User user, Long id, String title) {
        Curation curation = new Curation(user, title, null, true);
        ReflectionTestUtils.setField(curation, "id", id);
        return curation;
    }
}
