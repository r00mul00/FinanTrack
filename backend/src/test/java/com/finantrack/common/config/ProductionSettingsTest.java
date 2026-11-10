package com.finantrack.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSettingsTest {

    private static final String STRONG_SECRET = "k3Jq9vXz2LmP8rT5wY1bN6cF4hD7gS0a";
    private static final String STRONG_PASSWORD = "U8m!q2Lr#9vZ";

    private static FinanTrackProperties properties(
            String secret, String frontendUrl, List<String> corsOrigins, boolean cookieSecure) {
        return new FinanTrackProperties(
                ZoneId.of("America/Sao_Paulo"),
                frontendUrl,
                new FinanTrackProperties.Jwt(secret, Duration.ofMinutes(15)),
                new FinanTrackProperties.Refresh(Duration.ofDays(7), Duration.ofSeconds(10), cookieSecure, "Strict"),
                new FinanTrackProperties.Tokens(Duration.ofHours(24), Duration.ofMinutes(30)),
                new FinanTrackProperties.Mail("nao-responda@finantrack.app"),
                new FinanTrackProperties.RateLimit(30, 10, Duration.ofMinutes(15)),
                new FinanTrackProperties.Security(false),
                new FinanTrackProperties.Cors(corsOrigins));
    }

    private static FinanTrackProperties valid() {
        return properties(STRONG_SECRET, "https://finantrack.app", List.of(), true);
    }

    @Test
    void acceptsAStrongProductionConfiguration() {
        assertThat(ProductionSettings.problems(valid(), STRONG_PASSWORD)).isEmpty();
    }

    @Test
    void acceptsPlainHttpOnlyForTheLocalMachine() {
        var local = properties(STRONG_SECRET, "http://localhost:8080", List.of(), true);

        assertThat(ProductionSettings.problems(local, STRONG_PASSWORD)).isEmpty();
    }

    @Test
    void rejectsTheExampleValues() {
        var example = properties("defina-um-segredo-aleatorio-com-pelo-menos-32-bytes",
                "https://finantrack.app", List.of(), true);

        assertThat(ProductionSettings.problems(example, "defina-uma-senha"))
                .containsExactly("JWT_SECRET ainda tem o valor de exemplo.", "DB_PASSWORD ainda tem o valor de exemplo.");
    }

    @Test
    void rejectsShortOrRepetitiveSecrets() {
        var shortSecret = properties("curto", "https://finantrack.app", List.of(), true);
        var repetitive = properties("a".repeat(64), "https://finantrack.app", List.of(), true);

        assertThat(ProductionSettings.problems(shortSecret, STRONG_PASSWORD))
                .containsExactly("JWT_SECRET precisa ter pelo menos 32 bytes.");
        assertThat(ProductionSettings.problems(repetitive, STRONG_PASSWORD))
                .containsExactly("JWT_SECRET é repetitivo demais; gere um com infra/generate-secrets.sh.");
        assertThat(ProductionSettings.problems(valid(), "curta"))
                .containsExactly("DB_PASSWORD precisa ter pelo menos 12 caracteres.");
    }

    @Test
    void requiresHttpsForPublicAddresses() {
        var http = properties(STRONG_SECRET, "http://finantrack.app", List.of("http://outro.app"), true);
        var relative = properties(STRONG_SECRET, "finantrack.app", List.of(), true);

        assertThat(ProductionSettings.problems(http, STRONG_PASSWORD)).containsExactly(
                "FRONTEND_URL precisa usar HTTPS.", "CORS_ALLOWED_ORIGINS precisa usar HTTPS.");
        assertThat(ProductionSettings.problems(relative, STRONG_PASSWORD))
                .containsExactly("FRONTEND_URL precisa ser um endereço completo, como https://exemplo.com.");
    }

    @Test
    void requiresASecureSessionCookie() {
        var insecure = properties(STRONG_SECRET, "https://finantrack.app", List.of(), false);

        assertThat(ProductionSettings.problems(insecure, STRONG_PASSWORD))
                .containsExactly("o cookie de sessão precisa ser Secure em produção.");
    }

    @Test
    void neverEchoesTheSecretValues() {
        var weak = properties("segredo-fraco", "http://finantrack.app", List.of(), true);

        assertThat(String.join(" ", ProductionSettings.problems(weak, "senha-fraca")))
                .doesNotContain("segredo-fraco", "senha-fraca");
    }
}
