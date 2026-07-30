package com.wakebook.recommendation.controller;

import com.wakebook.common.ApiException;
import com.wakebook.common.exception.GlobalExceptionHandler;
import com.wakebook.recommendation.dto.KeywordsResponse;
import com.wakebook.recommendation.service.KeywordGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiKeywordControllerTest {

    private KeywordGenerationService keywordGenerationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        keywordGenerationService = mock(KeywordGenerationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiKeywordController(keywordGenerationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 키워드_생성_요청은_서비스_결과를_그대로_반환한다() throws Exception {
        when(keywordGenerationService.generateKeywords("9788996991342"))
                .thenReturn(new KeywordsResponse(List.of("인간관계", "자존감", "심리", "행복", "용기")));

        mockMvc.perform(post("/ai/keywords")
                        .contentType("application/json")
                        .content("{\"isbn\": \"9788996991342\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keywords[0]").value("인간관계"));
    }

    @Test
    void isbn이_없으면_VALIDATION_001_예외() throws Exception {
        mockMvc.perform(post("/ai/keywords")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_001"));
    }

    @Test
    void 존재하지_않는_도서면_BOOK_001_예외를_그대로_전달한다() throws Exception {
        when(keywordGenerationService.generateKeywords("0000000000000"))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "BOOK_001", "도서를 찾을 수 없습니다."));

        mockMvc.perform(post("/ai/keywords")
                        .contentType("application/json")
                        .content("{\"isbn\": \"0000000000000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_001"));
    }
}
