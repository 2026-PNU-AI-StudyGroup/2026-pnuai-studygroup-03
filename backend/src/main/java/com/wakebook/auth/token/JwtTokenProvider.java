package com.wakebook.auth.token;

import com.wakebook.user.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenExpiration;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.expiration}") long expirationMillis
    ) {
        this(jwtEncoder, expirationMillis, Clock.systemUTC());
    }

    JwtTokenProvider(JwtEncoder jwtEncoder, long expirationMillis, Clock clock) {
        if (expirationMillis <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION은 0보다 커야 합니다.");
        }
        this.jwtEncoder = jwtEncoder;
        this.accessTokenExpiration = Duration.ofMillis(expirationMillis);
        this.clock = clock;
    }

    public String createAccessToken(User user) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(accessTokenExpiration))
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
