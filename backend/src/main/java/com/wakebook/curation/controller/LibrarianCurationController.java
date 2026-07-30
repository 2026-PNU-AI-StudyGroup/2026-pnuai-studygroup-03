package com.wakebook.curation.controller;

import com.wakebook.common.PageResponse;
import com.wakebook.common.response.ApiResponse;
import com.wakebook.curation.dto.CurationGenerateRequest;
import com.wakebook.curation.dto.CurationGenerateResponse;
import com.wakebook.curation.dto.CurationResponse;
import com.wakebook.curation.dto.CurationSummaryResponse;
import com.wakebook.curation.dto.SaveCurationRequest;
import com.wakebook.curation.service.CurationGenerationService;
import com.wakebook.curation.service.CurationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/librarian/curations")
public class LibrarianCurationController {

    private final CurationGenerationService curationGenerationService;
    private final CurationService curationService;

    public LibrarianCurationController(
            CurationGenerationService curationGenerationService,
            CurationService curationService
    ) {
        this.curationGenerationService = curationGenerationService;
        this.curationService = curationService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<CurationGenerateResponse>> generate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CurationGenerateRequest request
    ) {
        CurationGenerateResponse response =
                curationGenerationService.generate(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("큐레이션 초안을 생성했습니다.", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CurationResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaveCurationRequest request
    ) {
        CurationResponse response = curationService.create(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("큐레이션이 저장되었습니다.", response));
    }

    @GetMapping
    public ApiResponse<PageResponse<CurationSummaryResponse>> getCurations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(curationService.getCurations(jwt.getSubject(), page, size));
    }

    @GetMapping("/{curationId}")
    public ApiResponse<CurationResponse> getCuration(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long curationId
    ) {
        return ApiResponse.success(curationService.getCuration(jwt.getSubject(), curationId));
    }

    @PatchMapping("/{curationId}")
    public ApiResponse<CurationResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long curationId,
            @Valid @RequestBody SaveCurationRequest request
    ) {
        CurationResponse response = curationService.update(jwt.getSubject(), curationId, request);
        return ApiResponse.success("큐레이션이 수정되었습니다.", response);
    }

    @DeleteMapping("/{curationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long curationId
    ) {
        curationService.delete(jwt.getSubject(), curationId);
        return ApiResponse.<Void>success("큐레이션이 삭제되었습니다.", null);
    }
}
