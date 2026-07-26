package com.wakebook.auth.service;

import com.wakebook.auth.dto.LoginRequest;
import com.wakebook.auth.dto.LoginResponse;
import com.wakebook.auth.dto.SignupRequest;
import com.wakebook.auth.dto.SignupResponse;
import com.wakebook.auth.token.JwtTokenProvider;
import com.wakebook.common.exception.DuplicateEmailException;
import com.wakebook.common.exception.InvalidCredentialsException;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void signupNormalizesEmailAndEncryptsPassword() {
        AuthService authService = createAuthService();
        SignupRequest request = new SignupRequest(
                UserRole.LIBRARIAN,
                " 김도서 ",
                " Librarian@WakeBook.kr ",
                "Password!123",
                " 책지기 ",
                " 부산대학교 도서관 ",
                " 자료운영팀 "
        );
        when(userRepository.existsByEmailIgnoreCase("librarian@wakebook.kr")).thenReturn(false);
        when(passwordEncoder.encode("Password!123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = authService.signup(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("librarian@wakebook.kr");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getLibraryName()).isEqualTo("부산대학교 도서관");
        assertThat(savedUser.getDepartment()).isEqualTo("자료운영팀");
        assertThat(response.role()).isEqualTo(UserRole.LIBRARIAN);
        assertThat(response.name()).isEqualTo("김도서");
    }

    @Test
    void signupRejectsDuplicateEmailBeforeEncodingPassword() {
        AuthService authService = createAuthService();
        SignupRequest request = new SignupRequest(
                UserRole.USER,
                "김독자",
                "reader@wakebook.kr",
                "Password!123",
                null,
                null,
                null
        );
        when(userRepository.existsByEmailIgnoreCase("reader@wakebook.kr")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void userSignupDoesNotStoreLibrarianInformation() {
        AuthService authService = createAuthService();
        SignupRequest request = new SignupRequest(
                UserRole.USER,
                "김독자",
                "reader@wakebook.kr",
                "Password!123",
                null,
                "잘못 전달된 도서관",
                "잘못 전달된 부서"
        );
        when(userRepository.existsByEmailIgnoreCase("reader@wakebook.kr")).thenReturn(false);
        when(passwordEncoder.encode("Password!123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getLibraryName()).isNull();
        assertThat(userCaptor.getValue().getDepartment()).isNull();
    }

    @Test
    void signupRequestDoesNotExposePasswordInLogs() {
        SignupRequest request = new SignupRequest(
                UserRole.USER,
                "김독자",
                "reader@wakebook.kr",
                "Password!123",
                null,
                null,
                null
        );

        assertThat(request.toString())
                .contains("password=[REDACTED]")
                .doesNotContain("Password!123");
    }

    @Test
    void loginReturnsTokenAndApiSpecificationUserData() {
        AuthService authService = createAuthService();
        User user = librarianUser();
        when(userRepository.findByEmailIgnoreCase("librarian@wakebook.kr"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password!123", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("access-token");

        LoginResponse response = authService.login(
                new LoginRequest(" Librarian@WakeBook.kr ", "Password!123")
        );

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().id()).isEqualTo(12L);
        assertThat(response.user().name()).isEqualTo("김도서");
        assertThat(response.user().role()).isEqualTo(UserRole.LIBRARIAN);
        assertThat(response.user().libraryName()).isEqualTo("부산대학교 도서관");
        verify(jwtTokenProvider).createAccessToken(user);
    }

    @Test
    void loginRejectsUnknownEmailWithoutCreatingToken() {
        AuthService authService = createAuthService();
        when(userRepository.findByEmailIgnoreCase("missing@wakebook.kr"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("missing@wakebook.kr", "Password!123")
        ))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

        verify(passwordEncoder).matches(
                eq("Password!123"),
                argThat(hash -> hash != null && hash.startsWith("$2"))
        );
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    void loginRejectsWrongPasswordWithoutCreatingToken() {
        AuthService authService = createAuthService();
        User user = librarianUser();
        when(userRepository.findByEmailIgnoreCase("librarian@wakebook.kr"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("librarian@wakebook.kr", "wrong-password")
        ))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    void loginRequestDoesNotExposePasswordInLogs() {
        LoginRequest request = new LoginRequest(
                "librarian@wakebook.kr",
                "Password!123"
        );

        assertThat(request.toString())
                .contains("password=[REDACTED]")
                .doesNotContain("Password!123");
    }

    private AuthService createAuthService() {
        return new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    private static User librarianUser() {
        User user = new User(
                UserRole.LIBRARIAN,
                "김도서",
                "librarian@wakebook.kr",
                "encoded-password",
                "책지기",
                "부산대학교 도서관",
                "자료운영팀"
        );
        ReflectionTestUtils.setField(user, "id", 12L);
        return user;
    }
}
