package com.finantrack.auth.controller;

import com.finantrack.common.config.FinanTrackProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookie {

    public static final String NAME = "finantrack_refresh";

    private static final String PATH = "/api/v1/auth";

    private final FinanTrackProperties.Refresh properties;

    public RefreshTokenCookie(FinanTrackProperties properties) {
        this.properties = properties.refresh();
    }

    public String create(String refreshToken) {
        return build(refreshToken, properties.ttl());
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
