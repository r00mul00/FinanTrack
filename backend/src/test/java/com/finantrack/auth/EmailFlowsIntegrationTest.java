package com.finantrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.auth.controller.RefreshTokenCookie;
import com.finantrack.auth.repository.RefreshTokenRepository;
import com.finantrack.auth.repository.UserTokenRepository;
import com.finantrack.auth.service.EmailSender;
import com.finantrack.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailFlowsIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");
    private static final String EMAIL = "ana@exemplo.com";
    private static final String PASSWORD = "senha-bem-segura";
    private static final String NEW_PASSWORD = "nova-senha-segura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void registerSendsLinkToFrontendAndStoresOnlyHash() throws Exception {
        register();

        String body = lastEmailBody();
        assertThat(body).contains("http://localhost:5080/validar-email?token=");
        String stored = jdbcTemplate.queryForObject("select token_hash from user_tokens", String.class);
        assertThat(stored).hasSize(64);
        assertThat(body).doesNotContain(stored);
    }

    @Test
    void loginIsBlockedUntilEmailIsVerified() throws Exception {
        register();

        login(PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        post("/verify-email", tokenJson(tokenFromLastEmail())).andExpect(status().isNoContent());

        login(PASSWORD).andExpect(status().isOk());
    }

    @Test
    void verificationTokenWorksOnlyOnce() throws Exception {
        register();
        String token = tokenFromLastEmail();

        post("/verify-email", tokenJson(token)).andExpect(status().isNoContent());

        post("/verify-email", tokenJson(token)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void expiredVerificationTokenIsRejected() throws Exception {
        register();
        String token = tokenFromLastEmail();
        jdbcTemplate.update("update user_tokens set expires_at = now() - interval '1 second'");

        post("/verify-email", tokenJson(token)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void resendInvalidatesPreviousLink() throws Exception {
        register();
        String first = tokenFromLastEmail();
        clearInvocations(emailSender);

        post("/resend-verification", emailJson(EMAIL)).andExpect(status().isAccepted());
        String second = tokenFromLastEmail();

        post("/verify-email", tokenJson(first)).andExpect(status().isUnprocessableContent());
        post("/verify-email", tokenJson(second)).andExpect(status().isNoContent());
    }

    @Test
    void resendDoesNothingForVerifiedOrUnknownEmail() throws Exception {
        registerAndVerify();
        clearInvocations(emailSender);

        post("/resend-verification", emailJson(EMAIL)).andExpect(status().isAccepted());
        post("/resend-verification", emailJson("ninguem@exemplo.com")).andExpect(status().isAccepted());

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPasswordRespondsTheSameForUnknownEmailWithoutSending() throws Exception {
        post("/forgot-password", emailJson("ninguem@exemplo.com")).andExpect(status().isAccepted());

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resetChangesPasswordAndRevokesAllSessions() throws Exception {
        registerAndVerify();
        String sessionCookie = refreshCookie(login(PASSWORD).andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));
        clearInvocations(emailSender);

        post("/forgot-password", emailJson(EMAIL)).andExpect(status().isAccepted());
        assertThat(lastEmailBody()).contains("http://localhost:5080/redefinir-senha?token=");
        post("/reset-password", resetJson(tokenFromLastEmail(), NEW_PASSWORD)).andExpect(status().isNoContent());

        login(PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, sessionCookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetTokenWorksOnlyOnce() throws Exception {
        registerAndVerify();
        clearInvocations(emailSender);
        post("/forgot-password", emailJson(EMAIL));
        String token = tokenFromLastEmail();

        post("/reset-password", resetJson(token, NEW_PASSWORD)).andExpect(status().isNoContent());

        post("/reset-password", resetJson(token, "terceira-senha-segura")).andExpect(status().isUnprocessableContent());
        login(NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void verificationTokenCannotResetPassword() throws Exception {
        register();

        post("/reset-password", resetJson(tokenFromLastEmail(), NEW_PASSWORD))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void resetRejectsWeakPassword() throws Exception {
        registerAndVerify();
        clearInvocations(emailSender);
        post("/forgot-password", emailJson(EMAIL));

        post("/reset-password", resetJson(tokenFromLastEmail(), "curta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void existingEmailDoesNotTriggerNewMessage() throws Exception {
        register();
        register();

        verify(emailSender, times(1)).send(eq(EMAIL), anyString(), anyString());
    }

    private void register() throws Exception {
        post("/register", """
                {"name":"Ana","email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD)).andExpect(status().isAccepted());
    }

    private void registerAndVerify() throws Exception {
        register();
        post("/verify-email", tokenJson(tokenFromLastEmail())).andExpect(status().isNoContent());
    }

    private ResultActions login(String password) throws Exception {
        return post("/login", """
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, password));
    }

    private ResultActions post(String path, String json) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/auth" + path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String lastEmailBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce()).send(anyString(), anyString(), body.capture());
        return body.getValue();
    }

    private String tokenFromLastEmail() {
        Matcher matcher = TOKEN.matcher(lastEmailBody());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String refreshCookie(String header) {
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private String tokenJson(String token) {
        return "{\"token\":\"%s\"}".formatted(token);
    }

    private String emailJson(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private String resetJson(String token, String password) {
        return "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password);
    }
}
