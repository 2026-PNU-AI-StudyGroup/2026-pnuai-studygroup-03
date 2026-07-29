package com.wakebook.bookshelf.dto;

import com.wakebook.bookshelf.domain.ReadingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateReadingStatusRequest(
        @NotNull(message = "읽기 상태를 선택해 주세요.")
        ReadingStatus status
) {
}
