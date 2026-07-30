package com.wakebook.recommendation.controller;

import com.wakebook.common.response.ApiResponse;
import com.wakebook.recommendation.dto.KeywordsRequest;
import com.wakebook.recommendation.dto.KeywordsResponse;
import com.wakebook.recommendation.service.KeywordGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiKeywordController {

    private final KeywordGenerationService keywordGenerationService;

    public AiKeywordController(KeywordGenerationService keywordGenerationService) {
        this.keywordGenerationService = keywordGenerationService;
    }

    @PostMapping("/keywords")
    public ApiResponse<KeywordsResponse> generateKeywords(@Valid @RequestBody KeywordsRequest request) {
        return ApiResponse.success(keywordGenerationService.generateKeywords(request.isbn()));
    }
}
