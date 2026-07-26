package com.wakebook.auth.service;

import com.wakebook.auth.dto.SignupRequest;
import com.wakebook.auth.dto.SignupResponse;
import com.wakebook.common.exception.DuplicateEmailException;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
                librarian ? nullableStrip(request.department()) : null
        );

        User savedUser = userRepository.save(user);
        return new SignupResponse(savedUser.getId(), savedUser.getRole(), savedUser.getName());
    }

    private static String nullableStrip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
