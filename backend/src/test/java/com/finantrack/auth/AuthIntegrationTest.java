package com.finantrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.auth.repository.RefreshTokenRepository;
import com.finantrack.auth.service.EmailSender;
import com.finantrack.common.security.JwtConfig;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String EMAIL = "ana@exemplo.com";
    private static final String PASSWORD = "senha-bem-segura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        jdbcTemplate.update("delete from user_tokens");
        userRepository.deleteAll();
    }

    @Test
    void registerStoresNormalizedEmailAndHashedPassword() throws Exception {
        register("Ana", "  ANA@Exemplo.com ", PASSWORD).andExpect(status().isAccepted());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getCurrency()).isEqualTo("BRL");
    }

    @Test
    void registerWithExistingEmailRespondsTheSameAndKeepsOriginalAccount() throws Exception {
        register("Ana", EMAIL, PASSWORD).andExpect(status().isAccepted());
        String originalHash = userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();

        register("Invasor", "ANA@exemplo.com", "outra-senha-qualquer").andExpect(status().isAccepted());

        assertThat(userRepository.count()).isEqualTo(1);
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void registerRejectsInvalidData() throws Exception {
        register("", "nao-e-email", "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerRejectsPasswordAboveBcryptLimit() throws Exception {
        register("Ana", EMAIL, "a".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginReturnsTokenThatAuthenticatesRequests() throws Exception {
        register("Ana", EMAIL, PASSWORD);

        String token = loginAndGetToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void tokenIdentifiesEachUserSeparately() throws Exception {
        register("Ana", EMAIL, PASSWORD);
        register("Bruno", "bruno@exemplo.com", PASSWORD);

        String brunoToken = loginAndGetToken("bruno@exemplo.com", PASSWORD);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + brunoToken))
                .andExpect(jsonPath("$.name").value("Bruno"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetIdenticalResponses() throws Exception {
        register("Ana", EMAIL, PASSWORD);

        String wrongPassword = login(EMAIL, "senha-incorreta")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownEmail = login("ninguem@exemplo.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void protectedRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteRejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer nao-e-um-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteRejectsExpiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String token = encode(jwtEncoder, JwtConfig.ISSUER, past.minusSeconds(900), past);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteRejectsTokenFromAnotherIssuer() throws Exception {
        Instant now = Instant.now();
        String token = encode(jwtEncoder, "outro-emissor", now, now.plusSeconds(900));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteRejectsTokenSignedWithAnotherKey() throws Exception {
        byte[] otherSecret = "outra-chave-com-mais-de-32-bytes-de-tamanho".getBytes(StandardCharsets.UTF_8);
        JwtEncoder otherEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(otherSecret, "HmacSHA256")));
        Instant now = Instant.now();
        String token = encode(otherEncoder, JwtConfig.ISSUER, now, now.plusSeconds(900));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String encode(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Registra e já marca o e-mail como validado; o fluxo de validação tem testes próprios. */
    private ResultActions register(String name, String email, String password) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","email":"%s","password":"%s"}
                        """.formatted(name, email, password)));
        jdbcTemplate.update("update users set email_verified_at = now()");
        return result;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.accessToken");
    }
}
