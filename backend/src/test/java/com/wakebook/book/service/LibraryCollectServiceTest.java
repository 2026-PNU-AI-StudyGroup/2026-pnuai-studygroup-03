package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.LibraryDirectoryProvider;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 도서관 후보군은 사용자별이 아니라 도서관별로 하나뿐이라, 누가 다시 만들면 모두가 그 결과를 본다.
 * 사서가 실제 대출건수로 만들어 둔 후보군을 다른 사람이 덮어쓰지 못하게 막는 규칙을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("도서관 후보군 산출 요청")
class LibraryCollectServiceTest {

    private static final String LIBRARY_CODE = "121020";
    private static final Long USER_ID = 7L;

    @Mock
    private LibraryDirectoryProvider directoryProvider;
    @Mock
    private HiddenBookJobService jobService;
    @Mock
    private HiddenBookCollector collector;
    @Mock
    private HiddenBookRepository hiddenBookRepository;
    @Mock
    private UserRepository userRepository;

    private LibraryCollectService service;

    @BeforeEach
    void setUp() {
        service = new LibraryCollectService(
            directoryProvider, jobService, collector, hiddenBookRepository, userRepository
        );
    }

    @Test
    void 후보군이_없는_도서관은_누구나_만들_수_있다() {
        when(hiddenBookRepository.findTopByLibraryCode(LIBRARY_CODE)).thenReturn(Optional.empty());
        when(jobService.create(anyString(), any(), any(), anyLong(), anyBoolean())).thenReturn(job());

        assertThatCode(() -> service.requestCollect(USER_ID.toString(), LIBRARY_CODE))
            .doesNotThrowAnyException();
        verify(collector).collectFromLibraryApi(any(), anyString());
    }

    @Test
    void API로_만들어진_후보군은_다시_만들_수_있다() {
        when(hiddenBookRepository.findTopByLibraryCode(LIBRARY_CODE))
            .thenReturn(Optional.of(book(HiddenBookSource.LIBRARY_API)));
        when(jobService.create(anyString(), any(), any(), anyLong(), anyBoolean())).thenReturn(job());

        assertThatCode(() -> service.requestCollect(USER_ID.toString(), LIBRARY_CODE))
            .doesNotThrowAnyException();
    }

    @Test
    void 사서가_CSV로_만든_후보군은_일반_이용자가_덮어쓸_수_없다() {
        when(hiddenBookRepository.findTopByLibraryCode(LIBRARY_CODE))
            .thenReturn(Optional.of(book(HiddenBookSource.CSV_UPLOAD)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserRole.USER, null)));

        assertThatThrownBy(() -> service.requestCollect(USER_ID.toString(), LIBRARY_CODE))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("소속 사서만");
        verify(jobService, never()).create(anyString(), any(), any(), anyLong(), anyBoolean());
        verify(collector, never()).collectFromLibraryApi(any(), anyString());
    }

    @Test
    void 다른_도서관_사서도_덮어쓸_수_없다() {
        when(hiddenBookRepository.findTopByLibraryCode(LIBRARY_CODE))
            .thenReturn(Optional.of(book(HiddenBookSource.CSV_UPLOAD)));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user(UserRole.LIBRARIAN, "121024")));

        assertThatThrownBy(() -> service.requestCollect(USER_ID.toString(), LIBRARY_CODE))
            .isInstanceOf(ApiException.class);
        verify(collector, never()).collectFromLibraryApi(any(), anyString());
    }

    @Test
    void 소속_사서는_자기_도서관_후보군을_다시_만들_수_있다() {
        when(hiddenBookRepository.findTopByLibraryCode(LIBRARY_CODE))
            .thenReturn(Optional.of(book(HiddenBookSource.CSV_UPLOAD)));
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user(UserRole.LIBRARIAN, LIBRARY_CODE)));
        when(jobService.create(anyString(), any(), any(), anyLong(), anyBoolean())).thenReturn(job());

        assertThatCode(() -> service.requestCollect(USER_ID.toString(), LIBRARY_CODE))
            .doesNotThrowAnyException();
        verify(collector).collectFromLibraryApi(any(), anyString());
    }

    @Test
    void 대기열이_가득차_비동기_작업이_거절되면_작업을_실패로_기록하고_503을_반환한다() {
        HiddenBookJob job = job();
        when(hiddenBookRepository.findTopByLibraryCode(LIBRARY_CODE)).thenReturn(Optional.empty());
        when(jobService.create(anyString(), any(), any(), anyLong(), anyBoolean())).thenReturn(job);
        org.mockito.Mockito.doThrow(new TaskRejectedException("queue full"))
            .when(collector).collectFromLibraryApi(job.getId(), LIBRARY_CODE);

        assertThatThrownBy(() -> service.requestCollect(USER_ID.toString(), LIBRARY_CODE))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(jobService).fail(job.getId(), "작업 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
    }

    private HiddenBook book(HiddenBookSource source) {
        return new HiddenBook(
            "9788996991342", LIBRARY_CODE, "테스트도서관", "제목", "저자", null,
            0, 80, null, List.of(), source, null, null, null
        );
    }

    private User user(UserRole role, String libraryCode) {
        return new User(role, "이름", "test@example.com", "hash", "닉네임", null, libraryCode, null);
    }

    private HiddenBookJob job() {
        return new HiddenBookJob(LIBRARY_CODE, null, HiddenBookSource.LIBRARY_API, USER_ID);
    }
}
