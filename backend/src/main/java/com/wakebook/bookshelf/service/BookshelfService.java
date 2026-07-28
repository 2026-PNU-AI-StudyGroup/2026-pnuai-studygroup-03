package com.wakebook.bookshelf.service;

import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.BookshelfType;
import com.wakebook.bookshelf.dto.BookshelfResponse;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfResponse;
import com.wakebook.bookshelf.repository.BookshelfRepository;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookshelfService {

    private static final Comparator<Bookshelf> BOOKSHELF_ORDER =
            Comparator.comparingInt((Bookshelf bookshelf) ->
                            bookshelf.getType() == BookshelfType.DEFAULT ? 0 : 1)
                    .thenComparing(Bookshelf::getCreatedAt)
                    .thenComparing(Bookshelf::getId);

    private final BookshelfRepository bookshelfRepository;
    private final UserRepository userRepository;

    public BookshelfService(
            BookshelfRepository bookshelfRepository,
            UserRepository userRepository
    ) {
        this.bookshelfRepository = bookshelfRepository;
        this.userRepository = userRepository;
    }

    public List<BookshelfResponse> getBookshelves(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);

        return bookshelfRepository.findAllWithBooksByUserId(userId).stream()
                .sorted(BOOKSHELF_ORDER)
                .map(BookshelfResponse::from)
                .toList();
    }

    @Transactional
    public void createDefaultBookshelf(User user) {
        bookshelfRepository.save(Bookshelf.createDefault(user));
    }

    @Transactional
    public CreateBookshelfResponse createBookshelf(
            String authenticatedUserId,
            CreateBookshelfRequest request
    ) {
        Long userId = parseUserId(authenticatedUserId);
        User user = userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        Bookshelf bookshelf = Bookshelf.createCustom(
                user,
                request.name().strip(),
                nullableStrip(request.description())
        );

        return CreateBookshelfResponse.from(bookshelfRepository.save(bookshelf));
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
