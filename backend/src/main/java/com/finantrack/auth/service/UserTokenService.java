package com.finantrack.auth.service;

import com.finantrack.auth.entity.UserToken;
import com.finantrack.auth.entity.UserTokenType;
import com.finantrack.auth.repository.UserTokenRepository;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.security.OpaqueTokens;
import com.finantrack.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserTokenService {

    private final UserTokenRepository repository;
    private final Clock clock;

    public UserTokenService(UserTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Emite um novo token e invalida os anteriores do mesmo tipo. */
    @Transactional
    public String issue(User user, UserTokenType type, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateActive(user.getId(), type, now);

        String rawToken = OpaqueTokens.generate();
        repository.save(new UserToken(user, type, OpaqueTokens.sha256(rawToken), now.plus(ttl)));
        return rawToken;
    }

    /** Emite o link de troca de e-mail, guardando o novo endereço com o token. */
    @Transactional
    public String issueEmailChange(User user, String newEmail, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateActive(user.getId(), UserTokenType.EMAIL_CHANGE, now);

        String rawToken = OpaqueTokens.generate();
        repository.save(new UserToken(user, OpaqueTokens.sha256(rawToken), now.plus(ttl), newEmail));
        return rawToken;
    }

    /** O link só vale na conta que o pediu; em outra, não é gasto. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserToken consumeEmailChange(String rawToken, UUID userId) {
        Instant now = clock.instant();
        UserToken token = repository.findForUpdate(OpaqueTokens.sha256(rawToken), UserTokenType.EMAIL_CHANGE)
                .filter(candidate -> candidate.isUsable(now))
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessRuleException("Link inválido ou expirado."));
        token.markUsed(now);
        return token;
    }

    /** Consome o token dentro da transação de quem aplica o efeito. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User consume(String rawToken, UserTokenType type) {
        Instant now = clock.instant();
        UserToken token = repository.findForUpdate(OpaqueTokens.sha256(rawToken), type)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new BusinessRuleException("Link inválido ou expirado."));
        token.markUsed(now);
        return token.getUser();
    }
}
