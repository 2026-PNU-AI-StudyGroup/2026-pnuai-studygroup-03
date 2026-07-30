package com.wakebook.auth.service;

import com.wakebook.auth.dto.LoginRequest;
import com.wakebook.auth.dto.LoginResponse;
import com.wakebook.auth.dto.LoginUserResponse;
import com.wakebook.auth.dto.MyInfoResponse;
import com.wakebook.auth.dto.SignupRequest;
import com.wakebook.auth.dto.SignupResponse;
import com.wakebook.auth.token.JwtTokenProvider;
import com.wakebook.bookshelf.service.BookshelfService;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.common.exception.DuplicateEmailException;
import com.wakebook.common.exception.InvalidCredentialsException;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2b$10$BzOMv4e41Ljhp5s88OvgPu0lB0VqwxyGpImxHKgL97A5t4DdoFdvm";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final BookshelfService bookshelfService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            BookshelfService bookshelfService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.bookshelfService = bookshelfService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        boolean librarian = request.role() == UserRole.LIBRARIAN;
        User user = new User(
                request.role(),
                request.name().strip(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                nullableStrip(request.nickname()),
                librarian ? nullableStrip(request.libraryName()) : null,
                librarian ? nullableStrip(request.libraryCode()) : null,
                librarian ? nullableStrip(request.department()) : null
        );

        User savedUser = userRepository.save(user);
        bookshelfService.createDefaultBookshelf(savedUser);
        return new SignupResponse(savedUser.getId(), savedUser.getRole(), savedUser.getName());
    }

    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);
        Optional<User> userCandidate = userRepository.findByEmailIgnoreCase(normalizedEmail);
        String passwordHash = userCandidate
                .map(User::getPasswordHash)
                .orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

        if (userCandidate.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        User user = userCandidate.orElseThrow();
        String accessToken = jwtTokenProvider.createAccessToken(user);
        LoginUserResponse loginUser = new LoginUserResponse(
                user.getId(),
                user.getName(),
                user.getRole(),
                user.getLibraryName(),
                user.getLibraryCode()
        );
        return new LoginResponse(accessToken, loginUser);
    }

    public MyInfoResponse getMyInfo(String authenticatedUserId) {
        Long userId = parseUserId(authenticatedUserId);
        User user = userRepository.findById(userId)
                .orElseThrow(AuthenticationRequiredException::new);

        return new MyInfoResponse(
                user.getId(),
                user.getName(),
                user.getNickname(),
                user.getRole(),
                user.getLibraryName(),
                user.getLibraryCode()
        );
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
