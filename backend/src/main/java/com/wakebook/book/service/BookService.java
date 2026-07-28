package com.wakebook.book.service;

import com.wakebook.book.dto.BookDetailResponse;
import com.wakebook.book.dto.BookSearchResponse;
import com.wakebook.book.dto.PopularBookResponse;
import com.wakebook.book.support.BookCategory;
import com.wakebook.common.ApiException;
import com.wakebook.common.PageResponse;
import com.wakebook.external.aladin.TableOfContentsProvider;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.library.BookSearchCriteria;
import com.wakebook.external.library.BookSearchProvider;
import com.wakebook.external.library.BookSearchResult;
import com.wakebook.external.library.Data4LibraryProperties;
import com.wakebook.external.library.LibraryHolding;
import com.wakebook.external.library.LibraryHoldingProvider;
import com.wakebook.external.library.PopularLoanBookCriteria;
import com.wakebook.external.library.PopularLoanBookProvider;
import com.wakebook.external.library.PopularLoanBookResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Service
public class BookService {

    private static final Set<Integer> ALLOWED_AGES = Set.of(0, 6, 8, 14, 20, 30, 40, 50, 60);
    private static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PopularLoanBookProvider popularLoanBookProvider;
    private final BookSearchProvider bookSearchProvider;
    private final BookDetailProvider bookDetailProvider;
    private final LibraryHoldingProvider libraryHoldingProvider;
    private final TableOfContentsProvider tableOfContentsProvider;
    private final Data4LibraryProperties properties;

    public BookService(
        PopularLoanBookProvider popularLoanBookProvider,
        BookSearchProvider bookSearchProvider,
        BookDetailProvider bookDetailProvider,
        LibraryHoldingProvider libraryHoldingProvider,
        TableOfContentsProvider tableOfContentsProvider,
        Data4LibraryProperties properties
    ) {
        this.popularLoanBookProvider = popularLoanBookProvider;
        this.bookSearchProvider = bookSearchProvider;
        this.bookDetailProvider = bookDetailProvider;
        this.libraryHoldingProvider = libraryHoldingProvider;
        this.tableOfContentsProvider = tableOfContentsProvider;
        this.properties = properties;
    }

    public PageResponse<PopularBookResponse> getPopularBooks(int page, int size, String category, String gender, Integer age) {
        int validatedPage = validatePage(page);
        int validatedSize = validateSize(size);
        String kdc = BookCategory.toKdcCode(category);
        String genderCode = toGenderCode(gender);
        validateAge(age);

        LocalDate endDt = LocalDate.now(SEOUL);
        LocalDate startDt = endDt.minusMonths(properties.defaultPeriodMonths());

        PopularLoanBookCriteria criteria = new PopularLoanBookCriteria(
            startDt, endDt, kdc, genderCode, age, validatedPage, validatedSize
        );
        PopularLoanBookResult result = popularLoanBookProvider.fetch(criteria);

        List<PopularBookResponse> content = result.items().stream()
            .map(PopularBookResponse::from)
            .toList();

        return PageResponse.of(content, validatedPage, validatedSize, result.totalCount());
    }

    public PageResponse<BookSearchResponse> searchBooks(String keyword, int page, int size) {
        int validatedPage = validatePage(page);
        int validatedSize = validateSize(size);
        String validatedKeyword = validateKeyword(keyword);

        BookSearchCriteria criteria = new BookSearchCriteria(validatedKeyword, validatedPage, validatedSize);
        BookSearchResult result = bookSearchProvider.search(criteria);

        List<BookSearchResponse> content = result.items().stream()
            .map(BookSearchResponse::from)
            .toList();

        return PageResponse.of(content, validatedPage, validatedSize, result.totalCount());
    }

    public BookDetailResponse getBookDetail(String isbn, String region) {
        String validatedIsbn = validateIsbn(isbn);

        BookDetail detail = bookDetailProvider.fetch(validatedIsbn)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "도서를 찾을 수 없습니다."));

        List<LibraryHolding> holdings = (region == null || region.isBlank())
            ? List.of()
            : libraryHoldingProvider.findHoldings(validatedIsbn, region.trim());
        List<String> tableOfContents = tableOfContentsProvider.fetch(validatedIsbn);

        return BookDetailResponse.of(detail, holdings, tableOfContents);
    }

    private String validateIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "isbn은 필수입니다.");
        }
        return isbn.trim();
    }

    private String validateKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "keyword는 필수입니다.");
        }
        return keyword.trim();
    }

    private int validatePage(int page) {
        if (page < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "page는 1 이상이어야 합니다.");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        return size;
    }

    private String toGenderCode(String gender) {
        if (gender == null || gender.isBlank() || "ALL".equalsIgnoreCase(gender)) {
            return null;
        }
        return switch (gender.toUpperCase()) {
            case "M" -> "0";
            case "F" -> "1";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "gender는 ALL, M, F 중 하나여야 합니다.");
        };
    }

    private void validateAge(Integer age) {
        if (age != null && !ALLOWED_AGES.contains(age)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "지원하지 않는 연령대입니다: " + age);
        }
    }
}
