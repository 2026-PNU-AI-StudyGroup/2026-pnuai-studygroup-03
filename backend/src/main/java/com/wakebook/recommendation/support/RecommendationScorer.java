package com.wakebook.recommendation.support;

/**
 * docs/README.md §2.2 최종 추천 점수 공식을 그대로 구현한다:
 * 키워드 관련성×0.35 + 독서 목적 일치도×0.20 + 분위기 일치도×0.15 + 독서 시간 일치도×0.10
 * + 도서 정보 품질×0.10 + 잠자는 도서 발견 가치×0.10
 */
public final class RecommendationScorer {

    private RecommendationScorer() {
    }

    public static int discoveryValue(long loanCount, long minLoanCount, long maxLoanCount) {
        if (maxLoanCount == minLoanCount) {
            return 100;
        }
        double ratio = (double) (maxLoanCount - loanCount) / (maxLoanCount - minLoanCount);
        return (int) Math.round(100 * ratio);
    }

    public static int finalScore(
        int keywordRelevance,
        int purposeMatch,
        int moodMatch,
        int timeMatch,
        int bookInfoQuality,
        int discoveryValue
    ) {
        double score = keywordRelevance * 0.35
            + purposeMatch * 0.20
            + moodMatch * 0.15
            + timeMatch * 0.10
            + bookInfoQuality * 0.10
            + discoveryValue * 0.10;
        return (int) Math.round(score);
    }
}
