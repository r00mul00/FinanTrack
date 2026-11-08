package com.finantrack.auth.service;

import com.finantrack.auth.dto.AuthSession;
import com.finantrack.auth.dto.LoginRequest;
import com.finantrack.auth.dto.RegisterRequest;
import com.finantrack.common.exception.EmailNotVerifiedException;
import com.finantrack.common.exception.InvalidCredentialsException;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService, EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.dummyHash = passwordEncoder.encode("hash-para-igualar-o-tempo-de-resposta");
    }

    /** E-mail já cadastrado não gera erro, para não revelar quais e-mails existem. */
    public void register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        String passwordHash = passwordEncoder.encode(request.password());

        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user;
        try {
            user = userRepository.saveAndFlush(new User(request.name().trim(), email, passwordHash));
        } catch (DataIntegrityViolationException duplicateFromConcurrentRequest) {
            return; // Duas requisições simultâneas: o índice único garante um único cadastro.
        }
        emailVerificationService.sendTo(user);
    }

    /**
     * Sem usuário, compara com um hash fictício para o tempo de resposta ser equivalente.
     * O aviso de e-mail não validado só aparece para quem acertou a senha.
     */
    public AuthSession login(LoginRequest request, String userAgent) {
        Optional<User> user = userRepository.findByEmail(User.normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);

        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        if (!user.get().isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return refreshTokenService.startSession(user.get(), userAgent);
    }
}
