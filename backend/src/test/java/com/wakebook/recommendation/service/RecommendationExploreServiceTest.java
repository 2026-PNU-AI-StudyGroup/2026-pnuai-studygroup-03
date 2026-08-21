package com.wakebook.recommendation.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.FakeBookDetailProvider;
import com.wakebook.external.openai.FakeOpenAiClient;
import com.wakebook.recommendation.dto.ExploreRequest;
import com.wakebook.recommendation.dto.ExploreResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationExploreServiceTest {

    private static final String LIBRARY_CODE = "121018";

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    private FakeBookDetailProvider fakeBookDetailProvider;
    private FakeOpenAiClient fakeOpenAiClient;
    private RecommendationExploreService recommendationExploreService;

    @BeforeEach
    void setUp() {
        fakeBookDetailProvider = new FakeBookDetailProvider();
        fakeOpenAiClient = new FakeOpenAiClient();
        recommendationExploreService = new RecommendationExploreService(
                hiddenBookRepository, fakeBookDetailProvider, fakeOpenAiClient, new ObjectMapper()
        );
    }

    @Test
    void 기준_도서_자신은_후보에서_제외하고_점수순으로_정렬한다() {
        HiddenBook self = new HiddenBook(
                "9788996991342", LIBRARY_CODE, "부산광역시 금정도서관", "기준도서", "저자", "cover0", 1, 80, "이유", List.of()
        );
        HiddenBook other = new HiddenBook(
                "9788960867450", LIBRARY_CODE, "부산광역시 금정도서관", "관계에도 연습이 필요합니다", "박상미",
                "cover1", 1, 80, "추천 이유", List.of("인간관계")
        );
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(self, other));
        fakeOpenAiClient.setResponse("""
            {"results": [{"isbn": "9788960867450", "relevance": 90, "reason": "더 깊이 있는 책"}]}
            """);

        List<ExploreResponse> result = recommendationExploreService.explore(
                new ExploreRequest("9788996991342", LIBRARY_CODE, "DEEPER")
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isbn()).isEqualTo("9788960867450");
        assertThat(result.get(0).reason()).isEqualTo("더 깊이 있는 책");
    }

    @Test
    void 후보군_200권_중_서버_상위_50권만_AI에_보낸다() {
        List<HiddenBook> pool = IntStream.range(0, 200)
            .mapToObj(index -> new HiddenBook(
                "978" + String.format("%010d", index), LIBRARY_CODE, "부산광역시 금정도서관",
                "관계를 탐구하는 책 " + index, "저자", "cover", index % 3, 80,
                "사람 사이의 관계와 마음을 탐구한다", List.of("인간관계")
            ))
            .toList();
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(pool);
        fakeOpenAiClient.setResponse("{\"results\": []}");

        recommendationExploreService.explore(new ExploreRequest(
            "9788996991342", LIBRARY_CODE, "SIMILAR_TOPIC"
        ));

        long candidateCount = fakeOpenAiClient.lastUserPrompt().lines()
            .filter(line -> line.startsWith("- isbn:"))
            .count();
        assertThat(candidateCount).isEqualTo(50);
        assertThat(fakeOpenAiClient.lastSystemPrompt()).contains("가장 적합한 책만 최대 9권 선택");
    }

    @Test
    void 성인_기준_도서에는_어린이_서가_후보를_AI에_보내지_않는다() {
        fakeBookDetailProvider.setDetail(new com.wakebook.external.library.BookDetail(
            "9788996991342", "성인의 관계", "저자", "출판사", 2025,
            "cover", "성인의 삶과 관계를 탐구하는 책"
        ));
        HiddenBook child = new HiddenBook(
            "9781111111111", LIBRARY_CODE, "부산광역시 금정도서관", "어린이 관계 만화", "저자",
            "cover", 0, 100, "어린이를 위한 관계 학습만화", List.of("인간관계"),
            HiddenBookSource.LIBRARY_API, "813.8-1", "[금정]어린이자료실(만화코너)",
            "어린이를 위한 관계 학습만화"
        );
        HiddenBook adult = new HiddenBook(
            "9782222222222", LIBRARY_CODE, "부산광역시 금정도서관", "어른의 관계", "저자",
            "cover", 1, 80, "성인의 관계를 다루는 책", List.of("인간관계"),
            HiddenBookSource.LIBRARY_API, "189-1", "[금정]종합자료실", "성인의 관계를 다루는 책"
        );
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(child, adult));
        fakeOpenAiClient.setResponse("""
            {"results":[{"isbn":"9782222222222","relevance":90,"reason":"성인 독자에게 맞음"}]}
            """);

        List<ExploreResponse> result = recommendationExploreService.explore(new ExploreRequest(
            "9788996991342", LIBRARY_CODE, "DEEPER"
        ));

        assertThat(fakeOpenAiClient.lastUserPrompt())
            .doesNotContain("9781111111111")
            .contains("9782222222222", "예상 독자층: ADULT");
        assertThat(result).extracting(ExploreResponse::isbn).containsExactly("9782222222222");
    }

    @Test
    void 지원하지_않는_재탐색_유형이면_VALIDATION_001_예외() {
        assertThatThrownBy(() -> recommendationExploreService.explore(
                new ExploreRequest("9788996991342", LIBRARY_CODE, "UNKNOWN_TYPE")
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void 기준_도서를_찾을_수_없으면_BOOK_001_예외() {
        fakeBookDetailProvider.makeEmpty();

        assertThatThrownBy(() -> recommendationExploreService.explore(
                new ExploreRequest("0000000000000", LIBRARY_CODE, "DEEPER")
        )).isInstanceOf(ApiException.class).hasMessage("도서를 찾을 수 없습니다.");
    }

    @Test
    void libraryCode가_없으면_VALIDATION_001_예외() {
        assertThatThrownBy(() -> recommendationExploreService.explore(
                new ExploreRequest("9788996991342", " ", "DEEPER")
        )).isInstanceOf(ApiException.class);
    }
}
