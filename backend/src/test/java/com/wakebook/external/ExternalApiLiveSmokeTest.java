package com.wakebook.external;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.external.aladin.AladinBookDetailProvider;
import com.wakebook.external.aladin.AladinProperties;
import com.wakebook.external.kakao.KakaoBookDetailProvider;
import com.wakebook.external.kakao.KakaoProperties;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.Data4LibraryBookDetailProvider;
import com.wakebook.external.library.Data4LibraryProperties;
import com.wakebook.external.library.FallbackBookDetailProvider;
import com.wakebook.external.naver.NaverApiProperties;
import com.wakebook.external.naver.NaverNewsSearchClient;
import com.wakebook.external.naver.NaverSearchTrendClient;
import com.wakebook.external.openai.OpenAiChatClient;
import com.wakebook.external.openai.OpenAiProperties;
import com.wakebook.external.trend.GoogleTrendsProperties;
import com.wakebook.external.trend.GoogleTrendsRssClient;
import com.wakebook.recommendation.dto.RecommendationRequest;
import com.wakebook.recommendation.dto.ExploreRequest;
import com.wakebook.recommendation.service.RecommendationExploreService;
import com.wakebook.recommendation.service.RecommendationService;
import com.wakebook.trend.domain.TrendEligibility;
import com.wakebook.trend.service.TrendAiService;
import com.wakebook.trend.service.TrendBookMatcher;
import com.wakebook.trend.service.TrendEnrichment;
import com.wakebook.trend.support.TrendProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 외부 서비스의 키와 응답 계약을 확인하는 선택 실행형 테스트다.
 *
 * <p>일반 {@code gradlew test}에서는 실행되지 않는다. API 호출량과 비용을 의도치 않게
 * 소비하지 않도록 아래처럼 명시적으로 활성화한다.</p>
 *
 * <pre>{@code
 * $env:RUN_LIVE_API_TESTS='true'
 * .\gradlew.bat test --tests com.wakebook.external.ExternalApiLiveSmokeTest
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_API_TESTS", matches = "(?i)true")
class ExternalApiLiveSmokeTest {

    private static final String TEST_ISBN = "9788937473135";
    private static Map<String, String> environment;

    @BeforeAll
    static void loadEnvironment() throws IOException {
        environment = new HashMap<>(System.getenv());
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return;
        }
        for (String rawLine : Files.readAllLines(envFile)) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator > 0) {
                environment.putIfAbsent(
                    line.substring(0, separator).trim(),
                    line.substring(separator + 1).trim()
                );
            }
        }
    }

    @Test
    void actualBookApisReturnRequiredDetailFields() {
        String kakaoKey = required("KAKAO_API_KEY");
        String aladinKey = required("ALADIN_TTB_KEY");
        String data4LibraryKey = required("DATA4LIBRARY_API_KEY");

        KakaoBookDetailProvider kakao = new KakaoBookDetailProvider(
            new KakaoProperties("https://dapi.kakao.com", kakaoKey)
        );
        AladinBookDetailProvider aladin = new AladinBookDetailProvider(
            new AladinProperties("https://www.aladin.co.kr/ttb/api", aladinKey)
        );
        Data4LibraryBookDetailProvider data4Library = new Data4LibraryBookDetailProvider(
            new Data4LibraryProperties("https://data4library.kr/api", data4LibraryKey, 12)
        );

        assertRequiredDetail(kakao.fetch(TEST_ISBN));
        assertRequiredDetail(aladin.fetch(TEST_ISBN));
        assertRequiredDetail(data4Library.fetch(TEST_ISBN));

        FallbackBookDetailProvider fallback = new FallbackBookDetailProvider(kakao, aladin, data4Library);
        assertRequiredDetail(fallback.fetch(TEST_ISBN));
    }

    @Test
    void actualGoogleTrendsRssReturnsKoreanTrendCandidates() {
        GoogleTrendsRssClient google = new GoogleTrendsRssClient(
            new GoogleTrendsProperties("https://trends.google.com", "KR")
        );

        assertThat(google.fetchDailyTrends("KR", 10))
            .isNotEmpty()
            .allSatisfy(item -> {
                assertThat(item.keyword()).isNotBlank();
                assertThat(item.sourceKey()).isNotBlank();
            });
    }

    @Test
    void actualNaverOpenApiReturnsNewsAndSearchTrend() {
        NaverApiProperties properties = new NaverApiProperties(
            "https://openapi.naver.com",
            required("NAVER_CLIENT_ID"),
            required("NAVER_CLIENT_SECRET")
        );

        var news = new NaverNewsSearchClient(properties).search("인공지능", 3);
        assertThat(news)
            .isNotEmpty()
            .allSatisfy(item -> {
                assertThat(item.title()).isNotBlank();
                assertThat(item.url()).isNotBlank();
            });

        var validation = new NaverSearchTrendClient(properties)
            .validate("인공지능", "생성형 인공지능 기술");
        assertThat(validation.spikeScore()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    void actualOpenAiReturnsJsonObject() {
        OpenAiChatClient openAi = openAiClient();

        String response = openAi.complete(
            "반드시 JSON 객체만 응답하세요.",
            "status 키의 값이 ok인 JSON 객체를 반환하세요."
        );

        assertThat(response).contains("\"status\"").contains("ok");
    }

    @Test
    void actualOpenAiRecommendsAtMostFifteenFromServerShortlist() {
        HiddenBookRepository repository = mock(HiddenBookRepository.class);
        List<HiddenBook> pool = IntStream.range(0, 200)
            .mapToObj(index -> new HiddenBook(
                "978" + String.format("%010d", index), "121018", "부산광역시 금정도서관",
                index < 80 ? "관계를 이해하는 심리책 " + index : "한국사 자료 " + index,
                "테스트 저자", "", index % 3, 80,
                index < 80 ? "인간관계와 마음 회복을 다루는 심리 안내서" : "한국 역사를 다루는 자료",
                index < 80 ? List.of("인간관계", "심리") : List.of("역사")
            ))
            .toList();
        when(repository.findAllByLibraryCode("121018")).thenReturn(pool);
        RecommendationService service = new RecommendationService(
            repository,
            openAiClient(),
            new ObjectMapper(),
            isbn -> Optional.of(new BookDetail(
                isbn, "관계를 이해하는 성인 심리서", "테스트 저자", "테스트 출판사", 2025,
                "", "성인의 인간관계와 마음 회복을 다루는 심리 안내서"
            ))
        );

        var result = service.recommend(new RecommendationRequest(
            "9788996991342", "121018", List.of("인간관계", "심리"), "마음의 위로", "따뜻한", 15
        ));

        assertThat(result).isNotEmpty().hasSizeLessThanOrEqualTo(15);
        assertThat(result).allSatisfy(book -> assertThat(book.keywordRelevance()).isGreaterThanOrEqualTo(35));
    }

    @Test
    void actualOpenAiExploresAtMostNineFromServerShortlist() {
        HiddenBookRepository repository = mock(HiddenBookRepository.class);
        List<HiddenBook> pool = IntStream.range(0, 200)
            .mapToObj(index -> new HiddenBook(
                "979" + String.format("%010d", index), "121018", "부산광역시 금정도서관",
                "삶의 의미를 탐구하는 심리책 " + index, "테스트 저자", "", index % 3, 80,
                "고난 속에서도 삶의 의미와 인간의 마음을 탐구하는 성인 심리서", List.of("삶의 의미", "심리")
            ))
            .toList();
        when(repository.findAllByLibraryCode("121018")).thenReturn(pool);
        RecommendationExploreService service = new RecommendationExploreService(
            repository,
            isbn -> Optional.of(new BookDetail(
                isbn, "죽음의 수용소에서", "빅터 프랭클", "테스트 출판사", 2020,
                "", "극한의 고난 속에서도 삶의 의미를 찾는 인간의 마음을 다룬 성인 심리서"
            )),
            openAiClient(),
            new ObjectMapper()
        );

        var result = service.explore(new ExploreRequest(
            "9788996991342", "121018", "SIMILAR_TOPIC"
        ));

        assertThat(result).isNotEmpty().hasSizeLessThanOrEqualTo(9);
        assertThat(result).allSatisfy(book -> assertThat(book.relevance()).isGreaterThanOrEqualTo(35));
    }

    @Test
    void actualTrendEvidenceIsConvertedToRelationshipPreservingMetadata() {
        GoogleTrendsRssClient google = new GoogleTrendsRssClient(
            new GoogleTrendsProperties("https://trends.google.com", "KR")
        );
        var sources = google.fetchDailyTrends("KR", 3);
        ObjectMapper objectMapper = new ObjectMapper();
        TrendProperties properties = new TrendProperties(20, 5, 2, .70, .70, .60, 10, 3, 30);
        TrendAiService service = new TrendAiService(
            trendOpenAiClient(), objectMapper, new TrendBookMatcher(objectMapper), properties
        );

        List<TrendEnrichment> results = service.enrich(sources.stream()
            .map(item -> new TrendAiService.EvidenceCandidate(item, java.util.List.of())).toList());

        // 프롬프트에 "후보를 몇 개든 전부 응답하라"는 지시가 없으면, 모델이 여러 후보 중 일부만
        // 응답하고 나머지는 조용히 빠뜨리는 문제가 있었다(2026-08-22 발견·수정, gpt-4o로 9개 중 1개만
        // 응답한 것으로 재현). 후보 수만큼 결과가 다 나오는지가 이 회귀의 핵심 검증 포인트다.
        assertThat(results).hasSize(sources.size());

        for (TrendEnrichment result : results) {
            assertThat(result.displayTopic()).isNotBlank();
            assertThat(result.contextDescription()).isNotBlank();
            assertThat(result.retrievalIntent()).isNotBlank();
            // 프롬프트가 topicConfidence/evidenceConsistencyScore의 의미를 설명하지 않고 JSON 스키마
            // 예시에 0.0을 그대로 남겨두면, 모델이 실제 판단 없이 그 예시값을 그대로 베껴 써서 모든
            // 트렌드가 조용히 EVIDENCE_MISMATCH로 걸러지는 문제가 있었다(2026-08-22 발견·수정).
            // 0보다 크다는 것만으로도 "예시값을 그대로 베끼는" 회귀는 잡아낼 수 있다.
            assertThat(result.topicConfidence()).isGreaterThan(0);
            assertThat(result.evidenceConsistencyScore()).isGreaterThan(0);
            // ELIGIBLE 트렌드는 책과 매칭 시도할 넓은 주제어(requiredConceptGroups)가 있어야 하고,
            // NO_BOOK_MATCH/SENSITIVE/EVIDENCE_MISMATCH는 애초에 책 매칭을 시도하지 않도록 비워야 한다.
            if (result.eligibility() == TrendEligibility.ELIGIBLE) {
                assertThat(result.requiredConceptGroups()).isNotEmpty();
            }
        }
    }

    private static OpenAiChatClient openAiClient() {
        return new OpenAiChatClient(new OpenAiProperties(
            "https://api.openai.com/v1",
            required("OPENAI_API_KEY"),
            environment.getOrDefault("OPENAI_MODEL", "gpt-4o-mini"),
            environment.getOrDefault("OPENAI_TREND_MODEL", "gpt-4o")
        ));
    }

    private static OpenAiChatClient trendOpenAiClient() {
        return new OpenAiChatClient(new OpenAiProperties(
            "https://api.openai.com/v1",
            required("OPENAI_API_KEY"),
            environment.getOrDefault("OPENAI_TREND_MODEL", "gpt-4o"),
            environment.getOrDefault("OPENAI_TREND_MODEL", "gpt-4o")
        ));
    }

    private static String required(String name) {
        String value = environment.get(name);
        assumeTrue(value != null && !value.isBlank(), name + " is not configured");
        return value;
    }

    private static void assertRequiredDetail(Optional<BookDetail> result) {
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().title()).isNotBlank();
        assertThat(result.orElseThrow().author()).isNotBlank();
        assertThat(result.orElseThrow().publisher()).isNotBlank();
        assertThat(result.orElseThrow().description()).isNotBlank();
    }

}
