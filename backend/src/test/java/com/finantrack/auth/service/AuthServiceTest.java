package com.finantrack.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finantrack.auth.dto.LoginRequest;
import com.finantrack.auth.dto.RegisterRequest;
import com.finantrack.common.exception.EmailNotVerifiedException;
import com.finantrack.common.exception.InvalidCredentialsException;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String DUMMY_HASH = "dummy-hash";
    private static final String USER_AGENT = "navegador";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final UserRegistrationService userRegistrationService = mock(UserRegistrationService.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthService(userRepository, passwordEncoder, refreshTokenService, emailVerificationService,
                userRegistrationService);
    }

    @Test
    void loginWithUnknownEmailStillComparesPassword() {
        when(userRepository.findByEmail("nao@existe.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nao@existe.com", "qualquer-senha"), USER_AGENT))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches("qualquer-senha", DUMMY_HASH);
        verify(refreshTokenService, never()).startSession(any(), any());
    }

    @Test
    void loginNormalizesEmail() {
        User user = new User("Ana", "ana@exemplo.com", "hash-real");
        user.markEmailVerified(Instant.now());
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("senha-correta"), eq("hash-real"))).thenReturn(true);

        authService.login(new LoginRequest("  ANA@Exemplo.com ", "senha-correta"), USER_AGENT);

        verify(refreshTokenService).startSession(user, USER_AGENT);
    }

    @Test
    void unverifiedUserWithCorrectPasswordIsBlocked() {
        User user = new User("Ana", "ana@exemplo.com", "hash-real");
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("senha-correta"), eq("hash-real"))).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@exemplo.com", "senha-correta"), USER_AGENT))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(refreshTokenService, never()).startSession(any(), any());
    }

    @Test
    void unverifiedUserWithWrongPasswordSeesOnlyInvalidCredentials() {
        User user = new User("Ana", "ana@exemplo.com", "hash-real");
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@exemplo.com", "senha-errada"), USER_AGENT))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void registerWithExistingEmailDoesNotSaveNorSendEmail() {
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

        authService.register(new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura"));

        verify(userRegistrationService, never()).create(any(), any(), any());
        verify(emailVerificationService, never()).sendTo(any());
    }
}
