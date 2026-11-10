package com.finantrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.auth.service.EmailSender;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "finantrack.rate-limit.ip-limit=4",
        "finantrack.rate-limit.email-limit=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    private static final String PASSWORD = "senha-bem-segura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void emailLimitBlocksAttemptsEvenFromDifferentAddresses() throws Exception {
        login("alvo@exemplo.com", "10.0.0.1").andExpect(status().isUnauthorized());
        login("alvo@exemplo.com", "10.0.0.2").andExpect(status().isUnauthorized());

        login("ALVO@exemplo.com", "10.0.0.3").andExpect(status().isTooManyRequests());
    }

    @Test
    void ipLimitBlocksAttemptsAcrossDifferentEmails() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("pessoa" + i + "@exemplo.com", "10.0.0.9").andExpect(status().isUnauthorized());
        }

        login("outra@exemplo.com", "10.0.0.9").andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordConfirmingRoutesShareOneLimitPerAccount() throws Exception {
        UUID ana = user("ana@exemplo.com");
        UUID bruno = user("bruno@exemplo.com");

        // Alternar entre as rotas não dá mais tentativas.
        changePassword(ana, "errada-errada").andExpect(status().isUnprocessableContent());
        authenticated(post("/api/v1/users/me/deletion-request"), ana, "{\"currentPassword\":\"errada-errada\"}")
                .andExpect(status().isUnprocessableContent());

        // Bloqueia mesmo com a senha certa: o atacante não descobre se acertou.
        authenticated(post("/api/v1/users/me/email-change"), ana,
                "{\"newEmail\":\"nova@exemplo.com\",\"currentPassword\":\"%s\"}".formatted(PASSWORD))
                .andExpect(status().isTooManyRequests());
        changePassword(ana, PASSWORD).andExpect(status().isTooManyRequests());

        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(ana).orElseThrow().getPasswordHash()))
                .isTrue();
        changePassword(bruno, PASSWORD).andExpect(status().isNoContent());
    }

    private UUID user(String email) {
        return userRepository.save(new User("Pessoa", email, passwordEncoder.encode(PASSWORD))).getId();
    }

    private ResultActions changePassword(UUID user, String current) throws Exception {
        return authenticated(put("/api/v1/users/me/password"), user,
                "{\"currentPassword\":\"%s\",\"newPassword\":\"outra-senha-segura\"}".formatted(current));
    }

    private ResultActions authenticated(MockHttpServletRequestBuilder request, UUID user, String json)
            throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions login(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"senha-qualquer\"}".formatted(email)));
    }
}
