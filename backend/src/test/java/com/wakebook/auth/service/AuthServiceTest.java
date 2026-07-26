package com.wakebook.auth.service;

import com.wakebook.auth.dto.SignupRequest;
import com.wakebook.auth.dto.SignupResponse;
import com.wakebook.common.exception.DuplicateEmailException;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void signupNormalizesEmailAndEncryptsPassword() {
        AuthService authService = new AuthService(userRepository, passwordEncoder);
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
        AuthService authService = new AuthService(userRepository, passwordEncoder);
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
        AuthService authService = new AuthService(userRepository, passwordEncoder);
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
}
