package com.wakebook.auth.dto;

import com.wakebook.user.domain.UserRole;

public record MyInfoResponse(
        Long id,
        String name,
        String nickname,
        UserRole role,
        String libraryName,
        String libraryCode
) {
}
