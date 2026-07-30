package com.wakebook.recommendation.service;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.FakeBookDetailProvider;
import com.wakebook.external.openai.FakeOpenAiClient;
import com.wakebook.recommendation.dto.KeywordsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordGenerationServiceTest {

    private FakeBookDetailProvider fakeBookDetailProvider;
    private FakeOpenAiClient fakeOpenAiClient;
    private KeywordGenerationService keywordGenerationService;

    @BeforeEach
    void setUp() {
        fakeBookDetailProvider = new FakeBookDetailProvider();
        fakeOpenAiClient = new FakeOpenAiClient();
        keywordGenerationService = new KeywordGenerationService(fakeBookDetailProvider, fakeOpenAiClient, new ObjectMapper());
    }

    @Test
    void AI가_반환한_키워드를_그대로_응답한다() {
        fakeOpenAiClient.setResponse("{\"keywords\": [\"인간관계\", \"자존감\", \"심리\", \"행복\", \"용기\"]}");

        KeywordsResponse response = keywordGenerationService.generateKeywords("9788996991342");

        assertThat(response.keywords()).containsExactly("인간관계", "자존감", "심리", "행복", "용기");
    }

    @Test
    void 존재하지_않는_도서면_BOOK_001_예외() {
        fakeBookDetailProvider.makeEmpty();

        assertThatThrownBy(() -> keywordGenerationService.generateKeywords("0000000000000"))
                .isInstanceOf(ApiException.class)
                .hasMessage("도서를 찾을 수 없습니다.");
    }

    @Test
    void AI_응답이_JSON이_아니면_AI_001_예외() {
        fakeOpenAiClient.setResponse("이건 JSON이 아닙니다");

        assertThatThrownBy(() -> keywordGenerationService.generateKeywords("9788996991342"))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI 추천 생성에 실패했습니다.");
    }

    @Test
    void isbn이_비어있으면_VALIDATION_001_예외() {
        assertThatThrownBy(() -> keywordGenerationService.generateKeywords(" "))
                .isInstanceOf(ApiException.class)
                .hasMessage("isbn은 필수입니다.");
    }
}
