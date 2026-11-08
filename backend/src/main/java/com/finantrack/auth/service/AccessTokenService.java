package com.finantrack.auth.service;

import com.finantrack.auth.dto.TokenResponse;
import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.common.security.JwtConfig;
import com.finantrack.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration ttl;

    public AccessTokenService(JwtEncoder jwtEncoder, Clock clock, FinanTrackProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.ttl = properties.jwt().accessTokenTtl();
    }

    public TokenResponse issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
    }
}
