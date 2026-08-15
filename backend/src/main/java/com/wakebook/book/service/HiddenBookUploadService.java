package com.wakebook.book.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import com.wakebook.book.domain.BookEnrichmentCache;
import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.dto.HiddenBookUploadResponse;
import com.wakebook.book.repository.BookEnrichmentCacheRepository;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.book.support.HiddenBookCsvParser;
import com.wakebook.book.support.HiddenBookProperties;
import com.wakebook.common.ApiException;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.HiddenBookDetailProvider;
import com.wakebook.external.library.ItemUsageRecord;
import com.wakebook.external.openai.OpenAiClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 사서가 정보나루에서 다운받은 "장서 대출목록" CSV를 업로드하면, 그 도서관의 저이용·고품질
 * "잠자는 도서" 후보군(hidden_books)을 즉시 다시 산출한다. 같은 도서관 코드의 기존 후보군은
 * 전부 지우고 새로 채워 넣는다(도서관별 delete-then-insert).
 *
 * 외부 API 호출을 줄이기 위해 세 단계 최적화를 적용한다:
 * 1) CSV에 이미 있는 필드(제목/저자/출판사/주제분류번호)로 먼저 걸러내, 표지·소개 조회를
 *    할 가치가 없는 후보에 API를 낭비하지 않는다. 주제분류번호(KDC)가 없는 후보는 2)의
 *    장르 다양성 로직에 기여할 수 없으므로 이 단계에서 함께 제외한다.
 * 2) CSV의 주제분류번호(KDC 대분류)마다 {@code candidatePoolSize}를 균등 배분한 목표치(quota)를
 *    정해두고, 카테고리별로 그 목표치를 채울 때까지만 시도한다. 대출 0건 구간이 특정 장르(예:
 *    외국어 참고서)로 쏠려 있어도 후보군이 그 장르로만 채워지지 않는 것은 물론, 다른 카테고리의
 *    조회 실패가 이 카테고리의 몫을 갉아먹지 않아 카테고리당 최종 권수가 고르게 유지된다(이전
 *    라운드로빈 방식은 "전체 성공 개수"만 보고 멈춰서, 실패가 몰린 카테고리만 적게 나오는 문제가
 *    있었다). 대출건수 오름차순 정렬 전에 CSV 행 순서를 섞어두므로(같은 대출건수 안에서만
 *    무작위) 같은 도서관의 CSV를 매달 다시 올려도 매번 같은 60권이 반복되지 않는다.
 * 3) 품질검증을 통과한 책들의 추천이유/키워드 생성을 책 한 권당 1번이 아니라, 여러 권을
 *    묶어 OpenAI를 배치로 호출한다. 이미 다른 도서관에서 같은 ISBN을 캐싱해둔 책은
 *    표지·소개 조회·AI 호출을 아예 건너뛰고 캐시를 재사용한다.
 *
 * 표지·소개 문구는 {@link HiddenBookDetailProvider}(카카오 책 검색, {@code target=isbn}으로
 * 정확 조회)로 얻는다. 도서관 소장 여부·대출건수는 이미 CSV에서 확보돼 있어 정보나루 ISBN
 * 조회가 필요 없고, 정보나루 authKey는 도서 검색/상세/추천 등 다른 기능과 하루 호출 한도를
 * 공유하므로 후보 1권당 1회씩 나가는 이 조회를 정보나루가 아닌 카카오로 분리했다(KAKAO_API_KEY 필요).
 */
@Service
public class HiddenBookUploadService {

    private static final int MIN_DESCRIPTION_LENGTH = 30;
    private static final int AI_BATCH_SIZE = 15;
    private static final int MAX_ATTEMPTS_PER_CATEGORY_MULTIPLIER = 5;

    private final Random random = new Random();
    private final HiddenBookCsvParser csvParser;
    private final HiddenBookDetailProvider bookDetailProvider;
    private final OpenAiClient openAiClient;
    private final HiddenBookRepository hiddenBookRepository;
    private final BookEnrichmentCacheRepository bookEnrichmentCacheRepository;
    private final HiddenBookProperties hiddenBookProperties;
    private final ObjectMapper objectMapper;

