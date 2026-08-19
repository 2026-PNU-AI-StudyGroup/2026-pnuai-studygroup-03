package com.wakebook.curation.controller;

import com.wakebook.common.PageResponse;
import com.wakebook.common.response.ApiResponse;
import com.wakebook.curation.dto.CurationResponse;
import com.wakebook.curation.dto.PublicCurationSummaryResponse;
import com.wakebook.curation.service.CurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/curations")
public class PublicCurationController {

    private final CurationService curationService;

    public PublicCurationController(CurationService curationService) {
        this.curationService = curationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PublicCurationSummaryResponse>> getCurations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size
    ) {
        return ApiResponse.success(curationService.getPublicCurations(page, size));
    }

    @GetMapping("/{curationId}")
    public ApiResponse<CurationResponse> getCuration(@PathVariable Long curationId) {
        return ApiResponse.success(curationService.getPublicCuration(curationId));
    }
}
