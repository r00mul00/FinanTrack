package com.finantrack.auth.service;

import com.finantrack.auth.dto.AuthSession;
import com.finantrack.auth.dto.LoginRequest;
import com.finantrack.auth.dto.RegisterRequest;
import com.finantrack.common.exception.InvalidCredentialsException;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode("hash-para-igualar-o-tempo-de-resposta");
    }

    /** E-mail já cadastrado não gera erro, para não revelar quais e-mails existem. */
    public void register(RegisterRequest request) {
        String email = normalize(request.email());
        String passwordHash = passwordEncoder.encode(request.password());

        if (userRepository.existsByEmail(email)) {
            return;
        }
        try {
            userRepository.saveAndFlush(new User(request.name().trim(), email, passwordHash));
        } catch (DataIntegrityViolationException duplicateFromConcurrentRequest) {
            // Duas requisições simultâneas: o índice único garante um único cadastro.
        }
    }

    /** Sem usuário, compara com um hash fictício para o tempo de resposta ser equivalente. */
    public AuthSession login(LoginRequest request, String userAgent) {
        Optional<User> user = userRepository.findByEmail(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);

        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return refreshTokenService.startSession(user.get(), userAgent);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
