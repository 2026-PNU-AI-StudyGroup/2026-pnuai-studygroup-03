package com.wakebook.bookshelf;

import com.jayway.jsonpath.JsonPath;
import com.wakebook.book.domain.Book;
import com.wakebook.book.repository.BookRepository;
import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.ReadingStatus;
import com.wakebook.bookshelf.repository.BookshelfRepository;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookshelfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookshelfRepository bookshelfRepository;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void signupCreatesAnEmptyDefaultBookshelf() throws Exception {
        AuthSession session = signupAndLogin("empty-shelf@wakebook.kr");

        mockMvc.perform(get("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("책장 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("읽고 싶은 책"))
                .andExpect(jsonPath("$.data[0].type").value("DEFAULT"))
                .andExpect(jsonPath("$.data[0].bookCount").value(0))
                .andExpect(jsonPath("$.data[0].books").isArray())
                .andExpect(jsonPath("$.data[0].books").isEmpty());
    }

    @Test
    void returnsNestedBooksForOnlyTheAuthenticatedUser() throws Exception {
        AuthSession owner = signupAndLogin("bookshelf-owner@wakebook.kr");
        signupAndLogin("bookshelf-other@wakebook.kr");

        User ownerUser = userRepository.findById(owner.userId()).orElseThrow();
        Book book = bookRepository.save(new Book(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                "https://example.com/cover.jpg"
        ));
        List<Bookshelf> ownerShelves =
                bookshelfRepository.findAllWithBooksByUserId(ownerUser.getId());
        Bookshelf defaultShelf = ownerShelves.getFirst();
        defaultShelf.addBook(book, ReadingStatus.WISH);
        bookshelfRepository.saveAndFlush(defaultShelf);

        mockMvc.perform(get("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(defaultShelf.getId()))
                .andExpect(jsonPath("$.data[0].bookCount").value(1))
                .andExpect(jsonPath("$.data[0].books.length()").value(1))
                .andExpect(jsonPath("$.data[0].books[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].books[0].isbn").value("9788960867450"))
                .andExpect(jsonPath("$.data[0].books[0].title")
                        .value("관계에도 연습이 필요합니다"))
                .andExpect(jsonPath("$.data[0].books[0].status").value("WISH"))
                .andExpect(jsonPath("$.data[0].books[0].cover")
                        .value("https://example.com/cover.jpg"));
    }

    @Test
    void missingTokenReturnsTheCommonAuthenticationError() throws Exception {
        mockMvc.perform(get("/api/bookshelves")
                        .contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void tokenForAnUnknownUserReturnsTheCommonAuthenticationError() throws Exception {
        String token = createToken(
                "999999",
                "USER",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(get("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private AuthSession signupAndLogin(String email) throws Exception {
        String signupResponse = mockMvc.perform(post("/api/auth/signup")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "USER",
                                  "name": "김독자",
                                  "email": "%s",
                                  "password": "Password!123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number userId = JsonPath.read(signupResponse, "$.data.id");

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
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

    private String createToken(
            String subject,
            String role,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private record AuthSession(long userId, String accessToken) {
    }
}
