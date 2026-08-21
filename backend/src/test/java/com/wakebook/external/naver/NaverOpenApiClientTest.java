package com.wakebook.external.naver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wakebook.trend.domain.TrendValidationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NaverOpenApiClientTest {
    private HttpServer server;
    private NaverApiProperties properties;
    private final AtomicReference<HttpExchange> newsRequest = new AtomicReference<>();
    private final AtomicReference<HttpExchange> dataLabRequest = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/search/news.json", exchange -> {
            newsRequest.set(exchange);
            respond(exchange, """
                {"items":[{"title":"인공지능 뉴스","originallink":"https://example.com/news",
                "link":"https://news.naver.com/example","description":"생성형 인공지능 소식","pubDate":"Fri, 21 Aug 2026 12:00:00 +0900"}]}
                """);
        });
        server.createContext("/v1/datalab/search", exchange -> {
            dataLabRequest.set(exchange);
            respond(exchange, """
                {"results":[{"data":[
                {"period":"2026-08-09","ratio":30},{"period":"2026-08-10","ratio":30},
                {"period":"2026-08-11","ratio":30},{"period":"2026-08-12","ratio":30},
                {"period":"2026-08-13","ratio":30},{"period":"2026-08-14","ratio":30},
                {"period":"2026-08-15","ratio":30},{"period":"2026-08-16","ratio":30},
                {"period":"2026-08-17","ratio":30},{"period":"2026-08-18","ratio":30},
                {"period":"2026-08-19","ratio":30},{"period":"2026-08-20","ratio":30},
                {"period":"2026-08-21","ratio":60},{"period":"2026-08-22","ratio":60}
                ]}]}
                """);
        });
        server.start();
        properties = new NaverApiProperties("http://localhost:" + server.getAddress().getPort(), "client-id", "client-secret");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void usesNaverDevelopersNewsEndpointAndHeaders() {
        var result = new NaverNewsSearchClient(properties).search("인공지능", 3);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("인공지능 뉴스");
        assertThat(newsRequest.get().getRequestMethod()).isEqualTo("GET");
        assertThat(newsRequest.get().getRequestURI().getPath()).isEqualTo("/v1/search/news.json");
        assertThat(newsRequest.get().getRequestHeaders().getFirst("X-Naver-Client-Id")).isEqualTo("client-id");
        assertThat(newsRequest.get().getRequestHeaders().getFirst("X-Naver-Client-Secret")).isEqualTo("client-secret");
    }

    @Test
    void usesNaverDevelopersDataLabEndpointAndHeaders() {
        var result = new NaverSearchTrendClient(properties).validate("인공지능", "생성형 인공지능");

        assertThat(result.status()).isEqualTo(TrendValidationStatus.CONFIRMED);
        assertThat(result.spikeScore()).isGreaterThan(1.15);
        assertThat(dataLabRequest.get().getRequestMethod()).isEqualTo("POST");
        assertThat(dataLabRequest.get().getRequestURI().getPath()).isEqualTo("/v1/datalab/search");
        assertThat(dataLabRequest.get().getRequestHeaders().getFirst("X-Naver-Client-Id")).isEqualTo("client-id");
        assertThat(dataLabRequest.get().getRequestHeaders().getFirst("X-Naver-Client-Secret")).isEqualTo("client-secret");
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
