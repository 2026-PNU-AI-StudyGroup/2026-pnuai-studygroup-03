package com.wakebook.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record KeywordsRequest(
    @NotBlank(message = "isbn을 입력해 주세요.")
    String isbn
) {
}
