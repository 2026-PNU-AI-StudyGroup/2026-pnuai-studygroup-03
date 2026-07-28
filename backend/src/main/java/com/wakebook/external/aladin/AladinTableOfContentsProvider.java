package com.wakebook.external.aladin;

import com.wakebook.external.aladin.dto.AladinItemLookupApiResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

@Component
public class AladinTableOfContentsProvider implements TableOfContentsProvider {

    private final RestClient restClient;
    private final String ttbKey;

    public AladinTableOfContentsProvider(AladinProperties properties) {
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
        this.ttbKey = properties.ttbKey();
    }

    /**
     * 목차는 부가 정보라 조회에 실패해도 도서 상세 전체를 실패시키지 않고 빈 목록으로 대체한다.
     */
    @Override
    public List<String> fetch(String isbn) {
        AladinItemLookupApiResponse response;
        try {
            response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/ItemLookUp.aspx")
                    .queryParam("ttbkey", ttbKey)
                    .queryParam("itemIdType", "ISBN13")
                    .queryParam("ItemId", isbn)
                    .queryParam("output", "js")
                    .queryParam("Version", "20131101")
                    .queryParam("OptResult", "Toc")
                    .build())
                .retrieve()
                .body(AladinItemLookupApiResponse.class);
        } catch (RestClientException e) {
            return List.of();
        }

        if (response == null || response.item() == null || response.item().isEmpty()) {
            return List.of();
        }

        AladinItemLookupApiResponse.SubInfo subInfo = response.item().get(0).subInfo();
        return subInfo == null ? List.of() : parseToc(subInfo.toc());
    }

    private List<String> parseToc(String toc) {
        if (toc == null || toc.isBlank()) {
            return List.of();
        }
        return Arrays.stream(toc.split("<br\\s*/?>|\\r?\\n"))
            .map(line -> line.replaceAll("<[^>]+>", "").trim())
            .filter(line -> !line.isBlank())
            .toList();
    }
}
