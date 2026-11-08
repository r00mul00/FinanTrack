package com.finantrack.auth.service;

import com.finantrack.auth.dto.AuthSession;
import com.finantrack.auth.entity.RefreshToken;
import com.finantrack.auth.repository.RefreshTokenRepository;
import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.InvalidRefreshTokenException;
import com.finantrack.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida do refresh token. Só o hash SHA-256 é gravado no banco. */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_USER_AGENT = 255;

    private final RefreshTokenRepository repository;
    private final AccessTokenService accessTokenService;
    private final Clock clock;
    private final Duration ttl;
    private final Duration reuseGrace;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AccessTokenService accessTokenService,
            Clock clock, FinanTrackProperties properties) {
        this.repository = repository;
        this.accessTokenService = accessTokenService;
        this.clock = clock;
        this.ttl = properties.refresh().ttl();
        this.reuseGrace = properties.refresh().reuseGrace();
    }

    /** Inicia uma sessão: nova família de tokens. */
    @Transactional
    public AuthSession startSession(User user, String userAgent) {
        return new AuthSession(accessTokenService.issue(user), store(user, UUID.randomUUID(), userAgent));
    }

    /**
     * Troca o token por um novo. O noRollbackFor mantém no banco a revogação da
     * família feita quando se detecta reutilização.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthSession rotate(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken current = repository.findForUpdateByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();

        if (current.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException();
        }
        if (current.getRotatedAt() != null) {
            if (current.getRotatedAt().plus(reuseGrace).isAfter(now)) {
                throw new ConflictException("A sessão acabou de ser renovada. Tente novamente.");
            }
            repository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        current.markRotated(now);
        User user = current.getUser();
        return new AuthSession(accessTokenService.issue(user), store(user, current.getFamilyId(), userAgent));
    }

    /** Encerra a sessão inteira. Token desconhecido não gera erro: o logout é idempotente. */
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private String store(User user, UUID familyId, String userAgent) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(new RefreshToken(user, familyId, hash(rawToken),
                clock.instant().plus(ttl), truncate(userAgent)));
        return rawToken;
    }

    private String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT ? userAgent : userAgent.substring(0, MAX_USER_AGENT);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }
}
