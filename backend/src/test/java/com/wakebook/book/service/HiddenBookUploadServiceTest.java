package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.support.HiddenBookCsvParser;
import com.wakebook.common.ApiException;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HiddenBookUploadServiceTest {

    private static final String CSV_HEADER =
        "번호,도서명,저자,출판사,발행년도,ISBN,세트 ISBN,부가기호,권,주제분류번호,도서권수,대출건수,등록일자\n";
    private static final String CSV_ROW =
        "\"1\",\"관계에도 연습이 필요합니다\",\"박상미\",\"빌리버튼\",\"2018\",\"9788960867450\",\"\",\"\",\"\",\"813.7\",\"1\",\"1\",\"2026-06-24\"\n";

    @Mock
    private UserRepository userRepository;

    @Mock
    private HiddenBookJobService jobService;

    @Mock
    private HiddenBookCollector collector;

    private HiddenBookUploadService hiddenBookUploadService;

    @BeforeEach
    void setUp() {
        hiddenBookUploadService = new HiddenBookUploadService(
            new HiddenBookCsvParser(), userRepository, jobService, collector
        );
    }

    @Test
    void 업로드는_파싱만_하고_산출_작업을_비동기로_넘긴다() {
        givenLibrarian(UserRole.LIBRARIAN, "121018");
        HiddenBookJob job = new HiddenBookJob(
            "121018", "부산광역시 금정도서관", HiddenBookSource.CSV_UPLOAD, 12L, LocalDateTime.now()
        );
        // 사서의 CSV 업로드는 스스로 정확한 데이터를 넣는 일이라 쿨다운·일일 제한을 적용하지 않는다.
        when(jobService.create(
            eq("121018"), eq("부산광역시 금정도서관"), eq(HiddenBookSource.CSV_UPLOAD), any(), eq(false)
        )).thenReturn(job);

        HiddenBookJob created = hiddenBookUploadService.upload("12", "121018", csvFile(CSV_HEADER + CSV_ROW));

        assertThat(created).isSameAs(job);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HiddenBookCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(collector).collectFromCsv(any(), eq("121018"), eq("부산광역시 금정도서관"), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).isbn()).isEqualTo("9788960867450");
        assertThat(captor.getValue().get(0).loanCount()).isEqualTo(1);
        assertThat(captor.getValue().get(0).kdcCode()).isEqualTo("813.7");
    }

    @Test
    void libraryCode를_생략하면_사서의_소속_도서관으로_접수한다() {
        givenLibrarian(UserRole.LIBRARIAN, "121018");
        when(jobService.create(eq("121018"), any(), any(), any(), eq(false)))
            .thenReturn(new HiddenBookJob(
                "121018", "부산광역시 금정도서관", HiddenBookSource.CSV_UPLOAD, 12L, LocalDateTime.now()
            ));

        hiddenBookUploadService.upload("12", null, csvFile(CSV_HEADER + CSV_ROW));

        verify(collector).collectFromCsv(any(), eq("121018"), any(), any());
    }

    @Test
    void 다른_도서관_코드로_업로드하면_AUTH_002_예외() {
        givenLibrarian(UserRole.LIBRARIAN, "121018");

        assertThatThrownBy(() -> hiddenBookUploadService.upload("12", "999999", csvFile(CSV_HEADER + CSV_ROW)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("소속 도서관");
        verify(collector, never()).collectFromCsv(any(), any(), any(), any());
    }

    @Test
    void 사서가_아니면_AUTH_002_예외() {
        givenLibrarian(UserRole.USER, null);

        assertThatThrownBy(() -> hiddenBookUploadService.upload("12", "121018", csvFile(CSV_HEADER + CSV_ROW)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("사서만");
    }

    @Test
    void 소속_도서관_코드가_없는_사서면_AUTH_002_예외() {
        givenLibrarian(UserRole.LIBRARIAN, null);

        assertThatThrownBy(() -> hiddenBookUploadService.upload("12", null, csvFile(CSV_HEADER + CSV_ROW)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("소속 도서관 코드");
    }

    @Test
    void 파일이_비어있으면_VALIDATION_001_예외() {
        givenLibrarian(UserRole.LIBRARIAN, "121018");
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> hiddenBookUploadService.upload("12", "121018", emptyFile))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("file");
    }

    @Test
    void ISBN이_있는_행이_없으면_VALIDATION_001_예외() {
        givenLibrarian(UserRole.LIBRARIAN, "121018");

        assertThatThrownBy(() -> hiddenBookUploadService.upload("12", "121018", csvFile(CSV_HEADER)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("ISBN");
    }

    @Test
    void 대기열이_가득차면_작업을_실패로_기록하고_503을_반환한다() {
        givenLibrarian(UserRole.LIBRARIAN, "121018");
        HiddenBookJob job = new HiddenBookJob(
            "121018", "부산광역시 금정도서관", HiddenBookSource.CSV_UPLOAD, 12L, LocalDateTime.now()
        );
        when(jobService.create(eq("121018"), any(), any(), any(), eq(false))).thenReturn(job);
        org.mockito.Mockito.doThrow(new TaskRejectedException("queue full"))
            .when(collector).collectFromCsv(eq(job.getId()), eq("121018"), any(), any());

        assertThatThrownBy(() -> hiddenBookUploadService.upload("12", null, csvFile(CSV_HEADER + CSV_ROW)))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(jobService).fail(job.getId(), "작업 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "library.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private void givenLibrarian(UserRole role, String libraryCode) {
        User user = new User(
            role, "김사서", "librarian@wakebook.kr", "hash", null,
            libraryCode == null ? null : "부산광역시 금정도서관", libraryCode,
            libraryCode == null ? null : "자료운영팀"
        );
        lenient().when(userRepository.findById(12L)).thenReturn(Optional.of(user));
    }
}