    public HiddenBookUploadService(
        HiddenBookCsvParser csvParser,
        HiddenBookDetailProvider bookDetailProvider,
        OpenAiClient openAiClient,
        HiddenBookRepository hiddenBookRepository,
        BookEnrichmentCacheRepository bookEnrichmentCacheRepository,
        HiddenBookProperties hiddenBookProperties,
        ObjectMapper objectMapper
    ) {
        this.csvParser = csvParser;
        this.bookDetailProvider = bookDetailProvider;
        this.openAiClient = openAiClient;
        this.hiddenBookRepository = hiddenBookRepository;
        this.bookEnrichmentCacheRepository = bookEnrichmentCacheRepository;
        this.hiddenBookProperties = hiddenBookProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HiddenBookUploadResponse upload(String libraryCode, String libraryName, MultipartFile file) {
        validate(libraryCode, libraryName, file);

        List<ItemUsageRecord> candidates = parseAndSortByLoanCount(file);
        List<ItemUsageRecord> prefiltered = dedupeByIsbn(candidates).stream()
            .filter(candidate -> candidate.loanCount() <= hiddenBookProperties.maxLoanCount())
            .filter(this::hasBasicMetadata)
            .filter(this::hasKdcCategory)
            .toList();

        Map<String, List<ItemUsageRecord>> byCategory = groupByKdcCategory(prefiltered);
        int poolSize = hiddenBookProperties.candidatePoolSize();
        Map<String, Integer> quotaByCategory = distributeQuota(byCategory.keySet(), poolSize);
        Map<String, List<ItemUsageRecord>> attemptWindowByCategory = buildAttemptWindows(byCategory, quotaByCategory);

        List<ItemUsageRecord> attemptPool = attemptWindowByCategory.values().stream()
            .flatMap(List::stream)
            .toList();
        Map<String, BookEnrichmentCache> cacheByIsbn = loadCache(attemptPool);

        List<HiddenBook> refreshed = new ArrayList<>();
        List<PendingEnrichment> pending = new ArrayList<>();

        for (Map.Entry<String, List<ItemUsageRecord>> entry : attemptWindowByCategory.entrySet()) {
            int quota = quotaByCategory.getOrDefault(entry.getKey(), 0);
            int successCount = 0;
            for (ItemUsageRecord candidate : entry.getValue()) {
                if (successCount >= quota) {
                    break;
                }
                BookEnrichmentCache cached = cacheByIsbn.get(candidate.isbn());
                if (cached != null) {
                    refreshed.add(buildHiddenBookFromCache(libraryCode, libraryName, candidate, cached));
                    successCount++;
                    continue;
                }
                boolean succeeded = bookDetailProvider.fetch(candidate.isbn())
                    .filter(this::passesQualityCheck)
                    .map(detail -> pending.add(new PendingEnrichment(candidate, detail)))
                    .orElse(false);
                if (succeeded) {
                    successCount++;
                }
            }
        }

        refreshed.addAll(generateInBatches(libraryCode, libraryName, pending));

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

    /**
     * 대출건수 오름차순으로 정렬하되, 정렬 전에 CSV 행 순서를 섞어둔다({@code sorted}는 안정 정렬이라
     * 대출건수가 같은 행끼리는 섞인 순서가 그대로 유지됨). 저이용 구간은 대부분 대출건수가 0~2로
     * 동률이라, 섞지 않으면 같은 도서관의 CSV를 매달 다시 올려도 앞쪽 후보가 거의 그대로 반복된다.
     */
    private List<ItemUsageRecord> parseAndSortByLoanCount(MultipartFile file) {
        List<ItemUsageRecord> parsed;
        try {
            parsed = new ArrayList<>(csvParser.parse(file.getInputStream()));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "CSV 파일을 읽을 수 없습니다.");
        }
        Collections.shuffle(parsed, random);
        return parsed.stream()
            .sorted(Comparator.comparingLong(ItemUsageRecord::loanCount))
            .toList();
    }

    /**
     * 도서관이 같은 책을 여러 권 소장하면 CSV에 같은 ISBN이 여러 행으로 나온다(실측: 부산 금정도서관
     * 실제 CSV 기준 고유 ISBN 25만여 개 중 3만여 개가 중복). 중복을 그대로 두면 같은 ISBN이
     * 후보군에 두 번 들어가 {@code hidden_books}의 (libraryCode, isbn) 유니크 제약을 위반해 저장이
     * 통째로 실패한다. loanCount 오름차순 정렬 뒤에 dedupe하므로 대출건수가 가장 낮은 행이 남는다.
     */
    private List<ItemUsageRecord> dedupeByIsbn(List<ItemUsageRecord> candidates) {
        Map<String, ItemUsageRecord> byIsbn = new LinkedHashMap<>();
        for (ItemUsageRecord candidate : candidates) {
            byIsbn.putIfAbsent(candidate.isbn(), candidate);
        }
        return new ArrayList<>(byIsbn.values());
    }

    private boolean hasBasicMetadata(ItemUsageRecord candidate) {
        return isNotBlank(candidate.title()) && isNotBlank(candidate.author()) && isNotBlank(candidate.publisher());
    }

    /**
     * 주제분류번호(KDC)가 없는 후보는 카테고리별 목표치 배분에 아무 기여를 못 하므로
     * 아예 후보군에서 제외한다. 대부분의 책이 KDC를 갖고 있어(실측 확인됨) 손실은 적다.
     */
    private boolean hasKdcCategory(ItemUsageRecord candidate) {
        return !ItemUsageRecord.UNKNOWN_KDC_CATEGORY.equals(candidate.kdcCategory());
    }

    private Map<String, List<ItemUsageRecord>> groupByKdcCategory(List<ItemUsageRecord> prefiltered) {
        return prefiltered.stream()
            .collect(Collectors.groupingBy(
                ItemUsageRecord::kdcCategory, TreeMap::new, Collectors.toCollection(ArrayList::new)));
    }

    /**
     * candidatePoolSize를 카테고리 수만큼 균등하게 나눈다. 나누어떨어지지 않는 나머지는
     * 앞쪽 카테고리(KDC 번호가 작은 쪽)부터 1권씩 더 배정한다.
     */
    private Map<String, Integer> distributeQuota(Set<String> categories, int poolSize) {
        if (categories.isEmpty()) {
            return Map.of();
        }
        List<String> ordered = new ArrayList<>(categories);
        int base = poolSize / ordered.size();
        int remainder = poolSize % ordered.size();

        Map<String, Integer> quota = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            quota.put(ordered.get(i), base + (i < remainder ? 1 : 0));
        }
        return quota;
    }

