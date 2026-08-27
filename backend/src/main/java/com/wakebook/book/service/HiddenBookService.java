package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.dto.LibrarySummaryResponse;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wakebook.book.dto.HiddenBookResponse;
import com.wakebook.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class HiddenBookService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HiddenBookRepository hiddenBookRepository;
    private final HiddenBookReasonService reasonService;

    public HiddenBookService(HiddenBookRepository hiddenBookRepository, HiddenBookReasonService reasonService) {
        this.hiddenBookRepository = hiddenBookRepository;
        this.reasonService = reasonService;
    }

    public List<LibrarySummaryResponse> getLibraries() {
        return hiddenBookRepository.findLibrarySummaries();
    }

    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 도서관의 잠자는 도서 전체 목록. DB만 읽으므로 정보나루를 호출하지 않는다.
     * 추천 이유(reason)는 아직 생성되지 않았을 수 있어, 목록에서는 정보나루 소개글을 함께 내려 준다.
     */
    public PageResponse<HiddenBookResponse> getHiddenBooks(String libraryCode, int page, int size) {
        String validatedLibraryCode = validateLibraryCode(libraryCode);
        int validatedPage = Math.max(1, page);
        int validatedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        var result = hiddenBookRepository.findByLibraryCode(
            validatedLibraryCode,
            PageRequest.of(validatedPage - 1, validatedSize,
                Sort.by(Sort.Order.desc("qualityScore"), Sort.Order.asc("id")))
        );

        return PageResponse.of(
            result.getContent().stream().map(HiddenBookResponse::from).toList(),
            validatedPage, validatedSize, result.getTotalElements()
        );
    }

    public HiddenBook getTodayBook(String libraryCode) {
        String validatedLibraryCode = validateLibraryCode(libraryCode);
        List<HiddenBook> pool = hiddenBookRepository.findAllByLibraryCodeOrderByIdAsc(validatedLibraryCode);
        if (pool.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "오늘의 잠자는 책 후보가 없습니다.");
        }
        int index = (int) (LocalDate.now(SEOUL).toEpochDay() % pool.size());
        return reasonService.ensureReason(pool.get(index));
    }

    public HiddenBook getRandomBook(String libraryCode) {
        String validatedLibraryCode = validateLibraryCode(libraryCode);
        HiddenBook picked = hiddenBookRepository.findRandomOneByLibraryCode(validatedLibraryCode)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "우연히 발견할 잠자는 책 후보가 없습니다."));
        return reasonService.ensureReason(picked);
    }

    private String validateLibraryCode(String libraryCode) {
        if (libraryCode == null || libraryCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "libraryCode는 필수입니다.");
        }
        return libraryCode.trim();
    }
}
