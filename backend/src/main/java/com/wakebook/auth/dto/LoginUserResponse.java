package com.wakebook.auth.dto;

import com.wakebook.user.domain.UserRole;

public record LoginUserResponse(
        Long id,
        String name,
        UserRole role,
        String libraryName,
        String libraryCode
) {
}
