package com.finantrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.auth.controller.RefreshTokenCookie;
import com.finantrack.auth.repository.RefreshTokenRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenIntegrationTest {

    private static final String EMAIL = "ana@exemplo.com";
    private static final String PASSWORD = "senha-bem-segura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("Ana", EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    @Test
    void loginSetsProtectedCookieAndKeepsTokenOutOfBody() throws Exception {
        MockHttpServletResponse response = login();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(RefreshTokenCookie.NAME + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(response.getContentAsString()).doesNotContain(cookieValue(response));
    }

    @Test
    void onlyTheHashIsStored() throws Exception {
        String raw = cookieValue(login());

        String stored = jdbcTemplate.queryForObject("select token_hash from refresh_tokens", String.class);

        assertThat(stored).hasSize(64).isNotEqualTo(raw);
    }

    @Test
    void refreshIssuesWorkingTokenAndRotatesCookie() throws Exception {
        String first = cookieValue(login());

        MockHttpServletResponse response = refresh(first).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(cookieValue(response)).isNotEqualTo(first);
        String accessToken = JsonPath.read(response.getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void refreshIgnoresExpiredBearerHeader() throws Exception {
        String token = cookieValue(login());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(RefreshTokenCookie.NAME, token))
                        .header("Authorization", "Bearer token-expirado"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithUnknownTokenIsRejectedAndClearsCookie() throws Exception {
        String setCookie = refresh("token-inventado")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("Max-Age=0");
    }

    @Test
    void reuseInsideGraceWindowReturnsConflictAndKeepsSession() throws Exception {
        String first = cookieValue(login());
        String second = cookieValue(refresh(first).andReturn().getResponse());

        refresh(first).andExpect(status().isConflict());

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void reuseAfterGraceWindowRevokesWholeFamily() throws Exception {
        String first = cookieValue(login());
        String second = cookieValue(refresh(first).andReturn().getResponse());
        jdbcTemplate.update("update refresh_tokens set rotated_at = rotated_at - interval '1 minute'"
                + " where rotated_at is not null");

        refresh(first).andExpect(status().isUnauthorized());

        refresh(second).andExpect(status().isUnauthorized());
        Integer active = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where revoked_at is null", Integer.class);
        assertThat(active).isZero();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = cookieValue(login());
        jdbcTemplate.update("update refresh_tokens set expires_at = now() - interval '1 second'");

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshesProduceExactlyOneRotation() throws Exception {
        String token = cookieValue(login());
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            start.await();
            return refresh(token).andReturn().getResponse().getStatus();
        };
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(task));
        }
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        executor.shutdown();

        assertThat(statuses).containsOnly(200, 409);
        assertThat(statuses.stream().filter(status -> status == 200)).hasSize(1);
    }

    @Test
    void logoutRevokesSessionAndClearsCookie() throws Exception {
        String first = cookieValue(login());
        String second = cookieValue(refresh(first).andReturn().getResponse());

        String setCookie = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(RefreshTokenCookie.NAME, second)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("Max-Age=0");
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookieIsHarmless() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void sessionsAreIndependent() throws Exception {
        String sessionA = cookieValue(login());
        String sessionB = cookieValue(login());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, sessionA)))
                .andExpect(status().isNoContent());

        refresh(sessionB).andExpect(status().isOk());
    }

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, token)));
    }

    private String cookieValue(MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }
}
