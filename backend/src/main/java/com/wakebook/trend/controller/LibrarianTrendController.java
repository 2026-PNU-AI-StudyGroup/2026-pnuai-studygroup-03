package com.wakebook.trend.controller;

import com.wakebook.common.response.ApiResponse;
import com.wakebook.trend.dto.*;
import com.wakebook.trend.service.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/librarian/trends")
public class LibrarianTrendController {
    private final DailyTrendQueryService queryService;
    private final TrendRefreshService refreshService;
    public LibrarianTrendController(DailyTrendQueryService queryService, TrendRefreshService refreshService) {
        this.queryService = queryService; this.refreshService = refreshService;
    }
    @GetMapping("/daily")
    public ApiResponse<DailyTrendResponse> daily(@AuthenticationPrincipal Jwt jwt) {
        DailyTrendQueryService.QueryResult result = queryService.librarianDaily(jwt.getSubject());
        return ApiResponse.success("내 도서관의 오늘 트렌드 추천을 조회했습니다.", result.data());
    }
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TrendBatchResponse>> refresh(@AuthenticationPrincipal Jwt jwt,
        @RequestBody(required = false) TrendRefreshRequest request) {
        boolean force = request != null && request.forceOrFalse();
        TrendRefreshService.RefreshResult result = refreshService.request(jwt.getSubject(), force);
        HttpStatus status = result.accepted() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        String message = result.accepted() ? "오늘의 트렌드 추천 생성을 요청했습니다." : "오늘의 트렌드 추천이 이미 생성되어 있습니다.";
        return ResponseEntity.status(status).body(ApiResponse.success(message, result.response()));
    }
    @GetMapping("/batches/{batchId}")
    public ApiResponse<TrendBatchResponse> batch(@AuthenticationPrincipal Jwt jwt, @PathVariable Long batchId) {
        return ApiResponse.success(refreshService.getOwned(jwt.getSubject(), batchId));
    }
}
