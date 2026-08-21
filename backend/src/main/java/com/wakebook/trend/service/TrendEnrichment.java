package com.wakebook.trend.service;

import com.wakebook.trend.domain.TrendEligibility;
import java.util.List;

public record TrendEnrichment(
    String sourceKey,
    String displayTopic,
    double topicConfidence,
    String contextDescription,
    String retrievalIntent,
    List<List<String>> requiredConceptGroups,
    TrendEligibility eligibility,
    double evidenceConsistencyScore
) {}
