package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.book.support.HiddenBookProperties;
import com.wakebook.book.support.KdcKeywords;
import com.wakebook.external.library.BookDetail;
import com.wakebook.external.library.BookDetailProvider;
import com.wakebook.external.library.HoldingCatalogItem;
import com.wakebook.external.library.HoldingCatalogResult;
import com.wakebook.external.library.LibraryHoldingCatalogProvider;
import com.wakebook.external.library.LibraryLoanRankingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 후보군 산출은 후보 도서마다 정보나루 상세 조회가 필요해 수 분이 걸린다. 요청 스레드가 아니라
 * 여기서 비동기로 돌리고 진행 상태만 작업에 기록한다.
 *
 * 추천 이유·키워드를 후보마다 AI로 미리 만들지는 않는다. 대부분은 화면에 노출되지도 않고,
 * 추천 API가 어차피 요청 시점에 이유를 새로 생성하기 때문이다. 대신 정보나루 소개글을 저장해 둔다.
 */
@Service
public class HiddenBookCollector {

    private static final Logger log = LoggerFactory.getLogger(HiddenBookCollector.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MIN_DESCRIPTION_LENGTH = 30;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    // 정보나루 itemSrch는 pageSize를 크게 잡으면 504로 응답한다(실측: 200 실패, 50 정상).
    private static final int CATALOG_PAGE_SIZE = 50;
    private static final int MAX_CATALOG_PAGES = 40;
    private static final int PROGRESS_INTERVAL = 5;

    private final BookDetailProvider bookDetailProvider;
    private final LibraryHoldingCatalogProvider holdingCatalogProvider;
    private final LibraryLoanRankingProvider loanRankingProvider;
    private final HiddenBookPoolWriter poolWriter;
    private final HiddenBookJobService jobService;
    private final HiddenBookProperties hiddenBookProperties;

    public HiddenBookCollector(
        BookDetailProvider bookDetailProvider,
        LibraryHoldingCatalogProvider holdingCatalogProvider,
        LibraryLoanRankingProvider loanRankingProvider,
        HiddenBookPoolWriter poolWriter,
        HiddenBookJobService jobService,
        HiddenBookProperties hiddenBookProperties
    ) {
        this.bookDetailProvider = bookDetailProvider;
        this.holdingCatalogProvider = holdingCatalogProvider;
        this.loanRankingProvider = loanRankingProvider;
        this.poolWriter = poolWriter;
        this.jobService = jobService;
        this.hiddenBookProperties = hiddenBookProperties;
    }

    /** 사서가 올린 CSV의 실제 대출건수로 후보를 고른다. */
    @Async
    public void collectFromCsv(
        Long jobId, String libraryCode, String libraryName, List<HiddenBookCandidate> parsedRows
    ) {
        try {
            List<HiddenBookCandidate> candidates = parsedRows.stream()
                .filter(row -> row.loanCount() <= hiddenBookProperties.maxLoanCount())
                .sorted((a, b) -> Long.compare(a.loanCount(), b.loanCount()))
                .toList();
            jobService.start(jobId, candidates.size());
            int saved = enrichAndReplace(
                jobId, libraryCode, libraryName, HiddenBookSource.CSV_UPLOAD, candidates
            );
            jobService.succeed(jobId, libraryName, saved, buildResultMessage(parsedRows.size(), saved));
        } catch (Exception e) {
            log.error("CSV 후보군 산출 실패 (libraryCode={})", libraryCode, e);
            jobService.fail(jobId, "후보군을 만들지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 정보나루 API만으로 후보를 고른다. 그 도서관의 장서 목록에서 대출 순위 안에 든 책을 빼면
     * 남는 것이 "대출 순위에 들지 못한 장서"다. 정확한 대출건수는 알 수 없는 간접 신호다.
     */
    @Async
    public void collectFromLibraryApi(Long jobId, String libraryCode) {
        try {
            LocalDate endDt = LocalDate.now(SEOUL);
            LocalDate startDt = endDt.minusMonths(hiddenBookProperties.apiPeriodMonths());

            Set<String> rankedIsbns = loanRankingProvider.fetchRankedIsbns(libraryCode, startDt, endDt);
            CatalogScan scan = scanHoldings(libraryCode, startDt, endDt, rankedIsbns);
            if (scan.candidates().isEmpty()) {
                jobService.succeed(jobId, scan.libraryName(), 0,
                    "대출 순위 밖 장서를 찾지 못했습니다. 조회 기간에 등록된 장서가 없을 수 있습니다.");
                return;
            }

            jobService.start(jobId, scan.candidates().size());
            int saved = enrichAndReplace(
                jobId, libraryCode, scan.libraryName(), HiddenBookSource.LIBRARY_API, scan.candidates()
            );
            jobService.succeed(jobId, scan.libraryName(), saved,
                buildResultMessage(scan.candidates().size(), saved));
        } catch (Exception e) {
            log.error("API 후보군 산출 실패 (libraryCode={})", libraryCode, e);
            jobService.fail(jobId, "후보군을 만들지 못했습니다: " + e.getMessage());
        }
    }

    private CatalogScan scanHoldings(
        String libraryCode, LocalDate startDt, LocalDate endDt, Set<String> rankedIsbns
    ) {
        List<HiddenBookCandidate> candidates = new ArrayList<>();
        String libraryName = null;
        int wanted = hiddenBookProperties.candidatePoolSize() * 3;

        for (int pageNo = 1; pageNo <= MAX_CATALOG_PAGES && candidates.size() < wanted; pageNo++) {
            HoldingCatalogResult page =
                holdingCatalogProvider.fetch(libraryCode, startDt, endDt, pageNo, CATALOG_PAGE_SIZE);
            if (libraryName == null) {
                libraryName = page.libraryName();
            }
            if (page.items().isEmpty()) {
                break;
            }
            page.items().stream()
                .filter(item -> !rankedIsbns.contains(item.isbn()))
                .filter(HiddenBookCollector::isShelvedWithCallNumber)
                .map(this::toCandidate)
                .forEach(candidates::add);
            if (page.items().size() < CATALOG_PAGE_SIZE) {
                break;
            }
        }
        return new CatalogScan(libraryName, candidates);
    }

    /**
     * 분류(청구기호)가 없는 장서는 후보에서 뺀다. "몇 번 서가로 가면 있다"를 안내하지 못할 뿐 아니라,
     * itemSrch가 등록일 순이라 최근 입고된 미분류 원서에 후보가 쏠리는 문제도 함께 걸러진다.
     */
    private static boolean isShelvedWithCallNumber(HoldingCatalogItem item) {
        return item.callNumber() != null && !item.callNumber().isBlank()
            && item.className() != null && !item.className().isBlank();
    }

    private HiddenBookCandidate toCandidate(HoldingCatalogItem item) {
        return new HiddenBookCandidate(
            item.isbn(), item.title(), item.author(), item.cover(),
            0, item.className(), item.callNumber(), item.shelfName()
        );
    }

    /**
     * 후보마다 정보나루 상세를 조회해 품질 기준(소개글 존재)을 통과한 것만 남긴다.
     * 저장은 마지막에 한 번만 해서, 실패했을 때 기존 후보군이 지워진 채로 남지 않게 한다.
     */
    private int enrichAndReplace(
        Long jobId,
        String libraryCode,
        String libraryName,
        HiddenBookSource source,
        List<HiddenBookCandidate> candidates
    ) {
        List<HiddenBook> collected = new ArrayList<>();
        int processed = 0;

        for (HiddenBookCandidate candidate : candidates) {
            if (collected.size() >= hiddenBookProperties.candidatePoolSize()) {
                break;
            }
            processed++;
            try {
                bookDetailProvider.fetch(candidate.isbn())
                    .filter(this::passesQualityCheck)
                    .ifPresent(detail ->
                        collected.add(buildHiddenBook(libraryCode, libraryName, source, candidate, detail)));
            } catch (Exception e) {
                // 한 권 조회가 실패해도 나머지 후보로 계속 진행한다.
                log.warn("후보 도서 상세 조회 실패 (isbn={})", candidate.isbn(), e);
            }
            if (processed % PROGRESS_INTERVAL == 0) {
                jobService.progress(jobId, processed, collected.size());
            }
        }

        // 0건으로 교체하면 멀쩡히 쓰던 기존 후보군까지 사라진다. 새로 모은 게 있을 때만 교체한다.
        if (!collected.isEmpty()) {
            poolWriter.replace(libraryCode, collected);
        }
        jobService.progress(jobId, processed, collected.size());
        return collected.size();
    }

    private boolean passesQualityCheck(BookDetail detail) {
        return isNotBlank(detail.title())
            && isNotBlank(detail.author())
            && isNotBlank(detail.publisher())
            && isNotBlank(detail.description())
            && detail.description().length() >= MIN_DESCRIPTION_LENGTH;
    }

    private HiddenBook buildHiddenBook(
        String libraryCode,
        String libraryName,
        HiddenBookSource source,
        HiddenBookCandidate candidate,
        BookDetail detail
    ) {
        return new HiddenBook(
            detail.isbn(),
            libraryCode,
            libraryName,
            detail.title(),
            detail.author(),
            detail.cover() != null ? detail.cover() : candidate.cover(),
            candidate.loanCount(),
            calculateQualityScore(detail),
            null,
            KdcKeywords.from(candidate.className()),
            source,
            candidate.callNumber(),
            candidate.shelfName(),
            truncateDescription(detail.description())
        );
    }

    private String truncateDescription(String description) {
        if (description == null) {
            return null;
        }
        return description.length() <= MAX_DESCRIPTION_LENGTH
            ? description
            : description.substring(0, MAX_DESCRIPTION_LENGTH);
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

    private String buildResultMessage(int candidateCount, int savedCount) {
        if (savedCount > 0) {
            return "후보 %d권을 검토해 %d권을 후보군으로 저장했습니다.".formatted(candidateCount, savedCount);
        }
        return "후보 %d권을 검토했지만 소개글이 있는 도서가 없어 저장된 후보가 없습니다.".formatted(candidateCount);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record CatalogScan(String libraryName, List<HiddenBookCandidate> candidates) {
    }
}
