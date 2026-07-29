package com.wakebook.recommendation.service;

import com.wakebook.book.domain.HiddenBook;
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
