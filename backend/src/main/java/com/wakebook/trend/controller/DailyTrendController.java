package com.wakebook.trend.controller;

import com.wakebook.common.response.ApiResponse;
import com.wakebook.trend.dto.DailyTrendResponse;
import com.wakebook.trend.service.DailyTrendQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/trends")
public class DailyTrendController {
    private final DailyTrendQueryService service;
    public DailyTrendController(DailyTrendQueryService service) { this.service = service; }
    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<DailyTrendResponse>> daily(@RequestParam String libraryCode,
        @RequestParam(required = false) LocalDate date) {
        DailyTrendQueryService.QueryResult result = service.publicDaily(libraryCode, date);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
            .eTag(result.etag()).body(ApiResponse.success(result.message(), result.data()));
    }
}
