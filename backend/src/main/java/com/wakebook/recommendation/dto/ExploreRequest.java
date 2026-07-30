package com.wakebook.recommendation.dto;

import jakarta.validation.constraints.NotBlank;

public record ExploreRequest(
    @NotBlank(message = "isbn을 입력해 주세요.")
    String isbn,

    @NotBlank(message = "libraryCode를 입력해 주세요.")
    String libraryCode,

    @NotBlank(message = "type을 선택해 주세요.")
    String type
) {
}
