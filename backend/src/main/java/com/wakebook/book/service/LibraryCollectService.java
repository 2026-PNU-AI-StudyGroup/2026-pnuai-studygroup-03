package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.external.library.LibraryDirectoryItem;
import com.wakebook.external.library.LibraryDirectoryProvider;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 사서의 CSV 업로드 없이, 정보나루 API만으로 도서관 후보군을 만들어 준다.
 * 사서가 참여하지 않은 도서관도 이용자가 바로 체험할 수 있게 하기 위한 경로다.
 */
@Service
public class LibraryCollectService {

    private final LibraryDirectoryProvider directoryProvider;
    private final HiddenBookJobService jobService;
    private final HiddenBookCollector collector;
    private final HiddenBookRepository hiddenBookRepository;
    private final UserRepository userRepository;

    public LibraryCollectService(
        LibraryDirectoryProvider directoryProvider,
        HiddenBookJobService jobService,
        HiddenBookCollector collector,
        HiddenBookRepository hiddenBookRepository,
        UserRepository userRepository
    ) {
        this.directoryProvider = directoryProvider;
        this.jobService = jobService;
        this.collector = collector;
        this.hiddenBookRepository = hiddenBookRepository;
        this.userRepository = userRepository;
    }

    public List<LibraryDirectoryItem> findLibraries(String region) {
        if (region == null || region.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "region은 필수입니다.");
        }
        return directoryProvider.findByRegion(region.trim());
    }

    public HiddenBookJob requestCollect(String authenticatedUserId, String libraryCode) {
        if (libraryCode == null || libraryCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "libraryCode는 필수입니다.");
        }
        String validated = libraryCode.trim();
        Long userId = parseUserId(authenticatedUserId);
        ensureMayReplacePool(userId, validated);
        HiddenBookJob job = jobService.create(
            validated, null, HiddenBookSource.LIBRARY_API, userId, true
        );
        try {
            collector.collectFromLibraryApi(job.getId(), validated);
        } catch (TaskRejectedException e) {
            jobService.fail(job.getId(), "작업 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "JOB_005", "작업이 많습니다. 잠시 후 다시 시도해 주세요."
            );
        }
        return job;
    }

    /**
     * 사서가 CSV로 만들어 둔 후보군은 실제 대출건수로 고른 것이라, 순위 기반 API 산출보다 정확하다.
     * 그걸 다른 사람이 덮어쓰면 도서관 입장에서는 데이터가 조용히 나빠진다.
     * 그래서 CSV로 만들어진 후보군은 그 도서관 사서만 다시 만들 수 있게 한다.
     *
     * 후보군이 아직 없는 도서관은 누구나 만들 수 있다. 이용자가 원하는 도서관을 직접 열어 보는 것이
     * 이 경로를 만든 이유이기 때문이다. API·데모 시드로 만들어진 후보군도 7일 쿨다운으로 충분하다.
     */
    private void ensureMayReplacePool(Long userId, String libraryCode) {
        HiddenBook existing = hiddenBookRepository.findTopByLibraryCode(libraryCode).orElse(null);
        if (existing == null || existing.getSource() != HiddenBookSource.CSV_UPLOAD) {
            return;
        }
        User user = userRepository.findById(userId)
            .orElseThrow(AuthenticationRequiredException::new);
        boolean ownLibraryLibrarian = user.getRole() == UserRole.LIBRARIAN
            && libraryCode.equals(user.getLibraryCode());
        if (!ownLibraryLibrarian) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, "AUTH_002",
                "이 도서관의 잠자는 도서는 사서가 등록한 장서 데이터로 만들어졌습니다. 소속 사서만 다시 만들 수 있습니다."
            );
        }
    }

    private Long parseUserId(String authenticatedUserId) {
        try {
            return Long.parseLong(authenticatedUserId);
        } catch (NumberFormatException e) {
            throw new AuthenticationRequiredException();
        }
    }
}
