package com.finantrack.auth.controller;

import com.finantrack.auth.dto.AuthSession;
import com.finantrack.auth.dto.EmailRequest;
import com.finantrack.auth.dto.LoginRequest;
import com.finantrack.auth.dto.RegisterRequest;
import com.finantrack.auth.dto.ResetPasswordRequest;
import com.finantrack.auth.dto.TokenRequest;
import com.finantrack.auth.dto.TokenResponse;
import com.finantrack.auth.service.AuthService;
import com.finantrack.auth.service.EmailVerificationService;
import com.finantrack.auth.service.PasswordResetService;
import com.finantrack.auth.service.RefreshTokenService;
import com.finantrack.common.exception.InvalidRefreshTokenException;
import com.finantrack.common.security.RateLimiter;
import com.finantrack.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final RateLimiter rateLimiter;
    private final RefreshTokenCookie cookie;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
            EmailVerificationService emailVerificationService, PasswordResetService passwordResetService,
            RateLimiter rateLimiter, RefreshTokenCookie cookie) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
        this.cookie = cookie;
    }

    @Operation(summary = "Cadastra um usuário", description = "Cria as categorias padrão e envia o e-mail de verificação. Responde 202 mesmo se o e-mail já estiver cadastrado, para não revelar quais e-mails existem.")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas para este IP ou e-mail.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        limit("register", http, request.email());
        authService.register(request);
    }

    @Operation(summary = "Entra na conta", description = "Devolve o access token (JWT, 15 minutos) e grava o refresh token no cookie HttpOnly `finantrack_refresh`, válido só para `/api/v1/auth`. Entrar cancela um pedido de exclusão da conta ainda em carência.")
    @ApiResponse(responseCode = "401", description = "E-mail ou senha incorretos.")
    @ApiResponse(responseCode = "403", description = "E-mail ainda não verificado (`code`: EMAIL_NOT_VERIFIED).")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas para este IP ou e-mail.")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        limit("login", http, request.email());
        return withCookie(authService.login(request, userAgent));
    }

    @Operation(summary = "Renova a sessão", description = "Troca o refresh token do cookie por um novo (rotação) e emite outro access token. Reapresentar um token já trocado revoga toda a família de sessões, por suspeita de roubo.")
    @ApiResponse(responseCode = "401", description = "Cookie ausente, expirado ou revogado; o cookie é apagado.")
    @ApiResponse(responseCode = "409", description = "O token foi trocado há menos de 10 segundos por outra aba; tente de novo em instantes.")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withCookie(refreshTokenService.rotate(refreshToken, userAgent));
    }

    @Operation(summary = "Sai da conta", description = "Revoga a família do refresh token atual e apaga o cookie.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }

    @Operation(summary = "Confirma o e-mail", description = "Valida o token recebido por e-mail (24 horas).")
    @ApiResponse(responseCode = "422", description = "Token inválido, expirado ou já usado.")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas para este IP.")
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        rateLimiter.checkIp("verify-email", http.getRemoteAddr());
        emailVerificationService.verify(request.token());
    }

    @Operation(summary = "Reenvia o e-mail de verificação", description = "Responde 202 mesmo se o e-mail não existir ou já estiver verificado.")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas para este IP ou e-mail.")
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        limit("resend-verification", http, request.email());
        emailVerificationService.resend(request.email());
    }

    @Operation(summary = "Pede a redefinição de senha", description = "Envia um link válido por 30 minutos. Responde 202 mesmo se o e-mail não existir.")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas para este IP ou e-mail.")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        limit("forgot-password", http, request.email());
        passwordResetService.requestReset(request.email());
    }

    @Operation(summary = "Redefine a senha", description = "Usa o token do e-mail e encerra todas as sessões do usuário.")
    @ApiResponse(responseCode = "422", description = "Token inválido, expirado ou já usado.")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas para este IP.")
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        rateLimiter.checkIp("reset-password", http.getRemoteAddr());
        passwordResetService.reset(request.token(), request.newPassword());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    private void limit(String action, HttpServletRequest http, String email) {
        rateLimiter.checkIp(action, http.getRemoteAddr());
        rateLimiter.checkEmail(action, User.normalizeEmail(email));
    }

    private ResponseEntity<TokenResponse> withCookie(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.create(session.refreshToken()))
                .body(session.accessToken());
    }
}
