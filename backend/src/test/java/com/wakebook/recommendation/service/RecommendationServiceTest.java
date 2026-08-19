package com.wakebook.recommendation.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final String LIBRARY_CODE = "121018";

    @Mock
    private HiddenBookRepository hiddenBookRepository;

    private FakeOpenAiClient fakeOpenAiClient;
    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        fakeOpenAiClient = new FakeOpenAiClient();
        recommendationService = new RecommendationService(hiddenBookRepository, fakeOpenAiClient, new ObjectMapper());
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
    void 후보_소개는_description을_우선하고_reason을_대체값으로_사용한다() {
        HiddenBook described = promptBook("9788960867450", null, "정보나루에서 수집한 소개글");
        HiddenBook fallback = promptBook("9999999999999", "기존 추천 이유", " ");
        when(hiddenBookRepository.findAllByLibraryCode(LIBRARY_CODE)).thenReturn(List.of(described, fallback));
        fakeOpenAiClient.setResponse("""
            {"results": [
              {"isbn": "9788960867450", "keywordRelevance": 90, "purposeMatch": 90, "moodMatch": 90, "reason": "이유1"},
              {"isbn": "9999999999999", "keywordRelevance": 80, "purposeMatch": 80, "moodMatch": 80, "reason": "이유2"}
            ]}
            """);

        recommendationService.recommend(new RecommendationRequest(
                "9788996991342", LIBRARY_CODE, List.of("인간관계"), "마음의 위로", "따뜻한", null
        ));

        assertThat(fakeOpenAiClient.lastUserPrompt())
                .contains("소개: 정보나루에서 수집한 소개글")
                .contains("소개: 기존 추천 이유")
                .doesNotContain("소개: null");
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

    private static HiddenBook promptBook(String isbn, String reason, String description) {
        return new HiddenBook(
                isbn, LIBRARY_CODE, "부산광역시 금정도서관", "후보 도서", "저자",
                "cover", 1, 80, reason, List.of("인간관계"),
                HiddenBookSource.LIBRARY_API, "100.1", "자료실", description
        );
    }
}
