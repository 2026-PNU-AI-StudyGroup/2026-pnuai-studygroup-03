package com.wakebook.recommendation.controller;

import com.wakebook.common.response.ApiResponse;
import com.wakebook.recommendation.dto.CompareRequest;
import com.wakebook.recommendation.dto.CompareResponse;
import com.wakebook.recommendation.dto.ExploreRequest;
import com.wakebook.recommendation.dto.ExploreResponse;
import com.wakebook.recommendation.dto.RecommendationRequest;
import com.wakebook.recommendation.dto.RecommendationResponse;
import com.wakebook.recommendation.service.BookComparisonService;
import com.wakebook.recommendation.service.RecommendationExploreService;
import com.wakebook.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final BookComparisonService bookComparisonService;
    private final RecommendationExploreService recommendationExploreService;

    public RecommendationController(
        RecommendationService recommendationService,
        BookComparisonService bookComparisonService,
        RecommendationExploreService recommendationExploreService
    ) {
        this.recommendationService = recommendationService;
        this.bookComparisonService = bookComparisonService;
        this.recommendationExploreService = recommendationExploreService;
    }

    @PostMapping
    public ApiResponse<List<RecommendationResponse>> recommend(@Valid @RequestBody RecommendationRequest request) {
        return ApiResponse.success(recommendationService.recommend(request));
    }

    @PostMapping("/compare")
    public ApiResponse<CompareResponse> compare(@Valid @RequestBody CompareRequest request) {
        return ApiResponse.success(bookComparisonService.compare(request));
    }

    @PostMapping("/explore")
    public ApiResponse<List<ExploreResponse>> explore(@Valid @RequestBody ExploreRequest request) {
        return ApiResponse.success(recommendationExploreService.explore(request));
    }
}
