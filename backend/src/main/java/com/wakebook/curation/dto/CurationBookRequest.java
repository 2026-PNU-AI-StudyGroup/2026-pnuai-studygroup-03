package com.wakebook.curation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CurationBookRequest(
        @NotBlank(message = "ISBN을 입력해 주세요.")
        @Size(max = 20, message = "ISBN은 20자 이하여야 합니다.")
        String isbn,

        @NotNull(message = "도서 순서를 입력해 주세요.")
        Integer displayOrder,

        @Size(max = 500, message = "코멘트는 500자 이하여야 합니다.")
        String comment
) {
}
