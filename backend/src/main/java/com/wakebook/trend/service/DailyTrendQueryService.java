package com.wakebook.trend.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.book.repository.HiddenBookRepository;
import com.wakebook.common.ApiException;
import com.wakebook.common.exception.AuthenticationRequiredException;
import com.wakebook.trend.domain.*;
import com.wakebook.trend.dto.DailyTrendResponse;
import com.wakebook.trend.repository.*;
import com.wakebook.trend.support.TrendProperties;
import com.wakebook.user.domain.User;
import com.wakebook.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class DailyTrendQueryService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final DailyTrendBatchRepository batchRepository;
    private final DailyTrendRecommendationRepository recommendationRepository;
    private final HiddenBookRepository hiddenBookRepository;
    private final UserRepository userRepository;
    private final TrendProperties properties;
    public DailyTrendQueryService(DailyTrendBatchRepository batchRepository,
        DailyTrendRecommendationRepository recommendationRepository, HiddenBookRepository hiddenBookRepository,
        UserRepository userRepository, TrendProperties properties) {
        this.batchRepository = batchRepository; this.recommendationRepository = recommendationRepository;
        this.hiddenBookRepository = hiddenBookRepository; this.userRepository = userRepository; this.properties = properties;
    }

    @Transactional(readOnly = true)
    public QueryResult publicDaily(String libraryCode, LocalDate requestedDate) {
        if (libraryCode == null || libraryCode.isBlank() || libraryCode.length() > 20 || libraryCode.chars().anyMatch(Character::isWhitespace))
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "올바른 도서관 코드를 입력해 주세요.");
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate requested = requestedDate == null ? today : requestedDate;
        if (requested.isAfter(today)) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "미래 날짜는 조회할 수 없습니다.");
        DailyTrendBatch batch = findBatch(libraryCode, requested, requested.equals(today));
        return build(batch, requested);
    }

    @Transactional(readOnly = true)
    public QueryResult librarianDaily(String subject) {
        User user;
        try { user = userRepository.findById(Long.parseLong(subject)).orElseThrow(AuthenticationRequiredException::new); }
        catch (NumberFormatException e) { throw new AuthenticationRequiredException(); }
        if (user.getLibraryCode() == null || user.getLibraryCode().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_001", "사서 계정에 도서관 코드가 필요합니다.");
        return publicDaily(user.getLibraryCode(), LocalDate.now(SEOUL));
    }

    private DailyTrendBatch findBatch(String libraryCode, LocalDate requested, boolean allowFallback) {
        DailyTrendBatch exact = batchRepository.findByRecommendationDateAndLibraryCode(requested, libraryCode)
            .filter(batch -> batch.getCompletedAt() != null).orElse(null);
        if (exact != null && !recommendationRepository.findByBatchIdOrderByDailyTrendFinalTrendScoreDescDisplayOrderAsc(exact.getId()).isEmpty())
            return exact;
        if (allowFallback) return batchRepository
            .findTopByLibraryCodeAndCompletedAtIsNotNullAndRecommendationDateBetweenOrderByRecommendationDateDesc(
                libraryCode, requested.minusDays(properties.fallbackDays()), requested.minusDays(1))
            .filter(batch -> !recommendationRepository.findByBatchIdOrderByDailyTrendFinalTrendScoreDescDisplayOrderAsc(batch.getId()).isEmpty())
            .orElseThrow(DailyTrendQueryService::notFound);
        throw notFound();
    }

    private QueryResult build(DailyTrendBatch batch, LocalDate requested) {
        List<DailyTrendRecommendation> rows = recommendationRepository
            .findByBatchIdOrderByDailyTrendFinalTrendScoreDescDisplayOrderAsc(batch.getId());
        LinkedHashMap<Long, List<DailyTrendRecommendation>> grouped = new LinkedHashMap<>();
        for (DailyTrendRecommendation row : rows) grouped.computeIfAbsent(row.getDailyTrend().getId(), key -> new ArrayList<>()).add(row);
        List<DailyTrendResponse.Item> items = new ArrayList<>();
        int rank = 0;
        for (List<DailyTrendRecommendation> group : grouped.values()) {
            DailyTrend trend = group.getFirst().getDailyTrend();
            List<DailyTrendResponse.Book> books = group.stream().map(row -> new DailyTrendResponse.Book(
                row.getId(), row.getIsbn(), row.getBookTitle(), row.getBookAuthor(), row.getBookCover(), row.getLoanCount(), row.getReason())).toList();
            items.add(new DailyTrendResponse.Item(trend.getId(), trend.getSourceKeyword(), trend.getDisplayTopic(), ++rank,
                trend.getGoogleTrafficLabel(), offset(trend.getStartedAt()), trend.getTopicConfidence(), trend.getValidationStatus(),
                trend.getContextDescription(), group.getFirst().getRecommendationTitle(), books));
        }
        DailyTrend first = rows.getFirst().getDailyTrend();
        List<DailyTrendResponse.Source> sources = sourceResponses(first);
        String libraryName = hiddenBookRepository.findTopByLibraryCode(batch.getLibraryCode()).map(HiddenBook::getLibraryName).orElse(null);
        DailyTrendResponse data = new DailyTrendResponse(requested, batch.getRecommendationDate(), batch.getLibraryCode(), libraryName,
            requested.equals(batch.getRecommendationDate()) ? "CURRENT" : "FALLBACK", offset(batch.getCompletedAt()), sources, items);
        String message = requested.equals(batch.getRecommendationDate()) ? "오늘의 트렌드 연계 추천을 조회했습니다."
            : "가장 최근의 트렌드 연계 추천을 조회했습니다.";
        return new QueryResult(data, message, "trend-" + batch.getId() + "-" + batch.getUpdatedAt().hashCode());
    }

    private List<DailyTrendResponse.Source> sourceResponses(DailyTrend trend) {
        List<DailyTrendResponse.Source> sources = new ArrayList<>();
        sources.add(new DailyTrendResponse.Source("GOOGLE_TRENDS", "Google Trends", "DISCOVERY", "KR",
            offset(trend.getFetchedAt()), "https://trends.google.com/trending?geo=KR"));
        if (trend.getNewsEnrichedAt() != null) sources.add(new DailyTrendResponse.Source("NAVER_NEWS", "NAVER 뉴스 검색", "EVIDENCE", "KR",
            offset(trend.getNewsEnrichedAt()), "https://search.naver.com/search.naver?where=news"));
        if (trend.getValidatedAt() != null) sources.add(new DailyTrendResponse.Source("NAVER_DATALAB", "NAVER 데이터랩", "VALIDATION", "KR",
            offset(trend.getValidatedAt()), "https://datalab.naver.com/keyword/trendSearch.naver"));
        return sources;
    }
    private static OffsetDateTime offset(LocalDateTime value) { return value == null ? null : value.atZone(SEOUL).toOffsetDateTime(); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "TREND_001", "조회할 수 있는 트렌드 추천이 없습니다."); }
    public record QueryResult(DailyTrendResponse data, String message, String etag) {}
}
