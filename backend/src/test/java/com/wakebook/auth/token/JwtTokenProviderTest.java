package com.wakebook.auth.token;

import com.wakebook.user.domain.User;
import com.wakebook.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void accessTokenUsesHs256AndContainsUserIdentityForOneHour() {
        SecretKey secretKey = secretKey();
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Clock clock = Clock.fixed(issuedAt, ZoneOffset.UTC);
        JwtTokenProvider tokenProvider = new JwtTokenProvider(jwtEncoder, 3_600_000L, clock);

        User user = new User(
                UserRole.LIBRARIAN,
                "김도서",
                "librarian@wakebook.kr",
                "encoded-password",
                "책지기",
                "부산대학교 도서관",
                "자료운영팀"
        );
        ReflectionTestUtils.setField(user, "id", 12L);

        String accessToken = tokenProvider.createAccessToken(user);

        JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getSubject()).isEqualTo("12");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("LIBRARIAN");
        assertThat(jwt.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(jwt.getExpiresAt()).isEqualTo(issuedAt.plusSeconds(3600));
    }

    @Test
    void expirationMustBePositive() {
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();

        assertThatThrownBy(() -> new JwtTokenProvider(jwtEncoder, 0, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT_EXPIRATION은 0보다 커야 합니다.");
    }

    private static SecretKey secretKey() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
