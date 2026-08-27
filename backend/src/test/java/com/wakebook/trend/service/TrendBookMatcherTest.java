package com.wakebook.trend.service;

import com.wakebook.book.domain.HiddenBook;
import com.wakebook.trend.domain.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendBookMatcherTest {
    private final TrendBookMatcher matcher = new TrendBookMatcher(new ObjectMapper());

    @Test
    void 항공과_AI_중_한쪽만_관련된_책은_제외한다() {
        DailyTrend trend = trend();
        HiddenBook generalAi = book("1", "처음 배우는 인공지능", "AI와 머신러닝을 쉽게 설명한다.", List.of("인공지능"));
        HiddenBook generalAviation = book("2", "항공 운항 입문", "비행과 공항 관제의 기초를 설명한다.", List.of("항공"));
        HiddenBook aviationAi = book("3", "자율비행과 항공 AI", "인공지능을 활용한 자율비행과 공항 자동화를 다룬다.",
            List.of("항공", "인공지능"));

        List<TrendBookMatcher.BookMatch> result = matcher.shortlist(trend,
            List.of(generalAi, generalAviation, aviationAi), 15);

        assertThat(result).extracting(match -> match.book().getIsbn()).containsExactly("3");
    }

    @Test
    void 개념군을_전부_만족하는_책이_없으면_일부만_맞는_책으로_완화한다() {
        DailyTrend trend = trend();
        HiddenBook generalAi = book("1", "처음 배우는 인공지능", "AI와 머신러닝을 쉽게 설명한다.", List.of("인공지능"));
        HiddenBook generalAviation = book("2", "항공 운항 입문", "비행과 공항 관제의 기초를 설명한다.", List.of("항공"));
        HiddenBook unrelated = book("4", "오늘의 요리", "집에서 쉽게 만드는 반찬 모음.", List.of("요리"));

        // "항공"과 "AI"를 둘 다 만족하는 책이 아예 없는 상황 — 완전 매칭(fullMatches)이 비어 있으므로
        // 한쪽만 맞는 책들로 완화되어야 한다. 아예 무관한 책(unrelated)은 여전히 제외돼야 한다.
        List<TrendBookMatcher.BookMatch> result = matcher.shortlist(trend,
            List.of(generalAi, generalAviation, unrelated), 15);

        assertThat(result).extracting(match -> match.book().getIsbn())
            .containsExactlyInAnyOrder("1", "2");
    }

    private DailyTrend trend() {
        return new DailyTrend(LocalDate.now(), "GOOGLE_TRENDS", "key", "항공 AI", "항공ai",
            "항공 분야 AI 기술 도입", .9, 1, "10K+", LocalDateTime.now(), null, "[]", "[]",
            "항공 운항과 공항 운영에 인공지능을 적용하는 움직임입니다.",
            "항공 운항·관제·공항 운영에 적용되는 인공지능과 자동화 기술",
            "[[\"항공\",\"비행\",\"공항\",\"관제\"],[\"AI\",\"인공지능\",\"머신러닝\",\"자동화\"]]",
            TrendEligibility.ELIGIBLE, .9, TrendValidationStatus.CONFIRMED, 1.5, .9,
            LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }

    private HiddenBook book(String isbn, String title, String description, List<String> keywords) {
        return new HiddenBook(isbn, "121018", "테스트도서관", title, "저자", null,
            0, 90, null, keywords, com.wakebook.book.domain.HiddenBookSource.CSV_UPLOAD,
            null, null, description, "5");
    }
}
