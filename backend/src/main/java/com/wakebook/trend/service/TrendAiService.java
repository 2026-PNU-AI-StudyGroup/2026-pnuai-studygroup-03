package com.wakebook.trend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.common.ApiException;
import com.wakebook.external.openai.OpenAiClient;
import com.wakebook.external.trend.NewsEvidence;
import com.wakebook.external.trend.TrendItem;
import com.wakebook.trend.domain.DailyTrend;
import com.wakebook.trend.domain.TrendEligibility;
import com.wakebook.trend.support.TrendProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrendAiService {
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final TrendBookMatcher bookMatcher;
    private final TrendProperties properties;

    public TrendAiService(@Qualifier("trendOpenAiClient") OpenAiClient openAiClient, ObjectMapper objectMapper,
        TrendBookMatcher bookMatcher, TrendProperties properties) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.bookMatcher = bookMatcher;
        this.properties = properties;
    }

    public List<TrendEnrichment> enrich(List<EvidenceCandidate> candidates) {
        String system = """
            당신은 도서관의 일일 트렌드 편집자입니다. 기사 근거에만 기반해 공통 주제를 재정의하세요.
            사용자 메시지로 받은 후보는 몇 개든 빠짐없이 전부 items 배열에 넣어 응답하세요. 일부만 응답하거나
            판단하기 애매하다고 건너뛰지 말고, 애매하면 eligibility를 NO_BOOK_MATCH나 EVIDENCE_MISMATCH로
            판정해서라도 모든 sourceKey에 대해 응답하세요.
            displayTopic은 인명·기관명만 반복하지 말고 실제 사건/쟁점을 드러내는 100자 이하의 짧은 구로 씁니다.
            topicConfidence는 displayTopic이 근거 기사들의 핵심 사건을 얼마나 정확히 요약했는지를 0~1 사이 점수로 매번 새로 판단해서 씁니다.
            근거가 분명하고 주제가 명확할수록 1에 가깝게, 근거가 빈약하거나 주제가 모호할수록 0에 가깝게 매깁니다. 아래 예시의 숫자를 그대로 베끼지 마세요.
            contextDescription은 확인된 사실만 중립적인 한국어 1~2문장으로 씁니다.
            evidenceConsistencyScore는 근거로 준 기사들이 서로 같은 사건을 가리키는 정도를 0~1 사이 점수로 매번 새로 판단해서 씁니다.
            기사들이 일관되게 같은 사건을 다룰수록 1에 가깝게, 기사마다 다른 사건을 다루거나 근거가 약할수록 0에 가깝게 매깁니다. 아래 예시의 숫자를 그대로 베끼지 마세요.
            retrievalIntent는 displayTopic의 관계를 보존한 한 문장으로 씁니다. '항공 AI'를 '항공'과 'AI'로 따로 검색하지 마세요.
            requiredConceptGroups는 책의 제목·설명·키워드에 실제로 나올 법한 넓은 주제어의 개념군입니다(책이
            모두 충족해야 하므로 외부 배열끼리는 AND, 각 내부 배열은 그 주제를 표현하는 동의어들을 OR로 묶음).
            뉴스 기사에만 나오는 고유명사·구체적 행동(인물명·단체명·지하철·중단 같은 사건 세부사항)을 그대로
            옮기지 말고, 그 사건이 속한 더 넓은 사회적·학문적 주제(예: 장애인 이동권, 사회복지 정책, 스포츠
            산업 구조)로 바꿔 쓰세요. 그렇게 바꿔도 남는 넓은 주제가 없으면 requiredConceptGroups를
            비워두고(빈 배열) eligibility를 NO_BOOK_MATCH로 판정하세요. 그룹은 1~2개면 충분하고, 한 그룹에
            단어를 1개만 넣지 말고 관련 동의어를 최소 3개 이상 채우세요(예시처럼 구체적일수록 좋습니다).
            eligibility 판정 기준(하나만 고릅니다). 아래 규칙은 "더 넓은 주제로 확장할 수 있는가"를 스스로
            상상해서 판단하지 말고, 트렌드의 핵심 소재가 무엇인지로 기계적으로 분류하세요:
            - EVIDENCE_MISMATCH: 근거 기사들이 서로 다른 사건을 섞어서 다룸.
            - SENSITIVE: 참사·범죄 피해·정치 선동 등 도서관이 도서 홍보에 쓰기 부적합한 소재.
            - NO_BOOK_MATCH: 핵심 소재가 아래 중 하나면 해당합니다. displayTopic에서 그 인물/팀/경기/
              사건 이름을 빼면 남는 내용이 없으면 NO_BOOK_MATCH입니다.
              (a) 특정 연예인 개인의 근황(결혼·열애·재산·발언·수상 등)
              (b) 특정 경기의 결과·순위·중계
              (c) 특정 개별 사건·사고 하나 자체(실종·화재·교통사고·범죄 의혹·특정 기관의 대응 논란 등) —
                  "이 사건이 어떻게 진행/처리됐는지"가 관심의 초점이면 해당됩니다. 그 사건이 계기가 되어
                  논의되는 정책·제도 자체가 트렌드의 핵심일 때만 예외로 ELIGIBLE입니다.
              "기부니까 사회공헌과 통한다", "연애관이니까 인간관계와 통한다", "이 사건도 결국 사회문제다"처럼
              소재를 억지로 넓혀 해석해서 ELIGIBLE로 올리지 마세요 — 실제로 그 넓힌 주제를 다룬 책과
              연결되는 경우는 거의 없습니다.
            - ELIGIBLE: 위 세 경우가 아니고, 특정 개인/경기/개별 사건이 아니라 정책·제도·사회운동·산업·
              과학·역사·문화현상 자체가 트렌드의 핵심 소재일 때만 해당합니다(예: 특정 시위 참가자 개인사가
              아니라 그 시위가 촉발한 정책 이슈 자체, 특정 선수 개인사가 아니라 그 종목의 산업·제도 변화 자체).
            반드시 JSON만 응답하세요(topicConfidence·evidenceConsistencyScore는 아래 예시값이 아니라 매번 새로 계산한 값을 넣으세요): {"items":[{"sourceKey":"...","displayTopic":"...","topicConfidence":0.85,"contextDescription":"...","retrievalIntent":"...","requiredConceptGroups":[["항공","비행","공항"],["AI","인공지능","머신러닝"]],"eligibility":"ELIGIBLE|SENSITIVE|NO_BOOK_MATCH|EVIDENCE_MISMATCH","evidenceConsistencyScore":0.9}]}
            """;
        String content = openAiClient.complete(system, toJson(candidates));
        try {
            EnrichmentPayload payload = objectMapper.readValue(content, EnrichmentPayload.class);
            Set<String> allowed = candidates.stream().map(c -> c.item().sourceKey()).collect(Collectors.toSet());
            if (payload.items() == null) return List.of();
            return payload.items().stream().filter(item -> allowed.contains(item.sourceKey()))
                .filter(item -> item.displayTopic() != null && !item.displayTopic().isBlank()
                    && item.displayTopic().length() <= 100)
                .map(item -> new TrendEnrichment(item.sourceKey(), item.displayTopic().strip(),
                    clamp(item.topicConfidence()), safeText(item.contextDescription(), 1000),
                    retrievalIntent(item), conceptGroups(item.requiredConceptGroups()),
                    parseEligibility(item.eligibility()), clamp(item.evidenceConsistencyScore())))
                .toList();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 트렌드 분석 결과를 해석하지 못했습니다.");
        }
    }

    public List<GeneratedRecommendation> recommend(List<DailyTrend> trends, List<HiddenBook> books, int booksPerTrend) {
        Map<Long, DailyTrend> trendMap = trends.stream().collect(Collectors.toMap(DailyTrend::getId, Function.identity()));
        Map<String, HiddenBook> bookMap = books.stream().collect(Collectors.toMap(HiddenBook::getIsbn,
            Function.identity(), (a, b) -> a));
        List<RecommendationInput> inputs = trends.stream().map(trend -> new RecommendationInput(
            trend.getId(), trend.getDisplayTopic(), trend.getContextDescription(), trend.getRetrievalIntent(),
            bookInputs(trend, books))).toList();
        Map<Long, Map<String, BookInput>> inputByTrend = inputs.stream().collect(Collectors.toMap(
            RecommendationInput::trendId, input -> input.books().stream()
                .collect(Collectors.toMap(BookInput::isbn, Function.identity(), (a, b) -> a))));

        String system = """
            당신은 도서관 사서입니다. 주어진 트렌드와 실제 보유 도서 후보만 연결하세요.
            retrievalIntent의 전체 관계와 서버의 필수 개념 검증을 통과한 후보만 평가하세요.
            한쪽 개념에만 관련된 책은 선택하지 마세요. 예: '항공 AI'에서 일반 AI 입문서나 일반 항공서는 제외합니다.
            ISBN을 절대 새로 만들지 말고 트렌드별 최대 요청 권수만 선택합니다.
            recommendationTitle은 전시형 한국어 제목, reason은 책과 트렌드의 연결을 설명하는 과장 없는 1문장입니다.
            matchScore는 0~1이고 전체 관계가 책 설명에서 직접 확인될 때만 0.6 이상입니다.
            반드시 JSON만 응답하세요: {"items":[{"trendId":1,"recommendationTitle":"...","books":[{"isbn":"...","matchScore":0.0,"reason":"..."}]}]}
            """;
        String content = openAiClient.complete(system, "트렌드별 최대 권수: " + booksPerTrend + "\n" + toJson(inputs));
        try {
            RecommendationPayload payload = objectMapper.readValue(content, RecommendationPayload.class);
            if (payload.items() == null) return List.of();
            List<GeneratedRecommendation> result = new ArrayList<>();
            for (AiRecommendation recommendation : payload.items()) {
                if (!trendMap.containsKey(recommendation.trendId()) || recommendation.books() == null) continue;
                Map<String, BookInput> allowed = inputByTrend.getOrDefault(recommendation.trendId(), Map.of());
                Set<String> used = new HashSet<>();
                int order = 0;
                for (AiBook aiBook : recommendation.books()) {
                    HiddenBook book = bookMap.get(aiBook.isbn());
                    BookInput input = allowed.get(aiBook.isbn());
                    double combinedScore = input == null ? 0
                        : input.serverMatchScore() * .55 + clamp(aiBook.matchScore()) * .45;
                    if (book == null || input == null || combinedScore < properties.minimumBookMatchScore()
                        || !used.add(book.getIsbn()) || order >= booksPerTrend) continue;
                    result.add(new GeneratedRecommendation(recommendation.trendId(),
                        safeText(recommendation.recommendationTitle(), 200), book,
                        safeText(aiBook.reason(), 1000), ++order, combinedScore));
                }
            }
            return result;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 도서 추천 결과를 해석하지 못했습니다.");
        }
    }

    private List<BookInput> bookInputs(DailyTrend trend, List<HiddenBook> books) {
        return bookMatcher.shortlist(trend, books, 15).stream().map(match -> new BookInput(
            match.book().getIsbn(), match.book().getTitle(), match.book().getAuthor(),
            safeText(match.book().getDescription(), 500), match.book().getKeywords(),
            match.serverScore(), match.matchedConcepts())).toList();
    }

    private String retrievalIntent(AiEnrichment item) {
        String value = safeText(item.retrievalIntent(), 500);
        return value.isBlank() ? item.displayTopic() + ". " + safeText(item.contextDescription(), 1000) : value;
    }

    private List<List<String>> conceptGroups(List<List<String>> groups) {
        if (groups == null) return List.of();
        return groups.stream().filter(Objects::nonNull)
            .map(group -> group.stream().filter(Objects::nonNull).map(value -> safeText(value, 50))
                .filter(value -> !value.isBlank()).distinct().limit(8).toList())
            .filter(group -> !group.isEmpty()).limit(4).toList();
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001", "AI 요청을 만들지 못했습니다."); }
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static String safeText(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.length() <= max ? text : text.substring(0, max);
    }
    private static TrendEligibility parseEligibility(String value) {
        try { return TrendEligibility.valueOf(value); }
        catch (Exception ignored) { return TrendEligibility.EVIDENCE_MISMATCH; }
    }

    public record EvidenceCandidate(TrendItem item, List<NewsEvidence> naverEvidence) {}
    public record GeneratedRecommendation(Long trendId, String recommendationTitle, HiddenBook book,
                                          String reason, int order, double matchScore) {}
    record RecommendationInput(Long trendId, String displayTopic, String contextDescription,
                               String retrievalIntent, List<BookInput> books) {}
    record BookInput(String isbn, String title, String author, String description, List<String> keywords,
                     double serverMatchScore, List<String> matchedConcepts) {}
    record EnrichmentPayload(@JsonProperty("items") List<AiEnrichment> items) {}
    record AiEnrichment(String sourceKey, String displayTopic, double topicConfidence, String contextDescription,
                        String retrievalIntent, List<List<String>> requiredConceptGroups,
                        String eligibility, double evidenceConsistencyScore) {}
    record RecommendationPayload(@JsonProperty("items") List<AiRecommendation> items) {}
    record AiRecommendation(Long trendId, String recommendationTitle, List<AiBook> books) {}
    record AiBook(String isbn, double matchScore, String reason) {}
}
