package com.wakebook.external;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookJob;
import com.wakebook.book.domain.HiddenBookJobStatus;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.book.service.HiddenBookJobService;
import com.wakebook.book.service.LibraryCollectService;
import com.wakebook.bookshelf.domain.ReadingStatus;
import com.wakebook.bookshelf.dto.AddBookshelfBookRequest;
import com.wakebook.bookshelf.dto.CreateBookshelfRequest;
import com.wakebook.bookshelf.service.BookshelfService;
import com.wakebook.curation.dto.CurationBookRequest;
import com.wakebook.curation.dto.CurationGenerateRequest;
import com.wakebook.curation.dto.CurationGenerateResponse;
import com.wakebook.curation.dto.CurationResponse;
import com.wakebook.curation.dto.SaveCurationRequest;
import com.wakebook.curation.service.CurationGenerationService;
import com.wakebook.curation.service.CurationService;
import com.wakebook.external.aladin.AladinBookDetailProvider;
import com.wakebook.external.aladin.AladinProperties;
import com.wakebook.external.kakao.KakaoBookDetailProvider;
import com.wakebook.external.kakao.KakaoProperties;
import com.wakebook.external.library.Data4LibraryBookDetailProvider;
import com.wakebook.external.library.Data4LibraryDirectoryProvider;
import com.wakebook.external.library.Data4LibraryProperties;
import com.wakebook.external.library.LibraryDirectoryItem;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.external.openai.OpenAiProperties;
import com.wakebook.recommendation.dto.CompareRequest;
import com.wakebook.recommendation.dto.CompareResponse;
import com.wakebook.recommendation.dto.ExploreRequest;
import com.wakebook.recommendation.dto.RecommendationRequest;
import com.wakebook.recommendation.dto.RecommendationResponse;
import com.wakebook.recommendation.service.BookComparisonService;
import com.wakebook.recommendation.service.RecommendationExploreService;
import com.wakebook.recommendation.service.RecommendationService;
import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import com.wakebook.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("external")
@SpringBootTest
@ActiveProfiles("external-test")
@TestMethodOrder(OrderAnnotation.class)
class WakeBookExternalIntegrationTest {

    private static final String REGION_CODE = "21";
    private static final String PREFERRED_LIBRARY_CODE = "121020";
    private static final String BASE_ISBN = "9788936433598";
    private static final Duration JOB_TIMEOUT = Duration.ofMinutes(8);

    @Autowired
    private Data4LibraryProperties data4LibraryProperties;

    @Autowired
    private AladinProperties aladinProperties;

    @Autowired
    private KakaoProperties kakaoProperties;

    @Autowired
    private OpenAiProperties openAiProperties;

    @Autowired
    private Data4LibraryDirectoryProvider directoryProvider;

    @Autowired
    private Data4LibraryBookDetailProvider data4LibraryBookDetailProvider;

    @Autowired
    private AladinBookDetailProvider aladinBookDetailProvider;

    @Autowired
    private KakaoBookDetailProvider kakaoBookDetailProvider;

    @Autowired
    private OpenAiClient openAiClient;

    @Autowired
    private LibraryCollectService libraryCollectService;

    @Autowired
    private HiddenBookJobService hiddenBookJobService;

    @Autowired
    private HiddenBookRepository hiddenBookRepository;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private RecommendationExploreService recommendationExploreService;

    @Autowired
    private BookComparisonService bookComparisonService;

    @Autowired
    private BookshelfService bookshelfService;

    @Autowired
    private CurationGenerationService curationGenerationService;

