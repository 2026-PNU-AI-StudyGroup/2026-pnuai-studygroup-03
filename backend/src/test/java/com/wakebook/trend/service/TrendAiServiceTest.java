package com.wakebook.trend.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.external.trend.TrendItem;
import com.wakebook.trend.domain.*;
import com.wakebook.trend.support.TrendProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAiServiceTest {
    @Test
    void enrichmentKeepsSourceKeyAndSeparatesDisplayTopic() {
        OpenAiClient ai = (system, user) -> """
            {"items":[
              {"sourceKey":"source-1","displayTopic":"항공 분야 AI 기술 도입","topicConfidence":0.92,
               "contextDescription":"항공 분야의 인공지능 기술 도입 논의가 주목받고 있습니다.",
               "retrievalIntent":"항공 운항과 공항 운영에 적용되는 인공지능 기술",
               "requiredConceptGroups":[["항공","공항"],["AI","인공지능"]],
               "eligibility":"ELIGIBLE","evidenceConsistencyScore":0.88},
              {"sourceKey":"invented","displayTopic":"잘못된 주제","topicConfidence":1.0,
               "contextDescription":"근거 없음","eligibility":"ELIGIBLE","evidenceConsistencyScore":1.0}
            ]}
            """;
        TrendAiService service = service(ai);
        TrendItem item = new TrendItem("source-1", "대한민국 국토교통부", "5K+", null, null, List.of(), 1);

        List<TrendEnrichment> result = service.enrich(List.of(new TrendAiService.EvidenceCandidate(item, List.of())));

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.sourceKey()).isEqualTo("source-1");
            assertThat(value.displayTopic()).isEqualTo("항공 분야 AI 기술 도입");
            assertThat(value.requiredConceptGroups()).hasSize(2);
            assertThat(value.topicConfidence()).isEqualTo(0.92);
            assertThat(value.evidenceConsistencyScore()).isEqualTo(0.88);
        });
    }

    @Test
    void enrichmentKeepsNoBookMatchEligibility() {
        OpenAiClient ai = (system, user) -> """
            {"items":[
              {"sourceKey":"source-1","displayTopic":"정선희, 연애에 대한 생각 공개","topicConfidence":0.8,
               "contextDescription":"방송인 정선희가 연애에 대한 생각이 별로 없다고 밝혔다.",
               "retrievalIntent":"정선희, 연애에 대한 생각 공개",
               "requiredConceptGroups":[["정선희"]],
               "eligibility":"NO_BOOK_MATCH","evidenceConsistencyScore":0.75}
            ]}
            """;
        TrendAiService service = service(ai);
        TrendItem item = new TrendItem("source-1", "정선희", "1K+", null, null, List.of(), 1);

        List<TrendEnrichment> result = service.enrich(List.of(new TrendAiService.EvidenceCandidate(item, List.of())));

        assertThat(result).singleElement().satisfies(value ->
            assertThat(value.eligibility()).isEqualTo(TrendEligibility.NO_BOOK_MATCH));
    }

    @Test
    void recommendationRejectsInventedIsbn() {
        OpenAiClient ai = (system, user) -> """
            {"items":[{"trendId":1,"recommendationTitle":"환율을 읽는 힘","books":[
              {"isbn":"9780000000001","matchScore":0.9,"reason":"환율의 원리를 이해하도록 돕습니다."},
              {"isbn":"9999999999999","matchScore":1.0,"reason":"존재하지 않는 책입니다."}
            ]}]}
            """;
        TrendAiService service = service(ai);
        DailyTrend trend = trend();
        setId(trend, 1L);
        HiddenBook book = new HiddenBook("9780000000001", "121018", "테스트도서관", "환율의 이해", "홍길동",
            null, 1, 90, null, List.of("경제", "환율"));

        List<TrendAiService.GeneratedRecommendation> result = service.recommend(List.of(trend), List.of(book), 3);

        assertThat(result).singleElement().satisfies(value -> assertThat(value.book().getIsbn()).isEqualTo("9780000000001"));
    }

    private TrendAiService service(OpenAiClient ai) {
        ObjectMapper mapper = new ObjectMapper();
        TrendProperties properties = new TrendProperties(20, 5, 3, .7, .7, .6, 10, 3, 30);
        return new TrendAiService(ai, mapper, new TrendBookMatcher(mapper), properties);
    }

    private DailyTrend trend() {
        return new DailyTrend(LocalDate.now(), "GOOGLE_TRENDS", "key", "환율 급등", "환율급등",
            "원·달러 환율 변동", .9, 1, "10K+", LocalDateTime.now(), null, "[]", "[]",
            "원·달러 환율의 변동성이 커지고 있습니다.", TrendEligibility.ELIGIBLE, .9,
            TrendValidationStatus.CONFIRMED, 1.5, .9, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }
    private void setId(DailyTrend trend, Long id) {
        try {
            var field = DailyTrend.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(trend, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
