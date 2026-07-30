package com.wakebook.book.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.dto.HiddenBookUploadResponse;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.book.support.HiddenBookCsvParser;
import com.wakebook.book.support.HiddenBookProperties;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.library.ItemUsageRecord;
import com.wakebook.external.openai.OpenAiClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 사서가 정보나루에서 다운받은 "장서 대출목록" CSV를 업로드하면, 그 도서관의 저이용·고품질
 * "잠자는 도서" 후보군(hidden_books)을 즉시 다시 산출한다. 같은 도서관 코드의 기존 후보군은
 * 전부 지우고 새로 채워 넣는다(도서관별 delete-then-insert).
 */
@Service
public class HiddenBookUploadService {

    private static final int MIN_DESCRIPTION_LENGTH = 30;

    private final HiddenBookCsvParser csvParser;
    private final BookDetailProvider bookDetailProvider;
    private final OpenAiClient openAiClient;
    private final HiddenBookRepository hiddenBookRepository;
    private final HiddenBookProperties hiddenBookProperties;
    private final ObjectMapper objectMapper;

    public HiddenBookUploadService(
        HiddenBookCsvParser csvParser,
        BookDetailProvider bookDetailProvider,
        OpenAiClient openAiClient,
        HiddenBookRepository hiddenBookRepository,
        HiddenBookProperties hiddenBookProperties,
        ObjectMapper objectMapper
    ) {
        this.csvParser = csvParser;
        this.bookDetailProvider = bookDetailProvider;
        this.openAiClient = openAiClient;
        this.hiddenBookRepository = hiddenBookRepository;
        this.hiddenBookProperties = hiddenBookProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HiddenBookUploadResponse upload(String libraryCode, String libraryName, MultipartFile file) {
        validate(libraryCode, libraryName, file);

        List<ItemUsageRecord> candidates = parseAndSortByLoanCount(file);

        List<HiddenBook> refreshed = new ArrayList<>();
        for (ItemUsageRecord candidate : candidates) {
            if (refreshed.size() >= hiddenBookProperties.candidatePoolSize()) {
                break;
            }
            if (candidate.loanCount() > hiddenBookProperties.maxLoanCount()) {
                break;
            }
            bookDetailProvider.fetch(candidate.isbn())
                .filter(this::passesQualityCheck)
                .ifPresent(detail -> refreshed.add(buildHiddenBook(libraryCode, libraryName, candidate, detail)));
        }

        hiddenBookRepository.deleteAllByLibraryCode(libraryCode);
        hiddenBookRepository.saveAll(refreshed);

        return new HiddenBookUploadResponse(libraryCode, libraryName, candidates.size(), refreshed.size());
    }

    private void validate(String libraryCode, String libraryName, MultipartFile file) {
        if (libraryCode == null || libraryCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "libraryCode는 필수입니다.");
        }
        if (libraryName == null || libraryName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "libraryName은 필수입니다.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "file은 필수입니다.");
        }
    }

    private List<ItemUsageRecord> parseAndSortByLoanCount(MultipartFile file) {
        List<ItemUsageRecord> parsed;
        try {
            parsed = csvParser.parse(file.getInputStream());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "CSV 파일을 읽을 수 없습니다.");
        }
        return parsed.stream()
            .sorted(Comparator.comparingLong(ItemUsageRecord::loanCount))
            .toList();
    }

    private boolean passesQualityCheck(BookDetail detail) {
        return isNotBlank(detail.title())
            && isNotBlank(detail.author())
            && isNotBlank(detail.publisher())
            && isNotBlank(detail.description())
            && detail.description().length() >= MIN_DESCRIPTION_LENGTH;
    }

    private HiddenBook buildHiddenBook(String libraryCode, String libraryName, ItemUsageRecord candidate, BookDetail detail) {
        int qualityScore = calculateQualityScore(detail);
        AiReasonAndKeywords generated = generateReasonAndKeywords(detail);

        return new HiddenBook(
            detail.isbn(),
            libraryCode,
            libraryName,
            detail.title(),
            detail.author(),
            detail.cover(),
            candidate.loanCount(),
            qualityScore,
            generated.reason(),
            generated.keywords()
        );
    }

    private int calculateQualityScore(BookDetail detail) {
        int score = 0;
        if (isNotBlank(detail.publisher())) {
            score += 20;
        }
        if (detail.publishedYear() != null) {
            score += 20;
        }
        if (isNotBlank(detail.cover())) {
            score += 20;
        }
        if (isNotBlank(detail.description())) {
            score += Math.min(40, detail.description().length() / 5);
        }
        return Math.min(100, score);
    }

    private AiReasonAndKeywords generateReasonAndKeywords(BookDetail detail) {
        String systemPrompt = """
            당신은 도서관 사서입니다. 저이용 고품질 도서를 독자에게 소개하는 한 문장짜리 추천 이유와
            핵심 키워드 3~5개를 생성합니다. 반드시 다음 JSON 형식으로만 답하세요:
            {"reason": "한 문장 추천 이유", "keywords": ["키워드1", "키워드2", "키워드3"]}
            """;
        String userPrompt = "제목: %s\n설명: %s".formatted(detail.title(), detail.description());

        String content = openAiClient.complete(systemPrompt, userPrompt);
        try {
            return objectMapper.readValue(content, AiReasonAndKeywords.class);
        } catch (Exception e) {
            return new AiReasonAndKeywords(null, List.of());
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record AiReasonAndKeywords(
        @JsonProperty("reason") String reason,
        @JsonProperty("keywords") List<String> keywords
    ) {
    }
}
