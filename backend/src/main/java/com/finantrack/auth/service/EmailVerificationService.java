package com.finantrack.auth.service;

import com.finantrack.auth.entity.UserTokenType;
import com.finantrack.common.config.FinanTrackProperties;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final UserTokenService userTokenService;
    private final EmailSender emailSender;
    private final Clock clock;
    private final FinanTrackProperties properties;

    public EmailVerificationService(UserRepository userRepository, UserTokenService userTokenService,
            EmailSender emailSender, Clock clock, FinanTrackProperties properties) {
        this.userRepository = userRepository;
        this.userTokenService = userTokenService;
        this.emailSender = emailSender;
        this.clock = clock;
        this.properties = properties;
    }

    public void sendTo(User user) {
        String token = userTokenService.issue(user, UserTokenType.EMAIL_VERIFICATION,
                properties.tokens().emailVerificationTtl());
        String link = properties.frontendUrl() + "/validar-email?token=" + token;

        emailSender.send(user.getEmail(), "Confirme seu e-mail no FinanTrack", """
                Olá, %s!

                Para ativar sua conta no FinanTrack, abra o link abaixo:

                %s

                O link é válido por 24 horas. Se você não criou esta conta, ignore esta mensagem.
                """.formatted(user.getName(), link));
    }

    /** E-mail desconhecido ou já validado não gera erro nem envio. */
    public void resend(String email) {
        userRepository.findByEmail(User.normalizeEmail(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendTo);
    }

    @Transactional
    public void verify(String rawToken) {
        userTokenService.consume(rawToken, UserTokenType.EMAIL_VERIFICATION).markEmailVerified(clock.instant());
    }
}
