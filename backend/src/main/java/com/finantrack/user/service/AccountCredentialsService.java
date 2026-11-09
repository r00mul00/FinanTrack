package com.finantrack.user.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.auth.entity.UserToken;
import com.finantrack.auth.service.EmailSender;
import com.finantrack.auth.service.RefreshTokenService;
import com.finantrack.auth.service.UserTokenService;
import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.ResourceNotFoundException;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Alterações de senha e e-mail: sempre exigem a senha atual. */
@Service
public class AccountCredentialsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserTokenService userTokenService;
    private final EmailSender emailSender;
    private final FinanTrackProperties properties;
    private final AuditRecorder audit;
    private final TransactionTemplate transaction;

    public AccountCredentialsService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService, UserTokenService userTokenService, EmailSender emailSender,
            FinanTrackProperties properties, AuditRecorder audit, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.userTokenService = userTokenService;
        this.emailSender = emailSender;
        this.properties = properties;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Encerra todas as sessões: quem tinha a senha antiga deixa de ter acesso. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = load(userId);
        requirePassword(user, currentPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAllSessions(userId);
        audit.record(userId, AuditAction.PASSWORD_CHANGE, AuditEntityType.USER, userId);
    }

    /** E-mail já em uso não gera erro nem envio, para não revelar quais estão cadastrados. */
    public void requestEmailChange(UUID userId, String newEmail, String currentPassword) {
        User user = load(userId);
        requirePassword(user, currentPassword);

        String email = User.normalizeEmail(newEmail);
        if (email.equals(user.getEmail())) {
            throw new BusinessRuleException("Este já é o e-mail da sua conta.");
        }
        if (userRepository.existsByEmail(email)) {
            return;
        }

        Duration ttl = properties.tokens().emailVerificationTtl();
        String token = userTokenService.issueEmailChange(user, email, ttl);
        String link = properties.frontendUrl() + "/confirmar-email?token=" + token;
        emailSender.send(email, "Confirme seu novo e-mail no FinanTrack", """
                Olá, %s!

                Para usar este endereço na sua conta do FinanTrack, abra o link abaixo:

                %s

                O link é válido por %d horas. Se você não pediu a troca, ignore esta mensagem.
                """.formatted(user.getName(), link, ttl.toHours()));
    }

    /** O endereço antigo é avisado só depois de a troca estar gravada. */
    public void confirmEmailChange(UUID userId, String rawToken) {
        String previous;
        try {
            previous = transaction.execute(status -> apply(userId, rawToken));
        } catch (DataIntegrityViolationException takenMeanwhile) {
            throw emailTaken();
        }
        emailSender.send(previous, "O e-mail da sua conta FinanTrack foi alterado", """
                O e-mail de acesso da sua conta foi alterado.

                Se não foi você, redefina sua senha e entre em contato com o suporte.
                """);
    }

    private String apply(UUID userId, String rawToken) {
        UserToken token = userTokenService.consumeEmailChange(rawToken, userId);
        if (userRepository.existsByEmail(token.getNewEmail())) {
            throw emailTaken();
        }
        User user = token.getUser();
        String previous = user.getEmail();
        user.changeEmail(token.getNewEmail());
        audit.record(userId, AuditAction.EMAIL_CHANGE, AuditEntityType.USER, userId);
        return previous;
    }

    private ConflictException emailTaken() {
        return new ConflictException("Este e-mail não está mais disponível.");
    }

    /** 422, e não 401: a sessão continua válida, apenas a senha digitada está errada. */
    private void requirePassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleException("Senha atual incorreta.");
        }
    }

    private User load(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }
}