    /**
     * 카테고리별로 목표치(quota)의 {@link #MAX_ATTEMPTS_PER_CATEGORY_MULTIPLIER}배까지만 시도
     * 대상으로 삼는다. 카카오 매칭/품질검증 실패율이 유난히 높은 카테고리를 만나도 그 카테고리
     * 하나 때문에 후보군 전체를 무한정 훑지 않도록 하는 안전장치다.
     */
    private Map<String, List<ItemUsageRecord>> buildAttemptWindows(
        Map<String, List<ItemUsageRecord>> byCategory, Map<String, Integer> quotaByCategory
    ) {
        Map<String, List<ItemUsageRecord>> windows = new LinkedHashMap<>();
        for (Map.Entry<String, List<ItemUsageRecord>> entry : byCategory.entrySet()) {
            int quota = quotaByCategory.getOrDefault(entry.getKey(), 0);
            int windowSize = Math.min(entry.getValue().size(), quota * MAX_ATTEMPTS_PER_CATEGORY_MULTIPLIER);
            windows.put(entry.getKey(), entry.getValue().subList(0, windowSize));
        }
        return windows;
    }

    private Map<String, BookEnrichmentCache> loadCache(List<ItemUsageRecord> candidates) {
        List<String> isbns = candidates.stream().map(ItemUsageRecord::isbn).toList();
        return bookEnrichmentCacheRepository.findAllByIsbnIn(isbns).stream()
            .collect(Collectors.toMap(BookEnrichmentCache::getIsbn, cache -> cache));
    }

    private HiddenBook buildHiddenBookFromCache(
        String libraryCode, String libraryName, ItemUsageRecord candidate, BookEnrichmentCache cached
    ) {
        return new HiddenBook(
            cached.getIsbn(), libraryCode, libraryName,
            cached.getTitle(), cached.getAuthor(), cached.getCover(),
            candidate.loanCount(), cached.getQualityScore(), cached.getReason(), cached.getKeywords()
        );
    }