    @Autowired
    private CurationService curationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanDedicatedTestDatabase() {
        List<String> tables = jdbcTemplate.queryForList("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name <> 'flyway_schema_history'
            """, String.class);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : tables) {
                jdbcTemplate.execute("TRUNCATE TABLE `" + table.replace("`", "``") + "`");
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    @Order(1)
    void 모든_외부_서비스_설정이_비밀값을_통해_주입된다() {
        assertAll(
            () -> assertConfigured("DATA4LIBRARY_API_KEY", data4LibraryProperties.authKey()),
            () -> assertConfigured("ALADIN_TTB_KEY", aladinProperties.ttbKey()),
            () -> assertConfigured("KAKAO_API_KEY", kakaoProperties.apiKey()),
            () -> assertConfigured("OPENAI_API_KEY", openAiProperties.apiKey())
        );
    }

    @Test
    @Order(2)
    void MySQL과_외부_API에_실제로_연결된다() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }

        assertAll(
            () -> assertThat(directoryProvider.findByRegion(REGION_CODE)).isNotEmpty(),
            () -> assertThat(data4LibraryBookDetailProvider.fetch(BASE_ISBN)).isPresent(),
            () -> assertThat(aladinBookDetailProvider.fetch(BASE_ISBN)).isPresent(),
            () -> assertThat(kakaoBookDetailProvider.fetch(BASE_ISBN)).isPresent(),
            () -> assertThat(openAiClient.complete(
                "JSON 객체만 응답하세요.",
                "연결 확인을 위해 {\"status\":\"ok\"}를 응답하세요."
            )).contains("status", "ok")
        );
    }

    @Test
    @Order(3)
    void 도서관_선택부터_큐레이션_공개까지_실제_서비스_흐름이_완주한다() throws Exception {
        List<LibraryDirectoryItem> libraries = libraryCollectService.findLibraries(REGION_CODE);
        LibraryDirectoryItem library = selectLibrary(libraries);
        User librarian = userRepository.save(new User(
            UserRole.LIBRARIAN,
            "외부 연동 검증 사서",
            "external-flow@wakebook.test",
            "x".repeat(60),
            "검증 사서",
            library.libraryName(),
            library.libraryCode(),
            "자료서비스팀"
        ));
        String userId = librarian.getId().toString();

        HiddenBookJob requested = libraryCollectService.requestCollect(userId, library.libraryCode());
        HiddenBookJob completed = awaitCompletion(requested.getId());
        assertThat(completed.getStatus())
            .withFailMessage("후보 수집 작업 실패: %s", completed.getMessage())
            .isEqualTo(HiddenBookJobStatus.SUCCEEDED);
        assertThat(completed.getSavedCount()).isPositive();

        List<HiddenBook> pool = hiddenBookRepository.findAllByLibraryCode(library.libraryCode());
        assertThat(pool).isNotEmpty();
        HiddenBook hiddenBook = pool.get(0);
        List<String> keywords = hiddenBook.getKeywords().isEmpty()
            ? List.of("새로운 관점")
            : List.of(hiddenBook.getKeywords().get(0));

        List<RecommendationResponse> recommendations = recommendationService.recommend(
            new RecommendationRequest(
                BASE_ISBN,
                library.libraryCode(),
                keywords,
                "새로운 관점",
                "사색적인",
                1
            )
        );
        assertThat(recommendations).isNotEmpty();

        assertThat(recommendationExploreService.explore(
            new ExploreRequest(BASE_ISBN, library.libraryCode(), "SIMILAR_TOPIC")
        )).isNotEmpty();

        CompareResponse comparison = bookComparisonService.compare(
            new CompareRequest(BASE_ISBN, hiddenBook.getIsbn())
        );
        assertThat(comparison.difference()).isNotBlank();

        Long shelfId = bookshelfService.createBookshelf(
            userId,
            new CreateBookshelfRequest("외부 연동 검증 책장", "4.1 전체 흐름 검증")
        ).id();
        assertThat(bookshelfService.addBook(
            userId,
            shelfId,
            new AddBookshelfBookRequest(hiddenBook.getIsbn(), ReadingStatus.WISH)
        ).isbn()).isEqualTo(hiddenBook.getIsbn());

        CurationGenerateResponse draft = curationGenerationService.generate(
            userId,
            new CurationGenerateRequest(
                "새로운 관점을 만나는 책",
                "성인",
                "사색적인",
                null,
                1,
                List.of(),
                "깊이 있는 사유"
            )
        );
        assertThat(draft.books()).isNotEmpty();

        List<CurationBookRequest> books = draft.books().stream()
            .map(book -> new CurationBookRequest(book.isbn(), 1, book.reason()))
            .toList();
        CurationResponse saved = curationService.create(
            userId,
            new SaveCurationRequest(draft.title(), draft.description(), true, books)
        );
        assertThat(saved.isPublic()).isTrue();
        assertThat(curationService.getPublicCuration(saved.id()).id()).isEqualTo(saved.id());
        assertThat(curationService.getPublicCurations(1, 10).content())
            .extracting(summary -> summary.id())
            .contains(saved.id());
    }

    private LibraryDirectoryItem selectLibrary(List<LibraryDirectoryItem> libraries) {
        assertThat(libraries).isNotEmpty();
        return libraries.stream()
            .filter(library -> PREFERRED_LIBRARY_CODE.equals(library.libraryCode()))
            .findFirst()
            .orElseGet(() -> libraries.stream()
                .max(Comparator.comparingLong(LibraryDirectoryItem::bookCount))
                .orElseThrow());
    }

    private HiddenBookJob awaitCompletion(Long jobId) throws InterruptedException {
        long deadline = System.nanoTime() + JOB_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            HiddenBookJob job = hiddenBookJobService.get(jobId);
            if (job.getStatus() == HiddenBookJobStatus.SUCCEEDED
                || job.getStatus() == HiddenBookJobStatus.FAILED) {
                return job;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("후보 수집 작업이 제한 시간 안에 끝나지 않았습니다.");
    }

    private void assertConfigured(String name, String value) {
        assertThat(value != null && !value.isBlank())
            .as(name + " must be supplied outside the repository")
            .isTrue();
    }
}
