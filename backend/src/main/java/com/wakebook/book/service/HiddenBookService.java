package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class HiddenBookService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HiddenBookRepository hiddenBookRepository;

    public HiddenBookService(HiddenBookRepository hiddenBookRepository) {
        this.hiddenBookRepository = hiddenBookRepository;
    }

    public HiddenBook getTodayBook(String libraryCode) {
        String validatedLibraryCode = validateLibraryCode(libraryCode);
        List<HiddenBook> pool = hiddenBookRepository.findAllByLibraryCodeOrderByIdAsc(validatedLibraryCode);
        if (pool.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "오늘의 잠자는 책 후보가 없습니다.");
        }
        int index = (int) (LocalDate.now(SEOUL).toEpochDay() % pool.size());
        return pool.get(index);
    }

    public HiddenBook getRandomBook(String libraryCode) {
        String validatedLibraryCode = validateLibraryCode(libraryCode);
        return hiddenBookRepository.findRandomOneByLibraryCode(validatedLibraryCode)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "우연히 발견할 잠자는 책 후보가 없습니다."));
    }

    private String validateLibraryCode(String libraryCode) {
        if (libraryCode == null || libraryCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "libraryCode는 필수입니다.");
        }
        return libraryCode.trim();
    }
}
