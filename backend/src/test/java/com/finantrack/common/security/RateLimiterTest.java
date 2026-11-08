package com.finantrack.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock, new FinanTrackProperties(null, null, null, null, null,
            null, new FinanTrackProperties.RateLimit(2, 2, Duration.ofMinutes(15)), null, null));

    @Test
    void blocksAboveLimit() {
        limiter.checkIp("login", "1.1.1.1");
        limiter.checkIp("login", "1.1.1.1");

        assertThatThrownBy(() -> limiter.checkIp("login", "1.1.1.1")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void windowResetsAfterExpiring() {
        limiter.checkIp("login", "1.1.1.1");
        limiter.checkIp("login", "1.1.1.1");

        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkIp("login", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void countersAreIndependentByKeyAndAction() {
        limiter.checkIp("login", "1.1.1.1");
        limiter.checkIp("login", "1.1.1.1");

        assertThatCode(() -> limiter.checkIp("login", "2.2.2.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkIp("register", "1.1.1.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkEmail("login", "1.1.1.1")).doesNotThrowAnyException();
    }
}
