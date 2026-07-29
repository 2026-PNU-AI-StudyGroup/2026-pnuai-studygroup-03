package com.wakebook.bookshelf.dto;

import com.wakebook.bookshelf.domain.ReadingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddBookshelfBookRequest(
        @NotBlank(message = "ISBN을 입력해 주세요.")
        @Size(max = 20, message = "ISBN은 20자 이하여야 합니다.")
        String isbn,

        @NotNull(message = "읽기 상태를 선택해 주세요.")
        ReadingStatus status
) {
}
