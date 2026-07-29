package com.wakebook.bookshelf;

import com.jayway.jsonpath.JsonPath;
import com.wakebook.book.domain.Book;
import com.wakebook.book.repository.BookRepository;
import com.wakebook.bookshelf.domain.Bookshelf;
import com.wakebook.bookshelf.domain.ReadingStatus;
import com.wakebook.bookshelf.repository.BookshelfBookRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private BookshelfBookRepository bookshelfBookRepository;

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

    @Test
    void createsACustomCollectionAndExposesItOnlyToItsOwner() throws Exception {
        AuthSession owner = signupAndLogin("collection-owner@wakebook.kr");
        AuthSession other = signupAndLogin("collection-other@wakebook.kr");

        String createResponse = mockMvc.perform(post("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  마음을 돌보는 책  ",
                                  "description": "  천천히 읽고 싶은 책 모음  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("컬렉션이 생성되었습니다."))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("마음을 돌보는 책"))
                .andExpect(jsonPath("$.data.description")
                        .value("천천히 읽고 싶은 책 모음"))
                .andExpect(jsonPath("$.data.type").value("CUSTOM"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number collectionId = JsonPath.read(createResponse, "$.data.id");

        List<Bookshelf> ownerShelves =
                bookshelfRepository.findAllWithBooksByUserId(owner.userId());
        Bookshelf created = ownerShelves.stream()
                .filter(bookshelf -> bookshelf.getId().equals(collectionId.longValue()))
                .findFirst()
                .orElseThrow();
        assertThat(created.getName()).isEqualTo("마음을 돌보는 책");
        assertThat(created.getDescription()).isEqualTo("천천히 읽고 싶은 책 모음");

        mockMvc.perform(get("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("DEFAULT"))
                .andExpect(jsonPath("$.data[1].id").value(collectionId))
                .andExpect(jsonPath("$.data[1].name").value("마음을 돌보는 책"))
                .andExpect(jsonPath("$.data[1].type").value("CUSTOM"))
                .andExpect(jsonPath("$.data[1].bookCount").value(0))
                .andExpect(jsonPath("$.data[1].books").isEmpty());

        mockMvc.perform(get("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("DEFAULT"));
    }

    @Test
    void rejectsInvalidCollectionFields() throws Exception {
        AuthSession session = signupAndLogin("invalid-collection@wakebook.kr");

        mockMvc.perform(post("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "description": "설명"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message").value("컬렉션 이름을 입력해 주세요."));

        mockMvc.perform(post("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted("가".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message")
                        .value("컬렉션 이름은 100자 이하여야 합니다."));

        mockMvc.perform(post("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 컬렉션",
                                  "description": "%s"
                                }
                                """.formatted("가".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message")
                        .value("컬렉션 설명은 500자 이하여야 합니다."));

        assertThat(bookshelfRepository.findAllWithBooksByUserId(session.userId()))
                .hasSize(1);
    }

    @Test
    void creatingACollectionWithoutATokenReturnsTheAuthenticationError() throws Exception {
        mockMvc.perform(post("/api/bookshelves")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 컬렉션"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void creatingACollectionForAnUnknownUserReturnsTheAuthenticationError()
            throws Exception {
        String token = createToken(
                "999999",
                "USER",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(post("/api/bookshelves")
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 컬렉션"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void addsABookToAnOwnedBookshelfAndRejectsADuplicate() throws Exception {
        AuthSession owner = signupAndLogin("save-book@wakebook.kr");
        Bookshelf defaultShelf = bookshelfRepository
                .findAllWithBooksByUserId(owner.userId())
                .getFirst();
        bookRepository.save(new Book(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                "https://example.com/cover.jpg"
        ));

        mockMvc.perform(post("/api/bookshelves/{shelfId}/books", defaultShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9788960867450",
                                  "status": "WISH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("책장에 도서가 저장되었습니다."))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.isbn").value("9788960867450"))
                .andExpect(jsonPath("$.data.title")
                        .value("관계에도 연습이 필요합니다"))
                .andExpect(jsonPath("$.data.status").value("WISH"))
                .andExpect(jsonPath("$.data.cover")
                        .value("https://example.com/cover.jpg"));

        assertThat(bookshelfBookRepository
                .existsByBookshelf_IdAndBook_Isbn(
                        defaultShelf.getId(),
                        "9788960867450"
                )).isTrue();

        mockMvc.perform(post("/api/bookshelves/{shelfId}/books", defaultShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9788960867450",
                                  "status": "READING"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BOOKSHELF_003"))
                .andExpect(jsonPath("$.message")
                        .value("이미 해당 책장에 저장된 도서입니다."));
    }

    @Test
    void preventsAddingABookToAnotherUsersBookshelf() throws Exception {
        AuthSession owner = signupAndLogin("save-book-owner@wakebook.kr");
        AuthSession other = signupAndLogin("save-book-other@wakebook.kr");
        Bookshelf ownerShelf = bookshelfRepository
                .findAllWithBooksByUserId(owner.userId())
                .getFirst();
        bookRepository.save(new Book(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                null
        ));

        mockMvc.perform(post("/api/bookshelves/{shelfId}/books", ownerShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9788960867450",
                                  "status": "WISH"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKSHELF_001"))
                .andExpect(jsonPath("$.message").value("컬렉션을 찾을 수 없습니다."));

        assertThat(bookshelfBookRepository.count()).isZero();
    }

    @Test
    void validatesTheAddBookRequestAndRequiresAuthentication() throws Exception {
        AuthSession owner = signupAndLogin("invalid-save-book@wakebook.kr");
        Bookshelf defaultShelf = bookshelfRepository
                .findAllWithBooksByUserId(owner.userId())
                .getFirst();

        mockMvc.perform(post("/api/bookshelves/{shelfId}/books", defaultShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": " ",
                                  "status": "WISH"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message").value("ISBN을 입력해 주세요."));

        mockMvc.perform(post("/api/bookshelves/{shelfId}/books", defaultShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9788960867450"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message").value("읽기 상태를 선택해 주세요."));

        mockMvc.perform(post("/api/bookshelves/{shelfId}/books", defaultShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9788960867450",
                                  "status": "PAUSED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"));

        mockMvc.perform(post("/api/bookshelves/{shelfId}/books", defaultShelf.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9788960867450",
                                  "status": "WISH"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void updatesAndDeletesAnOwnedCustomCollection() throws Exception {
        AuthSession owner = signupAndLogin("manage-collection@wakebook.kr");
        long collectionId = createCollection(
                owner,
                "기존 컬렉션",
                "기존 설명"
        );
        Book book = bookRepository.save(new Book(
                "9788960867450",
                "관계에도 연습이 필요합니다",
                "https://example.com/cover.jpg"
        ));
        Bookshelf custom = bookshelfRepository.findById(collectionId).orElseThrow();
        custom.addBook(book, ReadingStatus.WISH);
        bookshelfRepository.saveAndFlush(custom);

        mockMvc.perform(patch("/api/bookshelves/{bookshelfId}", collectionId)
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  천천히 읽을 책  ",
                                  "description": "  이번 달에 읽을 책  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("컬렉션이 수정되었습니다."))
                .andExpect(jsonPath("$.data.id").value(collectionId))
                .andExpect(jsonPath("$.data.name").value("천천히 읽을 책"))
                .andExpect(jsonPath("$.data.description").value("이번 달에 읽을 책"))
                .andExpect(jsonPath("$.data.type").value("CUSTOM"));

        Bookshelf updated = bookshelfRepository.findById(collectionId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("천천히 읽을 책");
        assertThat(updated.getDescription()).isEqualTo("이번 달에 읽을 책");

        mockMvc.perform(delete("/api/bookshelves/{bookshelfId}", collectionId)
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("컬렉션이 삭제되었습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(bookshelfRepository.findById(collectionId)).isEmpty();
        assertThat(bookRepository.existsById("9788960867450")).isTrue();
    }

    @Test
    void preventsManagingTheDefaultBookshelf() throws Exception {
        AuthSession owner = signupAndLogin("protect-default@wakebook.kr");
        Bookshelf defaultShelf = bookshelfRepository
                .findAllWithBooksByUserId(owner.userId())
                .getFirst();

        mockMvc.perform(patch("/api/bookshelves/{bookshelfId}", defaultShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "바꾸려는 이름",
                                  "description": "바꾸려는 설명"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BOOKSHELF_002"))
                .andExpect(jsonPath("$.message")
                        .value("기본 책장은 수정하거나 삭제할 수 없습니다."));

        mockMvc.perform(delete("/api/bookshelves/{bookshelfId}", defaultShelf.getId())
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BOOKSHELF_002"))
                .andExpect(jsonPath("$.message")
                        .value("기본 책장은 수정하거나 삭제할 수 없습니다."));

        assertThat(bookshelfRepository.findById(defaultShelf.getId())).isPresent();
    }

    @Test
    void hidesAnotherUsersCollectionFromUpdateAndDelete() throws Exception {
        AuthSession owner = signupAndLogin("collection-real-owner@wakebook.kr");
        AuthSession other = signupAndLogin("collection-intruder@wakebook.kr");
        long collectionId = createCollection(owner, "소유자의 컬렉션", null);

        mockMvc.perform(patch("/api/bookshelves/{bookshelfId}", collectionId)
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "가져갈 컬렉션"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BOOKSHELF_001"))
                .andExpect(jsonPath("$.message").value("컬렉션을 찾을 수 없습니다."));

        mockMvc.perform(delete("/api/bookshelves/{bookshelfId}", collectionId)
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BOOKSHELF_001"))
                .andExpect(jsonPath("$.message").value("컬렉션을 찾을 수 없습니다."));

        assertThat(bookshelfRepository.findById(collectionId)).isPresent();
    }

    @Test
    void validatesCollectionUpdateFields() throws Exception {
        AuthSession owner = signupAndLogin("invalid-update@wakebook.kr");
        long collectionId = createCollection(owner, "유효한 이름", "유효한 설명");

        mockMvc.perform(patch("/api/bookshelves/{bookshelfId}", collectionId)
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "description": "설명"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message").value("컬렉션 이름을 입력해 주세요."));

        mockMvc.perform(patch("/api/bookshelves/{bookshelfId}", collectionId)
                        .contextPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "유효한 이름",
                                  "description": "%s"
                                }
                                """.formatted("가".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.message")
                        .value("컬렉션 설명은 500자 이하여야 합니다."));

        Bookshelf unchanged = bookshelfRepository.findById(collectionId).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("유효한 이름");
        assertThat(unchanged.getDescription()).isEqualTo("유효한 설명");
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

    private long createCollection(
            AuthSession session,
            String name,
            String description
    ) throws Exception {
        String descriptionJson = description == null
                ? "null"
                : "\"" + description + "\"";
        String response = mockMvc.perform(post("/api/bookshelves")
                        .contextPath("/api")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + session.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": %s
                                }
                                """.formatted(name, descriptionJson)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number collectionId = JsonPath.read(response, "$.data.id");
        return collectionId.longValue();
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
