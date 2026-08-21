package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.support.HiddenBookCsvParser;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.external.library.ItemUsageRecord;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 사서가 정보나루에서 다운받은 "장서 대출목록" CSV를 업로드하면, 그 도서관의 저이용·고품질
 * "잠자는 도서" 후보군(hidden_books)을 다시 산출한다.
 *
 * 대상 도서관은 요청값이 아니라 인증된 사서 계정의 소속 도서관으로 결정한다. 남의 도서관 후보군을
 * 덮어쓰는 것을 막기 위해, 요청에 libraryCode가 함께 오면 자기 소속과 같은지만 확인한다.
 *
 * CSV 파싱까지만 요청 안에서 처리하고, 후보마다 외부 API를 호출하는 산출 작업은 비동기로 넘긴다.
 * 업로드 응답은 작업 접수 결과이며 진행 상태는 작업 조회 API로 확인한다.
 */
@Service
@Transactional(readOnly = true)
public class HiddenBookUploadService {

    private final HiddenBookCsvParser csvParser;
    private final UserRepository userRepository;
    private final HiddenBookJobService jobService;
    private final HiddenBookCollector collector;

    public HiddenBookUploadService(
        HiddenBookCsvParser csvParser,
        UserRepository userRepository,
        HiddenBookJobService jobService,
        HiddenBookCollector collector
    ) {
        this.csvParser = csvParser;
        this.userRepository = userRepository;
        this.jobService = jobService;
        this.collector = collector;
    }

    public HiddenBookJob upload(String authenticatedUserId, String requestedLibraryCode, MultipartFile file) {
        User librarian = requireLibrarian(authenticatedUserId);
        String libraryCode = requireOwnLibraryCode(librarian, requestedLibraryCode);
        String libraryName = librarian.getLibraryName();
        validateFile(file);

        List<HiddenBookCandidate> parsedRows = parse(file);
        if (parsedRows.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, "VALIDATION_001",
                "CSV에서 ISBN이 있는 행을 찾지 못했습니다. 정보나루 \"장서 대출목록\" 원본 파일인지 확인해 주세요."
            );
        }

        HiddenBookJob job = jobService.create(
            libraryCode, libraryName, HiddenBookSource.CSV_UPLOAD, librarian.getId(), false
        );
        try {
            collector.collectFromCsv(job.getId(), libraryCode, libraryName, parsedRows);
        } catch (TaskRejectedException e) {
            // 작업은 이미 저장됐으므로, 큐 거절을 실패 상태로 기록해 다음 업로드가 막히지 않게 한다.
            jobService.fail(job.getId(), "작업 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "JOB_005", "작업이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        return job;
    }

    private List<HiddenBookCandidate> parse(MultipartFile file) {
        List<ItemUsageRecord> parsed;
        try {
            parsed = csvParser.parse(file.getInputStream());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "CSV 파일을 읽을 수 없습니다.");
        }
        return parsed.stream()
            .map(record -> HiddenBookCandidate.fromCsv(
                record.isbn(), record.title(), record.author(), record.loanCount(), record.kdcCode()
            ))
            .toList();
    }

    private User requireLibrarian(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        User user = userRepository.findById(userId)
            .orElseThrow(AuthenticationRequiredException::new);
        if (user.getRole() != UserRole.LIBRARIAN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AUTH_002", "사서만 장서 데이터를 등록할 수 있습니다.");
        }
        return user;
    }

    /** 업로드 대상 도서관은 항상 로그인한 사서의 소속이다. 요청에 다른 도서관 코드가 들어오면 거부한다. */
    private String requireOwnLibraryCode(User librarian, String requestedLibraryCode) {
        String ownLibraryCode = librarian.getLibraryCode();
        if (ownLibraryCode == null || ownLibraryCode.isBlank()) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, "AUTH_002", "소속 도서관 코드가 없는 사서 계정입니다."
            );
        }
        if (requestedLibraryCode != null
            && !requestedLibraryCode.isBlank()
            && !ownLibraryCode.equals(requestedLibraryCode.strip())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, "AUTH_002", "소속 도서관의 장서 데이터만 등록할 수 있습니다."
            );
        }
        return ownLibraryCode;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "file은 필수입니다.");
        }
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
}
