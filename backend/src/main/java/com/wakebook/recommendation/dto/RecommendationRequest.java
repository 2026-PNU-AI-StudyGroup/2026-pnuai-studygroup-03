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
    String mood,

    /** 돌려받을 추천 도서 수. 생략하면 기본값을 쓴다. 후보군 전체를 그대로 내려 주지 않기 위한 상한이다. */
    Integer limit
) {
}
