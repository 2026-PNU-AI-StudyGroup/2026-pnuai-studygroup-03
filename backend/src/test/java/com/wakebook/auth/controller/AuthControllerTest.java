package com.wakebook.auth.controller;

import com.wakebook.auth.dto.SignupRequest;
import com.wakebook.auth.dto.SignupResponse;
import com.wakebook.auth.service.AuthService;
import com.wakebook.common.exception.DuplicateEmailException;
import com.wakebook.common.exception.GlobalExceptionHandler;
import com.wakebook.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void librarianSignupReturnsApiSpecificationResponse() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenReturn(new SignupResponse(12L, UserRole.LIBRARIAN, "김도서"));

        mockMvc.perform(post("/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "LIBRARIAN",
                                  "name": "김도서",
                                  "email": "librarian@wakebook.kr",
                                  "password": "Password!123",
                                  "nickname": "책지기",
                                  "libraryName": "부산대학교 도서관",
                                  "department": "자료운영팀"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.data.name").value("김도서"));
    }

    @Test
    void librarianSignupRequiresLibraryNameAndDepartment() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "LIBRARIAN",
                                  "name": "김도서",
                                  "email": "librarian@wakebook.kr",
                                  "password": "Password!123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.message")
                        .value("사서는 소속 도서관과 담당 부서를 입력해야 합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(authService);
    }

    @Test
    void duplicateEmailReturnsConflictResponse() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "USER",
                                  "name": "김독자",
                                  "email": "reader@wakebook.kr",
                                  "password": "Password!123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_002"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unsupportedRoleReturnsBadRequestResponse() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN",
                                  "name": "관리자",
                                  "email": "admin@wakebook.kr",
                                  "password": "Password!123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.message").value("요청값을 확인해 주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
