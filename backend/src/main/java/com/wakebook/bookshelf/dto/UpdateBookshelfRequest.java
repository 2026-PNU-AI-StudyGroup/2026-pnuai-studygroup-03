package com.wakebook.bookshelf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBookshelfRequest(
        @NotBlank(message = "컬렉션 이름을 입력해 주세요.")
        @Size(max = 100, message = "컬렉션 이름은 100자 이하여야 합니다.")
        String name,

        @Size(max = 500, message = "컬렉션 설명은 500자 이하여야 합니다.")
        String description
) {
}
