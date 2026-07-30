package com.wakebook.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthMeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void loginTokenReturnsTheAuthenticatedUsersInformation() throws Exception {
        AuthSession session = signupAndLogin(
                "LIBRARIAN",
                "김도서",
                "me-librarian@wakebook.kr",
                "책지기",
                "부산대학교 도서관",
                "121018",
                "자료운영팀"
        );

        mockMvc.perform(get("/api/auth/me")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("내 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.id").value(session.userId()))
                .andExpect(jsonPath("$.data.name").value("김도서"))
                .andExpect(jsonPath("$.data.nickname").value("책지기"))
                .andExpect(jsonPath("$.data.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.data.libraryName").value("부산대학교 도서관"))
                .andExpect(jsonPath("$.data.libraryCode").value("121018"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.department").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist());
    }

    @Test
    void optionalUserInformationIsReturnedAsNull() throws Exception {
        AuthSession session = signupAndLogin(
                "USER",
                "김독자",
                "me-user@wakebook.kr",
                null,
                null,
                null,
                null
        );

        mockMvc.perform(get("/api/auth/me")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(session.userId()))
                .andExpect(jsonPath("$.data.name").value("김독자"))
                .andExpect(jsonPath("$.data.nickname").value(nullValue()))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.libraryName").value(nullValue()));
    }

    @Test
    void missingTokenReturnsTheCommonAuthenticationError() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void tamperedAndExpiredTokensReturnTheCommonAuthenticationError() throws Exception {
        AuthSession session = signupAndLogin(
                "USER",
                "김독자",
                "me-invalid-token@wakebook.kr",
                null,
                null,
                null,
                null
        );
        String tamperedToken = tamperSignature(session.accessToken());
        String expiredToken = createToken(
                String.valueOf(session.userId()),
                "USER",
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60)
        );

        assertAuthenticationRequired(tamperedToken);
        assertAuthenticationRequired(expiredToken);
    }

    @Test
    void tokenForADeletedOrUnknownUserReturnsTheCommonAuthenticationError() throws Exception {
        String token = createToken(
                "999999",
                "USER",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        assertAuthenticationRequired(token);
    }

    @Test
    void userRoleCannotAccessLibrarianApis() throws Exception {
        AuthSession session = signupAndLogin(
                "USER",
                "김독자",
                "me-role@wakebook.kr",
                null,
                null,
                null,
                null
        );

        mockMvc.perform(get("/api/librarian/dashboard")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_002"))
                .andExpect(jsonPath("$.message").value("권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private AuthSession signupAndLogin(
            String role,
            String name,
            String email,
            String nickname,
            String libraryName,
            String libraryCode,
            String department
    ) throws Exception {
        String signupBody = """
                {
                  "role": "%s",
                  "name": "%s",
                  "email": "%s",
                  "password": "Password!123",
                  "nickname": %s,
                  "libraryName": %s,
                  "libraryCode": %s,
                  "department": %s
                }
                """.formatted(
                role,
                name,
                email,
                jsonString(nickname),
                jsonString(libraryName),
                jsonString(libraryCode),
                jsonString(department)
        );

        String signupResponse = mockMvc.perform(post("/api/auth/signup")
                        .contextPath("/api")
                        .contentType(APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number userId = JsonPath.read(signupResponse, "$.data.id");

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contextPath("/api")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password!123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(loginResponse, "$.data.accessToken");

        return new AuthSession(userId.longValue(), accessToken);
    }

    private void assertAuthenticationRequired(String accessToken) throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private String createToken(
            String subject,
            String role,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private static String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'a' ? 'b' : 'a';
        return token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private record AuthSession(long userId, String accessToken) {
    }
}
