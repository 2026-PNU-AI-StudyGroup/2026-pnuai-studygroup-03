package com.wakebook.external.trend;

import java.time.LocalDateTime;

public record NewsEvidence(String title, String summary, String url, String source, LocalDateTime publishedAt) {}
