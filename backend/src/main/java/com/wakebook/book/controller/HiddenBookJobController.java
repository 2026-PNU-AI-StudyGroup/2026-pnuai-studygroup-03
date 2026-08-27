package com.wakebook.book.controller;

import com.wakebook.book.dto.HiddenBookJobResponse;
import com.wakebook.book.service.HiddenBookJobService;
import com.wakebook.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hidden-book-jobs")
public class HiddenBookJobController {

    private final HiddenBookJobService jobService;

    public HiddenBookJobController(HiddenBookJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    public ApiResponse<HiddenBookJobResponse> getJob(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long jobId
    ) {
        return ApiResponse.success(HiddenBookJobResponse.from(jobService.getForRequester(jobId, jwt.getSubject())));
    }
}
