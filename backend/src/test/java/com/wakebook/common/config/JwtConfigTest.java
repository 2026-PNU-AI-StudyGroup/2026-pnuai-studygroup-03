package com.wakebook.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    @Test
    void jwtSecretMustBeAtLeast32Bytes() {
        JwtConfig jwtConfig = new JwtConfig();

        assertThatThrownBy(() -> jwtConfig.jwtEncoder("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET은 UTF-8 기준 32바이트 이상이어야 합니다.");
    }
}
