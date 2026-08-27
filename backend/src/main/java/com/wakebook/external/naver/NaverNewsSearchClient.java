package com.wakebook.external.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wakebook.external.trend.NewsEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class NaverNewsSearchClient implements NewsEvidenceProvider {
    private static final Logger log = LoggerFactory.getLogger(NaverNewsSearchClient.class);

    private final RestClient restClient;
    private final NaverApiProperties properties;
    private final ObjectMapper objectMapper;
    public NaverNewsSearchClient(NaverApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.objectMapper = new ObjectMapper();
    }
    @Override
    public List<NewsEvidence> search(String keyword, int limit) {
        if (!properties.configured()) return List.of();
        try {
            String body = restClient.get().uri(uri -> uri.path("/v1/search/news.json")
                    .queryParam("query", keyword).queryParam("display", Math.min(10, limit))
                    .queryParam("sort", "date").build())
                .header("X-Naver-Client-Id", properties.clientId())
                .header("X-Naver-Client-Secret", properties.clientSecret())
                .retrieve().body(String.class);
            Response response = body == null ? null : objectMapper.readValue(body, Response.class);
            if (response == null || response.items() == null) return List.of();
            return response.items().stream().map(item -> new NewsEvidence(clean(item.title()),
                clean(item.description()), firstNonBlank(item.originallink(), item.link()), "NAVER 뉴스",
                parseDate(item.pubDate()))).toList();
        } catch (Exception e) {
            log.warn("NAVER 뉴스 검색 API 호출에 실패했습니다. (keyword={})", keyword, e);
            return List.of();
        }
    }
    private static String clean(String value) {
        if (value == null) return null;
        return value.replaceAll("<[^>]+>", "").replace("&quot;", "\"")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    }
    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
    private static java.time.LocalDateTime parseDate(String value) {
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime(); }
        catch (Exception ignored) { return null; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) record Response(List<Item> items) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Item(String title, String originallink, String link, String description, String pubDate) {}
}
