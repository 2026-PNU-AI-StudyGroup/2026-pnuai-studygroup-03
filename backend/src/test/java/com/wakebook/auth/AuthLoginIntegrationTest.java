package com.wakebook.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signupThenLoginUsesDatabaseBcryptAndJwtComponentsTogether() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contextPath("/api")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "LIBRARIAN",
                                  "name": "김도서",
                                  "email": "login-integration@wakebook.kr",
                                  "password": "Password!123",
                                  "nickname": "책지기",
                                  "libraryName": "부산대학교 도서관",
                                  "libraryCode": "121018",
                                  "department": "자료운영팀"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contextPath("/api")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "LOGIN-INTEGRATION@wakebook.kr",
                                  "password": "Password!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("로그인되었습니다."))
                .andExpect(jsonPath("$.data.accessToken")
                        .value(matchesPattern("^[^.]+\\.[^.]+\\.[^.]+$")))
                .andExpect(jsonPath("$.data.user.id").isNumber())
                .andExpect(jsonPath("$.data.user.name").value("김도서"))
                .andExpect(jsonPath("$.data.user.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.data.user.libraryName").value("부산대학교 도서관"))
                .andExpect(jsonPath("$.data.user.libraryCode").value("121018"));
    }

    @Test
    void unknownEmailStillReturnsTheNeutralAuthenticationFailure() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contextPath("/api")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unknown@wakebook.kr",
                                  "password": "Password!123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.message")
                        .value("이메일 또는 비밀번호가 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
