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
import com.finantrack.common.exception.InvalidCredentialsException;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String DUMMY_HASH = "dummy-hash";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthService(userRepository, passwordEncoder, accessTokenService);
    }

    @Test
    void loginWithUnknownEmailStillComparesPassword() {
        when(userRepository.findByEmail("nao@existe.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nao@existe.com", "qualquer-senha")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches("qualquer-senha", DUMMY_HASH);
        verify(accessTokenService, never()).issue(any());
    }

    @Test
    void loginNormalizesEmail() {
        User user = new User("Ana", "ana@exemplo.com", "hash-real");
        when(userRepository.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("senha-correta"), eq("hash-real"))).thenReturn(true);

        authService.login(new LoginRequest("  ANA@Exemplo.com ", "senha-correta"));

        verify(accessTokenService).issue(user);
    }

    @Test
    void registerWithExistingEmailDoesNotSave() {
        when(userRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

        authService.register(new RegisterRequest("Ana", "ana@exemplo.com", "senha-segura"));

        verify(userRepository, never()).saveAndFlush(any());
    }
}
