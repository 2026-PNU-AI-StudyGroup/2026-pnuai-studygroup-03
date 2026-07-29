package com.wakebook.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record CompareRequest(
    @NotBlank(message = "popularBook을 입력해 주세요.")
    String popularBook,

    @NotBlank(message = "hiddenBook을 입력해 주세요.")
    String hiddenBook
) {
}
