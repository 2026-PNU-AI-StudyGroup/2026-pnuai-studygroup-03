package com.wakebook.recommendation.controller;

import com.wakebook.common.exception.GlobalExceptionHandler;
import com.wakebook.recommendation.dto.CompareResponse;
import com.wakebook.recommendation.dto.ExploreResponse;
import com.wakebook.recommendation.dto.RecommendationResponse;
import com.wakebook.recommendation.service.BookComparisonService;
import com.wakebook.recommendation.service.RecommendationExploreService;
import com.wakebook.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationControllerTest {

    private RecommendationService recommendationService;
    private BookComparisonService bookComparisonService;
    private RecommendationExploreService recommendationExploreService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        recommendationService = mock(RecommendationService.class);
        bookComparisonService = mock(BookComparisonService.class);
        recommendationExploreService = mock(RecommendationExploreService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecommendationController(recommendationService, bookComparisonService, recommendationExploreService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 추천_요청은_서비스_결과를_그대로_반환한다() throws Exception {
        when(recommendationService.recommend(any())).thenReturn(List.of(new RecommendationResponse(
                "9788960867450", "관계에도 연습이 필요합니다", "박상미", "https://example.com/cover.jpg",
                93, 95, 92, 90, 88, 89, "추천 이유", List.of("인간관계", "심리")
        )));

        mockMvc.perform(post("/recommendations")
                        .contentType("application/json")
                        .content("""
                            {"isbn": "9788996991342", "libraryCode": "121018", "keywords": ["인간관계", "심리"],
                             "purpose": "마음의 위로", "mood": "따뜻한", "readingTime": "MEDIUM"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isbn").value("9788960867450"))
                .andExpect(jsonPath("$.data[0].score").value(93));
    }

    @Test
    void 필수값이_없으면_VALIDATION_001_예외() throws Exception {
        mockMvc.perform(post("/recommendations")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_001"));
    }

    @Test
    void 비교_요청은_서비스_결과를_그대로_반환한다() throws Exception {
        when(bookComparisonService.compare(any())).thenReturn(new CompareResponse(
                List.of("인간관계", "심리"), "차이점 설명",
                new CompareResponse.BookProfile("보통", "철학적 대화"),
                new CompareResponse.BookProfile("쉬움", "일상 사례")
        ));

        mockMvc.perform(post("/recommendations/compare")
                        .contentType("application/json")
                        .content("{\"popularBook\": \"9788996991342\", \"hiddenBook\": \"9788960867450\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.difference").value("차이점 설명"));
    }

    @Test
    void 재탐색_요청은_서비스_결과를_그대로_반환한다() throws Exception {
        when(recommendationExploreService.explore(any())).thenReturn(List.of(new ExploreResponse(
                "9788960867450", "관계에도 연습이 필요합니다", "박상미", "https://example.com/cover.jpg",
                80, 80, 80, "더 깊이 있는 책", List.of("인간관계")
        )));

        mockMvc.perform(post("/recommendations/explore")
                        .contentType("application/json")
                        .content("{\"isbn\": \"9788960867450\", \"libraryCode\": \"121018\", \"type\": \"DEEPER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isbn").value("9788960867450"));
    }
}
