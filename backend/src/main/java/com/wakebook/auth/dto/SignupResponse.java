package com.wakebook.auth.dto;

import com.wakebook.user.domain.UserRole;

public record SignupResponse(
        Long id,
        UserRole role,
        String name
) {
}
