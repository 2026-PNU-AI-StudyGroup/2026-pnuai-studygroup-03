package com.wakebook.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecommendationRequest(
    @NotBlank(message = "isbn을 입력해 주세요.")
    String isbn,

    @NotBlank(message = "libraryCode를 입력해 주세요.")
    String libraryCode,

    @NotEmpty(message = "keywords를 하나 이상 선택해 주세요.")
    List<String> keywords,

    @NotBlank(message = "purpose를 선택해 주세요.")
    String purpose,

    @NotBlank(message = "mood를 선택해 주세요.")
    String mood
) {
}
