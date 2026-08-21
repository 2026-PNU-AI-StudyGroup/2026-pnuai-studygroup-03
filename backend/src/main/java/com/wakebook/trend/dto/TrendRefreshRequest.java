package com.wakebook.trend.dto;

public record TrendRefreshRequest(Boolean force) {
    public boolean forceOrFalse() { return Boolean.TRUE.equals(force); }
}
