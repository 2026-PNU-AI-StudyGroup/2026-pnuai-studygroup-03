package com.wakebook.curation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveCurationRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        @Size(max = 1000, message = "소개는 1000자 이하여야 합니다.")
        String description,

        Boolean isPublic,

        @Valid
        List<CurationBookRequest> books
) {
}
