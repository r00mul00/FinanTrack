package com.finantrack.auth.controller;

import com.finantrack.auth.dto.AuthSession;
import com.finantrack.auth.dto.LoginRequest;
import com.finantrack.auth.dto.RegisterRequest;
import com.finantrack.auth.dto.TokenResponse;
import com.finantrack.auth.service.AuthService;
import com.finantrack.auth.service.RefreshTokenService;
import com.finantrack.common.exception.InvalidRefreshTokenException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookie cookie;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
            RefreshTokenCookie cookie) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.cookie = cookie;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withCookie(authService.login(request, userAgent));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withCookie(refreshTokenService.rotate(refreshToken, userAgent));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    private ResponseEntity<TokenResponse> withCookie(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.create(session.refreshToken()))
                .body(session.accessToken());
    }
}
