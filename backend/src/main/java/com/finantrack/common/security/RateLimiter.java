package com.finantrack.common.security;

import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Janela fixa em memória, por IP e por e-mail. Suficiente para uma única
 * instância; com várias, os contadores precisariam de um armazenamento comum.
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant startedAt, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int ipLimit;
    private final int emailLimit;
    private final Duration windowSize;

    public RateLimiter(Clock clock, FinanTrackProperties properties) {
        this.clock = clock;
        this.ipLimit = properties.rateLimit().ipLimit();
        this.emailLimit = properties.rateLimit().emailLimit();
        this.windowSize = properties.rateLimit().window();
    }

    public void checkIp(String action, String ip) {
        check(action + ":ip:" + ip, ipLimit);
    }

    public void checkEmail(String action, String normalizedEmail) {
        check(action + ":email:" + normalizedEmail, emailLimit);
    }

    private void check(String key, int limit) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> isExpired(window, now));
        }
        Window window = windows.compute(key, (ignored, current) -> current == null || isExpired(current, now)
                ? new Window(now, 1)
                : new Window(current.startedAt(), current.count() + 1));
        if (window.count() > limit) {
            throw new TooManyRequestsException();
        }
    }

    private boolean isExpired(Window window, Instant now) {
        return !window.startedAt().plus(windowSize).isAfter(now);
    }
}
