package com.wakebook.auth.dto;

public record LoginResponse(
        String accessToken,
        LoginUserResponse user
) {
}
