package com.wakebook.external.trend;

import java.util.List;

public interface TrendProvider {
    List<TrendItem> fetchDailyTrends(String region, int limit);
}
