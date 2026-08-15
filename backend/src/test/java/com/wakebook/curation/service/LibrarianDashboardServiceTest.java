package com.wakebook.curation.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.curation.domain.Curation;
import com.wakebook.curation.dto.LibrarianDashboardResponse;
import com.wakebook.curation.repository.CurationRepository;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibrarianDashboardServiceTest {

    private static final String LIBRARY_CODE = "121018";

    @Mock
    private UserRepository userRepository;

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    @Mock
    private CurationRepository curationRepository;

    private LibrarianDashboardService librarianDashboardService;

    @BeforeEach
    void setUp() {
        librarianDashboardService =
                new LibrarianDashboardService(userRepository, hiddenBookRepository, curationRepository);
    }

    @Test
    void 도서관코드로_후보군을_집계해_인기_키워드_상위_3개를_반환한다() {
        User librarian = librarian(12L, LIBRARY_CODE);
        when(userRepository.findById(12L)).thenReturn(Optional.of(librarian));
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(
                hiddenBook("9788960867450", List.of("청년", "불안", "관계", "마음돌봄")),
                hiddenBook("9999999999999", List.of("청년", "불안", "관계")),
                hiddenBook("1111111111111", List.of("청년", "불안")),
                hiddenBook("2222222222222", List.of("청년")),
                hiddenBook("3333333333333", List.of("성장"))
        ));
        when(curationRepository.countByUser_IdAndCreatedAtBetween(eq(12L), any(), any())).thenReturn(4L);
        when(curationRepository.findTop5ByUser_IdOrderByCreatedAtDesc(12L)).thenReturn(List.of(
                curation(librarian, 5L, "괜찮지 않아도 괜찮은 우리에게", true)
        ));

        LibrarianDashboardResponse response = librarianDashboardService.getDashboard("12");

        assertThat(response.hiddenBookCount()).isEqualTo(5);
        assertThat(response.popularKeywords()).containsExactly("청년", "불안", "관계");
        assertThat(response.monthlyCurationCount()).isEqualTo(4L);
        assertThat(response.recentCurations()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(5L);
            assertThat(item.title()).isEqualTo("괜찮지 않아도 괜찮은 우리에게");
            assertThat(item.isPublic()).isTrue();
        });
    }

    @Test
    void 도서관코드가_없는_사서는_빈_후보군으로_처리한다() {
        User librarian = librarian(12L, null);
        when(userRepository.findById(12L)).thenReturn(Optional.of(librarian));
        when(curationRepository.countByUser_IdAndCreatedAtBetween(eq(12L), any(), any())).thenReturn(0L);
        when(curationRepository.findTop5ByUser_IdOrderByCreatedAtDesc(12L)).thenReturn(List.of());

        LibrarianDashboardResponse response = librarianDashboardService.getDashboard("12");

        assertThat(response.hiddenBookCount()).isZero();
        assertThat(response.popularKeywords()).isEmpty();
    }

    @Test
    void 유효하지_않은_jwt_subject는_인증오류를_던진다() {
        assertThatThrownBy(() -> librarianDashboardService.getDashboard("not-a-user-id"))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    private static User librarian(Long id, String libraryCode) {
        User user = new User(
                UserRole.LIBRARIAN, "김도서", "librarian@wakebook.kr", "encoded-password",
                "책지기", "부산광역시 금정도서관", libraryCode, "자료운영팀"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static HiddenBook hiddenBook(String isbn, List<String> keywords) {
        return new HiddenBook(
                isbn, LIBRARY_CODE, "부산광역시 금정도서관", "제목-" + isbn, "저자",
                "cover", 1, 80, "이유", keywords
        );
    }

    private static Curation curation(User user, Long id, String title, boolean isPublic) {
        Curation curation = new Curation(user, title, null, isPublic);
        ReflectionTestUtils.setField(curation, "id", id);
        ReflectionTestUtils.setField(curation, "createdAt", LocalDateTime.now());
        return curation;
    }
}
