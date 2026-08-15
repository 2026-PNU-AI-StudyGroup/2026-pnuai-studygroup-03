package com.wakebook.book.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.external.openai.OpenAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 추천 이유는 후보군을 만들 때가 아니라 실제로 화면에 필요할 때 만들고 저장한다.
 * 후보 도서 전부에 대해 미리 생성하면 산출이 몇 배로 느려지는데, 그중 노출되는 건 극히 일부다.
 * 생성에 실패해도 도서 자체는 보여 줘야 하므로 예외를 밖으로 던지지 않는다.
 */
@Service
public class HiddenBookReasonService {

    private static final Logger log = LoggerFactory.getLogger(HiddenBookReasonService.class);

    private static final String SYSTEM_PROMPT = """
        당신은 도서관 사서입니다. 대출이 적었지만 소개할 만한 도서를 독자에게 권하는
        한 문장짜리 추천 이유와 핵심 키워드 3~5개를 만듭니다. 반드시 다음 JSON 형식으로만 답하세요:
        {"reason": "한 문장 추천 이유", "keywords": ["키워드1", "키워드2", "키워드3"]}
        """;

    private final OpenAiClient openAiClient;
    private final HiddenBookRepository hiddenBookRepository;
    private final ObjectMapper objectMapper;

    public HiddenBookReasonService(
        OpenAiClient openAiClient, HiddenBookRepository hiddenBookRepository, ObjectMapper objectMapper
    ) {
        this.openAiClient = openAiClient;
        this.hiddenBookRepository = hiddenBookRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 호출부가 트랜잭션 밖에서 조회한 엔티티를 넘기므로 변경 감지에 기대지 않고 직접 저장한다.
     * 저장하지 않으면 같은 책을 볼 때마다 AI를 다시 호출하게 된다.
     */
    @Transactional
    public HiddenBook ensureReason(HiddenBook book) {
        if (book.hasReason()) {
            return book;
        }
        try {
            String userPrompt = "제목: %s\n설명: %s".formatted(book.getTitle(), summary(book));
            AiReasonPayload payload =
                objectMapper.readValue(openAiClient.complete(SYSTEM_PROMPT, userPrompt), AiReasonPayload.class);
            book.applyGeneratedReason(payload.reason(), payload.keywords());
            return hiddenBookRepository.save(book);
        } catch (Exception e) {
            log.warn("추천 이유 생성 실패 (isbn={})", book.getIsbn(), e);
            return book;
        }
    }

    private String summary(HiddenBook book) {
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            return book.getDescription();
        }
        return String.join(", ", book.getKeywords());
    }

    private record AiReasonPayload(
        @JsonProperty("reason") String reason,
        @JsonProperty("keywords") List<String> keywords
    ) {
    }
}
