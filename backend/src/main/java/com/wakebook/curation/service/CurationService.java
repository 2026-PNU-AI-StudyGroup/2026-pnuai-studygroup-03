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
import com.wakebook.curation.dto.PublicCurationSummaryResponse;
import com.wakebook.curation.dto.SaveCurationRequest;
import com.wakebook.curation.repository.CurationRepository;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CurationService {

    private final CurationRepository curationRepository;
    private final BookRepository bookRepository;
    private final BookDetailProvider bookDetailProvider;
    private final UserRepository userRepository;

    public CurationService(
            CurationRepository curationRepository,
            BookRepository bookRepository,
            BookDetailProvider bookDetailProvider,
            UserRepository userRepository
    ) {
        this.curationRepository = curationRepository;
        this.bookRepository = bookRepository;
        this.bookDetailProvider = bookDetailProvider;
        this.userRepository = userRepository;
    }

    @Transactional
    public CurationResponse create(String authenticatedUserId, SaveCurationRequest request) {
        User user = requireAuthenticatedUser(authenticatedUserId);
        Curation curation = new Curation(
                user,
                request.title().strip(),
                nullableStrip(request.description()),
                Boolean.TRUE.equals(request.isPublic())
        );
        addBooks(curation, request.books());

        return CurationResponse.from(curationRepository.save(curation));
    }

    public PageResponse<CurationSummaryResponse> getCurations(String authenticatedUserId, int page, int size) {
        Long userId = requireAuthenticatedUserId(authenticatedUserId);
        int pageIndex = Math.max(0, page - 1);
        Page<Curation> result = curationRepository.findAllByUser_Id(
                userId, PageRequest.of(pageIndex, size)
        );
        List<CurationSummaryResponse> content = result.getContent().stream()
                .map(CurationSummaryResponse::from)
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    public CurationResponse getCuration(String authenticatedUserId, Long curationId) {
        Long userId = requireAuthenticatedUserId(authenticatedUserId);
        return CurationResponse.from(findOwnedCuration(curationId, userId));
    }

    public PageResponse<PublicCurationSummaryResponse> getPublicCurations(int page, int size) {
        int pageNumber = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(size, 30));
        Page<Curation> result = curationRepository.findAllByIsPublicTrueOrderByCreatedAtDesc(
                PageRequest.of(pageNumber - 1, pageSize)
        );
        List<PublicCurationSummaryResponse> content = result.getContent().stream()
                .map(PublicCurationSummaryResponse::from)
                .toList();
        return PageResponse.of(content, pageNumber, pageSize, result.getTotalElements());
    }

    public CurationResponse getPublicCuration(Long curationId) {
        return CurationResponse.from(curationRepository.findByIdAndIsPublicTrue(curationId)
                .orElseThrow(CurationService::curationNotFound));
    }

    @Transactional
    public CurationResponse update(String authenticatedUserId, Long curationId, SaveCurationRequest request) {
        Long userId = requireAuthenticatedUserId(authenticatedUserId);
        Curation curation = findOwnedCuration(curationId, userId);
        curation.update(
                request.title().strip(),
                nullableStrip(request.description()),
                request.isPublic() == null ? curation.isPublic() : request.isPublic()
        );
        replaceBooks(curation, request.books());

        return CurationResponse.from(curationRepository.saveAndFlush(curation));
    }

    @Transactional
    public void delete(String authenticatedUserId, Long curationId) {
        Long userId = requireAuthenticatedUserId(authenticatedUserId);
        Curation curation = findOwnedCuration(curationId, userId);
        curationRepository.delete(curation);
    }

    private void addBooks(Curation curation, List<CurationBookRequest> requests) {
        if (requests == null) {
            return;
        }
        for (CurationBookRequest bookRequest : requests) {
            Book book = findOrCreateBook(bookRequest.isbn().strip());
            curation.addBook(new CurationBook(
                    curation, book, bookRequest.displayOrder(), nullableStrip(bookRequest.comment())
            ));
        }
    }

    private void replaceBooks(Curation curation, List<CurationBookRequest> requests) {
        curation.clearBooks();
        curationRepository.saveAndFlush(curation);

        for (CurationBookRequest bookRequest : requests == null ? List.<CurationBookRequest>of() : requests) {
            Book book = findOrCreateBook(bookRequest.isbn().strip());
            curation.addBook(new CurationBook(
                    curation, book, bookRequest.displayOrder(), nullableStrip(bookRequest.comment())
            ));
        }
    }

    private Book findOrCreateBook(String isbn) {
        return bookRepository.findById(isbn)
                .orElseGet(() -> createBook(isbn));
    }

    private Book createBook(String isbn) {
        BookDetail detail = bookDetailProvider.fetch(isbn)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "BOOK_001",
                        "도서를 찾을 수 없습니다."
                ));
        return bookRepository.save(new Book(isbn, detail.title(), detail.cover()));
    }

    private Curation findOwnedCuration(Long curationId, Long userId) {
        return curationRepository.findByIdAndUser_Id(curationId, userId)
                .orElseThrow(CurationService::curationNotFound);
    }

    private static ApiException curationNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "CURATION_001",
                "큐레이션을 찾을 수 없습니다."
        );
    }

    private User requireAuthenticatedUser(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        return userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);
    }

    private Long requireAuthenticatedUserId(String authenticatedUserId) {
        return requireAuthenticatedUser(authenticatedUserId).getId();
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

    private static String nullableStrip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
