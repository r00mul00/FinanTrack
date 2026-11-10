package com.finantrack.common.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Regras que a configuração precisa cumprir em produção. As mensagens nunca repetem
 * o valor recebido, para que um segredo não acabe no log.
 */
public final class ProductionSettings {

    /** Os exemplos (.env.example e .env.prod.example) usam valores que começam assim. */
    static final String PLACEHOLDER_PREFIX = "defina";

    private static final int MIN_JWT_SECRET_BYTES = 32;
    /** Barra segredos repetitivos, como "aaaa..." ou "1234...", mesmo com 32 bytes. */
    private static final int MIN_JWT_SECRET_DISTINCT_CHARS = 16;
    private static final int MIN_DB_PASSWORD_LENGTH = 12;
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private ProductionSettings() {
    }

    public static List<String> problems(FinanTrackProperties properties, String dbPassword) {
        List<String> problems = new ArrayList<>();
        checkJwtSecret(properties.jwt().secret(), problems);
        checkDbPassword(dbPassword, problems);
        checkUrl("FRONTEND_URL", properties.frontendUrl(), problems);
        for (String origin : properties.cors().allowedOrigins()) {
            checkUrl("CORS_ALLOWED_ORIGINS", origin, problems);
        }
        if (!properties.refresh().cookieSecure()) {
            problems.add("o cookie de sessão precisa ser Secure em produção.");
        }
        return problems;
    }

    private static void checkJwtSecret(String secret, List<String> problems) {
        if (isPlaceholder(secret)) {
            problems.add("JWT_SECRET ainda tem o valor de exemplo.");
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET precisa ter pelo menos " + MIN_JWT_SECRET_BYTES + " bytes.");
        } else if (secret.chars().distinct().count() < MIN_JWT_SECRET_DISTINCT_CHARS) {
            problems.add("JWT_SECRET é repetitivo demais; gere um com infra/generate-secrets.sh.");
        }
    }

    private static void checkDbPassword(String password, List<String> problems) {
        if (isPlaceholder(password)) {
            problems.add("DB_PASSWORD ainda tem o valor de exemplo.");
        } else if (password.length() < MIN_DB_PASSWORD_LENGTH) {
            problems.add("DB_PASSWORD precisa ter pelo menos " + MIN_DB_PASSWORD_LENGTH + " caracteres.");
        }
    }

    /** Exige HTTPS; HTTP só vale para a própria máquina (teste das imagens). */
    private static void checkUrl(String name, String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(name + " não foi definido.");
            return;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            problems.add(name + " não é um endereço válido.");
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            problems.add(name + " precisa ser um endereço completo, como https://exemplo.com.");
        } else if (!scheme.equals("https") && !(scheme.equals("http") && LOCAL_HOSTS.contains(host))) {
            problems.add(name + " precisa usar HTTPS.");
        }
    }

    private static boolean isPlaceholder(String value) {
        return value == null || value.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(PLACEHOLDER_PREFIX);
    }
}
