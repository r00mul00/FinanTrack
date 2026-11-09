package com.finantrack.common.security;

import com.finantrack.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Recusa o access token de quem excluiu a conta ou pediu a exclusão.
 * Sem esta verificação, o token continuaria valendo até expirar (15 minutos).
 */
@Component
public class ActiveUserValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INACTIVE_USER =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Usuário inexistente ou inativo.", null);

    private final UserRepository userRepository;

    public ActiveUserValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException invalidSubject) {
            return OAuth2TokenValidatorResult.failure(INACTIVE_USER);
        }
        return userRepository.existsByIdAndDeletionRequestedAtIsNull(userId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INACTIVE_USER);
    }
}
