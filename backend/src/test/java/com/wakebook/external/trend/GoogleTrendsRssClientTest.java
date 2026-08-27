package com.wakebook.external.trend;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTrendsRssClientTest {
    @Test
    void parsesGoogleNamespaceAndNewsEvidence() throws Exception {
        GoogleTrendsRssClient client = new GoogleTrendsRssClient(
            new GoogleTrendsProperties("https://trends.google.com", "KR"));
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:ht="https://trends.google.com/trending/rss" version="2.0"><channel><item>
              <title>환율 급등</title><ht:approx_traffic>10K+</ht:approx_traffic>
              <link>https://trends.google.com/trending?geo=KR</link>
              <pubDate>Tue, 18 Aug 2026 01:00:00 +0000</pubDate>
              <ht:news_item><ht:news_item_title>원·달러 환율 변동 확대</ht:news_item_title>
                <ht:news_item_snippet>시장 변동성이 커졌다.</ht:news_item_snippet>
                <ht:news_item_url>https://news.example/article</ht:news_item_url>
                <ht:news_item_source>테스트뉴스</ht:news_item_source></ht:news_item>
            </item></channel></rss>
            """;

        List<TrendItem> result = client.parse(xml);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().keyword()).isEqualTo("환율 급등");
        assertThat(result.getFirst().trafficLabel()).isEqualTo("10K+");
        assertThat(result.getFirst().evidence()).singleElement()
            .satisfies(evidence -> {
                assertThat(evidence.title()).isEqualTo("원·달러 환율 변동 확대");
                assertThat(evidence.url()).isEqualTo("https://news.example/article");
            });
    }
}
