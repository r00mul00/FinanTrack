package com.finantrack.common.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finantrack")
public record FinanTrackProperties(ZoneId zoneId, Jwt jwt, Refresh refresh, Security security, Cors cors) {

    public record Jwt(String secret, Duration accessTokenTtl) {
    }

    public record Refresh(Duration ttl, Duration reuseGrace, boolean cookieSecure, String cookieSameSite) {
    }

    public record Security(boolean swaggerPublic) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
