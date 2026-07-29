package com.wakebook.recommendation.service;

import com.wakebook.common.ApiException;
import com.wakebook.external.library.FakeBookDetailProvider;
import com.wakebook.external.openai.FakeOpenAiClient;
import com.wakebook.recommendation.dto.CompareRequest;
import com.wakebook.recommendation.dto.CompareResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookComparisonServiceTest {

    private FakeBookDetailProvider fakeBookDetailProvider;
    private FakeOpenAiClient fakeOpenAiClient;
    private BookComparisonService bookComparisonService;

    @BeforeEach
    void setUp() {
        fakeBookDetailProvider = new FakeBookDetailProvider();
        fakeOpenAiClient = new FakeOpenAiClient();
        bookComparisonService = new BookComparisonService(fakeBookDetailProvider, fakeOpenAiClient, new ObjectMapper());
    }

    @Test
    void AI가_반환한_비교결과를_그대로_응답한다() {
        fakeOpenAiClient.setResponse("""
            {"commonKeywords": ["인간관계", "심리", "자존감"],
             "difference": "두 책 모두 타인의 시선에서 벗어나는 태도를 다룹니다.",
             "popularBookProfile": {"difficulty": "보통", "style": "철학적 대화"},
             "hiddenBookProfile": {"difficulty": "쉬움", "style": "일상 사례"}}
            """);

        CompareResponse response = bookComparisonService.compare(
                new CompareRequest("9788996991342", "9788960867450")
        );

        assertThat(response.commonKeywords()).containsExactly("인간관계", "심리", "자존감");
        assertThat(response.popularBookProfile().difficulty()).isEqualTo("보통");
        assertThat(response.hiddenBookProfile().style()).isEqualTo("일상 사례");
    }

    @Test
    void 존재하지_않는_도서면_BOOK_001_예외() {
        fakeBookDetailProvider.makeEmpty();

        assertThatThrownBy(() -> bookComparisonService.compare(
                new CompareRequest("0000000000000", "9788960867450")
        )).isInstanceOf(ApiException.class).hasMessage("도서를 찾을 수 없습니다.");
    }
}
