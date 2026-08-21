package com.wakebook.external.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wakebook.trend.domain.TrendValidationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

@Component
public class NaverSearchTrendClient implements SearchTrendValidator {
    private static final Logger log = LoggerFactory.getLogger(NaverSearchTrendClient.class);

    private final RestClient restClient;
    private final NaverApiProperties properties;
    private final ObjectMapper objectMapper;
    public NaverSearchTrendClient(NaverApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.objectMapper = new ObjectMapper();
    }
    @Override
    public SearchTrendValidation validate(String sourceKeyword, String displayTopic) {
        if (!properties.configured()) return SearchTrendValidation.unverified();
        LocalDate end = LocalDate.now();
        Request request = new Request(end.minusDays(13).toString(), end.toString(), "date",
            List.of(new KeywordGroup("topic", List.of(sourceKeyword, displayTopic))));
        try {
            String body = restClient.post().uri("/v1/datalab/search")
                .header("X-Naver-Client-Id", properties.clientId())
                .header("X-Naver-Client-Secret", properties.clientSecret())
                .body(request).retrieve().body(String.class);
            Response response = body == null ? null : objectMapper.readValue(body, Response.class);
            if (response == null || response.results() == null || response.results().isEmpty())
                return SearchTrendValidation.unverified();
            List<DataPoint> data = response.results().getFirst().data();
            if (data == null || data.size() < 7) return SearchTrendValidation.unverified();
            double recent = data.stream().skip(Math.max(0, data.size() - 2)).mapToDouble(DataPoint::ratio).average().orElse(0);
            int baselineStart = Math.max(0, data.size() - 9);
            int baselineEnd = data.size() - 2;
            double baseline = data.stream().skip(baselineStart).limit(Math.max(1, baselineEnd - baselineStart))
                .mapToDouble(DataPoint::ratio).average().orElse(0);
            double spike = baseline <= 0.01 ? (recent > 0 ? 2.0 : 0.0) : recent / baseline;
            TrendValidationStatus status = spike >= 1.15 ? TrendValidationStatus.CONFIRMED
                : spike < 0.75 ? TrendValidationStatus.CONTRADICTED : TrendValidationStatus.UNVERIFIED;
            return new SearchTrendValidation(status, spike);
        } catch (Exception e) {
            log.warn("NAVER 데이터랩 검색어 트렌드 검증에 실패했습니다. (sourceKeyword={})", sourceKeyword, e);
            return SearchTrendValidation.unverified();
        }
    }
    record Request(String startDate, String endDate, String timeUnit, List<KeywordGroup> keywordGroups) {}
    record KeywordGroup(@JsonProperty("groupName") String groupName, List<String> keywords) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(List<Result> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Result(List<DataPoint> data) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record DataPoint(String period, double ratio) {}
}
