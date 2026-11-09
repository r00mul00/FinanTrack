package com.finantrack.auth.service;

import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.auth.entity.UserTokenType;
import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final RefreshTokenService refreshTokenService;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final FinanTrackProperties properties;
    private final AuditRecorder audit;

    public PasswordResetService(UserRepository userRepository, UserTokenService userTokenService,
            RefreshTokenService refreshTokenService, EmailSender emailSender, PasswordEncoder passwordEncoder,
            FinanTrackProperties properties, AuditRecorder audit) {
        this.audit = audit;
        this.userRepository = userRepository;
        this.userTokenService = userTokenService;
        this.refreshTokenService = refreshTokenService;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /** E-mail desconhecido não gera erro nem envio. */
    public void requestReset(String email) {
        userRepository.findByEmail(User.normalizeEmail(email)).ifPresent(this::sendTo);
    }

    /** Troca a senha e encerra todas as sessões na mesma transação. */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        User user = userTokenService.consume(rawToken, UserTokenType.PASSWORD_RESET);
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAllSessions(user.getId());
        audit.record(user.getId(), AuditAction.PASSWORD_RESET, AuditEntityType.USER, user.getId());
    }

    private void sendTo(User user) {
        String token = userTokenService.issue(user, UserTokenType.PASSWORD_RESET,
                properties.tokens().passwordResetTtl());
        String link = properties.frontendUrl() + "/redefinir-senha?token=" + token;

        emailSender.send(user.getEmail(), "Redefinição de senha do FinanTrack", """
                Olá, %s!

                Para criar uma nova senha, abra o link abaixo:

                %s

                O link é válido por 30 minutos. Se você não pediu a redefinição, ignore esta mensagem.
                """.formatted(user.getName(), link));
    }
}