    private boolean passesQualityCheck(BookDetail detail) {
        return isNotBlank(detail.title())
            && isNotBlank(detail.author())
            && isNotBlank(detail.publisher())
            && isNotBlank(detail.description())
            && detail.description().length() >= MIN_DESCRIPTION_LENGTH;
    }

    private List<HiddenBook> generateInBatches(String libraryCode, String libraryName, List<PendingEnrichment> pending) {
        List<HiddenBook> result = new ArrayList<>();
        for (int start = 0; start < pending.size(); start += AI_BATCH_SIZE) {
            List<PendingEnrichment> chunk = pending.subList(start, Math.min(start + AI_BATCH_SIZE, pending.size()));
            Map<String, AiReasonAndKeywords> byIsbn = generateReasonAndKeywordsBatch(chunk);

            for (PendingEnrichment item : chunk) {
                String isbn = item.detail().isbn();
                AiReasonAndKeywords generated = byIsbn.getOrDefault(isbn, new AiReasonAndKeywords(null, List.of()));
                int qualityScore = calculateQualityScore(item.detail());

                result.add(new HiddenBook(
                    isbn, libraryCode, libraryName,
                    item.detail().title(), item.detail().author(), item.detail().cover(),
                    item.candidate().loanCount(), qualityScore, generated.reason(), generated.keywords()
                ));
                saveToCache(item.detail(), qualityScore, generated);
            }
        }
        return result;
    }

    private void saveToCache(BookDetail detail, int qualityScore, AiReasonAndKeywords generated) {
        List<String> keywords = generated.keywords() != null ? generated.keywords() : List.of();
        bookEnrichmentCacheRepository.save(new BookEnrichmentCache(
            detail.isbn(), detail.title(), detail.author(), detail.cover(),
            qualityScore, generated.reason(), keywords
        ));
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

    private Map<String, AiReasonAndKeywords> generateReasonAndKeywordsBatch(List<PendingEnrichment> chunk) {
        String systemPrompt = buildBatchSystemPrompt();
        String userPrompt = buildBatchUserPrompt(chunk);
        String content = openAiClient.complete(systemPrompt, userPrompt);
        try {
            AiReasonAndKeywordsListPayload payload = objectMapper.readValue(content, AiReasonAndKeywordsListPayload.class);
            return payload.results().stream()
                .filter(result -> result.isbn() != null)
                .collect(Collectors.toMap(
                    AiReasonAndKeywordsPayload::isbn,
                    result -> new AiReasonAndKeywords(result.reason(), result.keywords()),
                    (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String buildBatchSystemPrompt() {
        return """
            당신은 도서관 사서입니다. 저이용 고품질 도서 여러 권이 주어지면, 각 책마다 독자에게
            소개하는 한 문장짜리 추천 이유(reason)와 핵심 키워드 3~5개(keywords)를 생성합니다.
            반드시 다음 JSON 형식으로, 입력받은 책 전부에 대해 답하세요:
            {"results": [{"isbn": "...", "reason": "한 문장 추천 이유", "keywords": ["키워드1", "키워드2"]}]}
            """;
    }

    private String buildBatchUserPrompt(List<PendingEnrichment> chunk) {
        StringBuilder builder = new StringBuilder();
        builder.append("아래 도서 각각에 대해 추천 이유와 키워드를 생성하세요:\n");
        for (PendingEnrichment item : chunk) {
            builder.append("- isbn: ").append(item.detail().isbn())
                .append(", 제목: ").append(item.detail().title())
                .append(", 설명: ").append(item.detail().description())
                .append('\n');
        }
        return builder.toString();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record PendingEnrichment(ItemUsageRecord candidate, BookDetail detail) {
    }

    private record AiReasonAndKeywords(String reason, List<String> keywords) {
    }

    private record AiReasonAndKeywordsPayload(
        @JsonProperty("isbn") String isbn,
        @JsonProperty("reason") String reason,
        @JsonProperty("keywords") List<String> keywords
    ) {
    }

    private record AiReasonAndKeywordsListPayload(@JsonProperty("results") List<AiReasonAndKeywordsPayload> results) {
    }
}
