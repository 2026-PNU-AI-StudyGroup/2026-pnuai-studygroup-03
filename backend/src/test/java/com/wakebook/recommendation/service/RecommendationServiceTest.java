package com.wakebook.recommendation.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.openai.FakeOpenAiClient;
import com.wakebook.recommendation.dto.RecommendationRequest;
import com.wakebook.recommendation.dto.RecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final String LIBRARY_CODE = "121018";

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    @Mock
    private BookDetailProvider bookDetailProvider;

    private FakeOpenAiClient fakeOpenAiClient;
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        fakeOpenAiClient = new FakeOpenAiClient();
        recommendationService = new RecommendationService(
            hiddenBookRepository, fakeOpenAiClient, new ObjectMapper(), bookDetailProvider
        );
        lenient().when(bookDetailProvider.fetch(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Optional.of(new BookDetail(
                "9788996991342", "성인 소설", "작가", "출판사", 2025,
                "cover", "성인의 삶과 인간관계를 깊이 탐구하는 소설"
            )));
    }

    @Test
    void AI_점수와_규칙기반_점수를_합쳐_점수순으로_정렬한다() {
        HiddenBook lowLoan = new HiddenBook(
                "9788960867450", LIBRARY_CODE, "부산광역시 금정도서관", "관계에도 연습이 필요합니다", "박상미",
                "cover1", 1, 80, "추천 이유1", List.of("인간관계")
        );
        HiddenBook highLoan = new HiddenBook(
                "9999999999999", LIBRARY_CODE, "부산광역시 금정도서관", "다른책", "다른저자",
                "cover2", 50, 80, "추천 이유2", List.of("심리")
        );
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(lowLoan, highLoan));
        fakeOpenAiClient.setResponse("""
            {"results": [
              {"isbn": "9788960867450", "keywordRelevance": 95, "purposeMatch": 92, "moodMatch": 90, "reason": "이유1"},
              {"isbn": "9999999999999", "keywordRelevance": 50, "purposeMatch": 50, "moodMatch": 50, "reason": "이유2"}
            ]}
            """);

        List<RecommendationResponse> result = recommendationService.recommend(new RecommendationRequest(
                "9788996991342", LIBRARY_CODE, List.of("인간관계", "심리"), "마음의 위로", "따뜻한", null
        ));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isbn()).isEqualTo("9788960867450");
        assertThat(result.get(0).discoveryValue()).isEqualTo(100);
        assertThat(result.get(1).isbn()).isEqualTo("9999999999999");
        assertThat(result.get(1).discoveryValue()).isEqualTo(0);
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }

    @Test
    void 후보군이_없으면_빈_목록을_반환한다() {
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of());

        List<RecommendationResponse> result = recommendationService.recommend(new RecommendationRequest(
                "9788996991342", LIBRARY_CODE, List.of("인간관계"), "마음의 위로", "따뜻한", null
        ));

        assertThat(result).isEmpty();
    }

    @Test
    void 후보군_200권은_유지하고_관련도_상위_60권만_AI에_보낸다() {
        List<HiddenBook> books = new java.util.ArrayList<>(IntStream.range(0, 199)
            .mapToObj(index -> new HiddenBook(
                "978" + String.format("%010d", index), LIBRARY_CODE, "부산광역시 금정도서관",
                index < 80 ? "관계를 이해하는 책 " + index : "무관한 역사책 " + index,
                "저자", "cover", 1, 100,
                index < 80 ? "인간관계 소개" : "역사 소개",
                index < 80 ? List.of("인간관계") : List.of("역사")
            ))
            .toList());
        HiddenBook relevant = new HiddenBook(
            "9799999999999", LIBRARY_CODE, "부산광역시 금정도서관",
            "관계를 이해하는 법", "저자", "cover", 1, 200,
            "인간관계를 다루는 책", List.of("인간관계")
        );
        books.add(relevant);
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(books);
        fakeOpenAiClient.setResponse("{\"results\": []}");

        recommendationService.recommend(new RecommendationRequest(
            "9788996991342", LIBRARY_CODE, List.of("인간관계"), "마음의 위로", "따뜻한", 15
        ));

        long candidateCount = fakeOpenAiClient.lastUserPrompt().lines()
            .filter(line -> line.startsWith("- isbn:"))
            .count();
        assertThat(candidateCount).isEqualTo(60);
        assertThat(fakeOpenAiClient.lastUserPrompt()).contains("isbn: 9799999999999");
        assertThat(fakeOpenAiClient.lastUserPrompt()).doesNotContain("무관한 역사책");
        assertThat(fakeOpenAiClient.lastSystemPrompt()).contains("가장 적합한 도서만 최대 15권 선택");
    }

    @Test
    void 성인_기준_도서에는_어린이_서가_후보를_AI에_보내지_않는다() {
        HiddenBook childBook = new HiddenBook(
            "9781111111111", LIBRARY_CODE, "부산광역시 금정도서관", "어린이 관계 동화", "저자",
            "cover", 0, 100, "인간관계를 배우는 어린이 동화", List.of("인간관계"),
            com.wakebook.book.domain.HiddenBookSource.LIBRARY_API,
            "813.8-1", "[금정]어린이자료실(만화코너)", "인간관계를 배우는 어린이 동화"
        );
        HiddenBook adultBook = new HiddenBook(
            "9782222222222", LIBRARY_CODE, "부산광역시 금정도서관", "어른의 관계", "저자",
            "cover", 1, 80, "성인의 인간관계를 다루는 책", List.of("인간관계"),
            com.wakebook.book.domain.HiddenBookSource.LIBRARY_API,
            "189-1", "[금정]종합자료실", "성인의 인간관계를 다루는 책"
        );
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(childBook, adultBook));
        fakeOpenAiClient.setResponse("""
            {"results":[
              {"isbn":"9781111111111","keywordRelevance":99,"purposeMatch":99,"moodMatch":99,"reason":"잘못된 추천"},
              {"isbn":"9782222222222","keywordRelevance":90,"purposeMatch":90,"moodMatch":90,"reason":"성인 독자 추천"}
            ]}
            """);

        List<RecommendationResponse> result = recommendationService.recommend(new RecommendationRequest(
            "9788996991342", LIBRARY_CODE, List.of("인간관계"), "마음의 위로", "사색적인", 15
        ));

        assertThat(fakeOpenAiClient.lastUserPrompt())
            .doesNotContain("9781111111111")
            .contains("9782222222222", "기준 인기 도서 제목: 성인 소설", "예상 독자층: ADULT");
        assertThat(result).extracting(RecommendationResponse::isbn).containsExactly("9782222222222");
    }

    @Test
    void 서버_단어가_일치하지_않아도_독자층이_맞으면_AI가_의미_관련성을_판단한다() {
        HiddenBook unrelated = new HiddenBook(
            "9781111111111", LIBRARY_CODE, "부산광역시 금정도서관", "사람 사이가 만든 역사", "저자",
            "cover", 0, 100, "역사 속 인물들의 관계와 선택", List.of("역사")
        );
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(unrelated));
        fakeOpenAiClient.setResponse("""
            {"results":[
              {"isbn":"9781111111111","keywordRelevance":75,"purposeMatch":80,"moodMatch":80,"reason":"의미 관련성 확인"}
            ]}
            """);

        List<RecommendationResponse> result = recommendationService.recommend(new RecommendationRequest(
            "9788996991342", LIBRARY_CODE, List.of("인간관계"), "마음의 위로", "사색적인", 15
        ));

        assertThat(result).extracting(RecommendationResponse::isbn).containsExactly("9781111111111");
        assertThat(fakeOpenAiClient.callCount()).isEqualTo(1);
    }

    @Test
    void 기준_인기_도서는_잠자는_책_후보에서_제외한다() {
        String sourceIsbn = "9788996991342";
        HiddenBook sourceBook = new HiddenBook(
            sourceIsbn, LIBRARY_CODE, "부산광역시 금정도서관", "기준책", "저자",
            "cover", 1, 100, "소개", List.of("인간관계")
        );
        HiddenBook otherBook = new HiddenBook(
            "9799999999999", LIBRARY_CODE, "부산광역시 금정도서관", "다른책", "저자",
            "cover", 1, 80, "소개", List.of("인간관계")
        );
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(sourceBook, otherBook));
        fakeOpenAiClient.setResponse("{\"results\": []}");

        recommendationService.recommend(new RecommendationRequest(
            sourceIsbn, LIBRARY_CODE, List.of("인간관계"), "마음의 위로", "따뜻한", 15
        ));

        assertThat(fakeOpenAiClient.lastUserPrompt())
            .doesNotContain("isbn: " + sourceIsbn)
            .contains("isbn: 9799999999999");
    }

    @Test
    void 지원하지_않는_독서_목적이면_VALIDATION_001_예외() {
        assertThatThrownBy(() -> recommendationService.recommend(new RecommendationRequest(
                "9788996991342", LIBRARY_CODE, List.of("인간관계"), "알수없는목적", "따뜻한", null
        ))).isInstanceOf(ApiException.class);
    }

    @Test
    void libraryCode가_없으면_VALIDATION_001_예외() {
        assertThatThrownBy(() -> recommendationService.recommend(new RecommendationRequest(
                "9788996991342", " ", List.of("인간관계"), "마음의 위로", "따뜻한", null
        ))).isInstanceOf(ApiException.class);
    }
}
