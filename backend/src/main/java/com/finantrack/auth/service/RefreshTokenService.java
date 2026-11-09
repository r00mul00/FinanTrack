package com.finantrack.auth.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.auth.dto.AuthSession;
import com.finantrack.auth.entity.RefreshToken;
import com.finantrack.auth.repository.RefreshTokenRepository;
import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.InvalidRefreshTokenException;
import com.finantrack.common.security.OpaqueTokens;
import com.finantrack.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida do refresh token. Só o hash SHA-256 é gravado no banco. */
@Service
public class RefreshTokenService {

    private static final int MAX_USER_AGENT = 255;

    private final RefreshTokenRepository repository;
    private final AccessTokenService accessTokenService;
    private final Clock clock;
    private final Duration ttl;
    private final Duration reuseGrace;
    private final AuditRecorder audit;

    public RefreshTokenService(RefreshTokenRepository repository, AccessTokenService accessTokenService,
            Clock clock, FinanTrackProperties properties, AuditRecorder audit) {
        this.audit = audit;
        this.repository = repository;
        this.accessTokenService = accessTokenService;
        this.clock = clock;
        this.ttl = properties.refresh().ttl();
        this.reuseGrace = properties.refresh().reuseGrace();
    }

    /** Inicia uma sessão: nova família de tokens. */
    @Transactional
    public AuthSession startSession(User user, String userAgent) {
        audit.record(user.getId(), AuditAction.LOGIN, AuditEntityType.USER, user.getId());
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
        RefreshToken current = repository.findForUpdateByTokenHash(OpaqueTokens.sha256(rawToken))
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
            UUID ownerId = current.getUser().getId();
            audit.record(ownerId, AuditAction.REFRESH_TOKEN_REUSE, AuditEntityType.USER, ownerId);
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
        repository.findByTokenHash(OpaqueTokens.sha256(rawToken)).ifPresent(token -> {
            repository.revokeFamily(token.getFamilyId(), clock.instant());
            UUID ownerId = token.getUser().getId();
            audit.record(ownerId, AuditAction.LOGOUT, AuditEntityType.USER, ownerId);
        });
    }

    /** Encerra todas as sessões do usuário, por exemplo ao redefinir a senha. */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        repository.revokeAllByUser(userId, clock.instant());
    }

    private String store(User user, UUID familyId, String userAgent) {
        String rawToken = OpaqueTokens.generate();
        repository.save(new RefreshToken(user, familyId, OpaqueTokens.sha256(rawToken),
                clock.instant().plus(ttl), truncate(userAgent)));
        return rawToken;
    }

    private String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT ? userAgent : userAgent.substring(0, MAX_USER_AGENT);
    }
}
