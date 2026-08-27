package com.wakebook.external.trend;

import com.wakebook.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class GoogleTrendsRssClient implements TrendProvider {
    private static final String HT = "https://trends.google.com/trending/rss";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final RestClient restClient;
    private final String defaultRegion;

    public GoogleTrendsRssClient(GoogleTrendsProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.defaultRegion = properties.region();
    }

    @Override
    public List<TrendItem> fetchDailyTrends(String region, int limit) {
        try {
            String xml = restClient.get().uri(uri -> uri.path("/trending/rss")
                .queryParam("geo", region == null ? defaultRegion : region).build())
                .retrieve().body(String.class);
            if (xml == null || xml.isBlank()) throw new IllegalStateException("empty RSS");
            return parse(xml).stream().limit(limit).toList();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TREND_002", "트렌드 후보를 수집하지 못했습니다.");
        }
    }

    List<TrendItem> parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList items = document.getElementsByTagName("item");
        List<TrendItem> result = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String keyword = text(item, "title");
            if (keyword == null || keyword.isBlank()) continue;
            String link = text(item, "link");
            LocalDateTime published = parseRfc822(text(item, "pubDate"));
            List<NewsEvidence> evidence = parseNews(item);
            result.add(new TrendItem(hash(keyword + "|" + text(item, "pubDate")), keyword,
                namespaceText(item, "approx_traffic"), published, link, evidence, i + 1));
        }
        return result;
    }

    private List<NewsEvidence> parseNews(Element item) {
        List<NewsEvidence> evidence = new ArrayList<>();
        NodeList nodes = item.getElementsByTagNameNS(HT, "news_item");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            evidence.add(new NewsEvidence(namespaceText(node, "news_item_title"),
                namespaceText(node, "news_item_snippet"), namespaceText(node, "news_item_url"),
                namespaceText(node, "news_item_source"), null));
        }
        return evidence;
    }

    private static String text(Element element, String tag) {
        NodeList list = element.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent().trim();
    }
    private static String namespaceText(Element element, String localName) {
        NodeList list = element.getElementsByTagNameNS(HT, localName);
        return list.getLength() == 0 ? null : list.item(0).getTextContent().trim();
    }
    private static LocalDateTime parseRfc822(String value) {
        if (value == null || value.isBlank()) return null;
        return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            .withZoneSameInstant(SEOUL).toLocalDateTime();
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
