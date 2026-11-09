package com.finantrack.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.repository.AccountRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Erros que o Spring gera antes de chegar ao serviço, pelos endpoints reais:
 * todos usam o formato RFC 9457 e nenhum vira erro 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorResponsesIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ana;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        problem(postJson(ACCOUNTS, "{\"name\":"), 400);
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        problem(send(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON)), 400);
    }

    @Test
    void unknownEnumInBodyIsBadRequest() throws Exception {
        problem(postJson(ACCOUNTS, "{\"name\":\"X\",\"type\":\"FOO\",\"initialBalance\":1}"), 400);
    }

    @Test
    void textWhereNumberIsExpectedIsBadRequest() throws Exception {
        problem(postJson(ACCOUNTS, "{\"name\":\"X\",\"type\":\"WALLET\",\"initialBalance\":\"abc\"}"), 400);
    }

    @Test
    void invalidIdInPathIsBadRequest() throws Exception {
        problem(send(get(ACCOUNTS + "/nao-e-uuid")), 400);
    }

    @Test
    void invalidFilterValuesAreBadRequest() throws Exception {
        problem(send(get("/api/v1/transactions").param("categoryId", "abc")), 400);
        problem(send(get("/api/v1/transactions").param("from", "2026-13-40")), 400);
        problem(send(get("/api/v1/transactions").param("type", "FOO")), 400);
        problem(send(get("/api/v1/transactions").param("page", "x")), 400);
        problem(send(get("/api/v1/budgets").param("month", "2026-99")), 400);
        problem(send(get("/api/v1/reports/annual").param("year", "abc")), 400);
    }

    @Test
    void missingRequiredParameterIsBadRequest() throws Exception {
        problem(send(get("/api/v1/reports/monthly/export")), 400);
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        problem(send(patch(ACCOUNTS)), 405);
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        problem(send(post(ACCOUNTS).contentType(MediaType.TEXT_PLAIN).content("x")), 415);
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        problem(send(get("/api/v1/nao-existe")), 404);
    }

    @Test
    void rejectedRequestsWriteNothing() throws Exception {
        postJson(ACCOUNTS, "{\"name\":");
        postJson(ACCOUNTS, "{\"name\":\"X\",\"type\":\"FOO\",\"initialBalance\":1}");
        send(post(ACCOUNTS).contentType(MediaType.TEXT_PLAIN).content("x"));

        assertThat(accountRepository.count()).isZero();
    }

    private void problem(ResultActions result, int expectedStatus) throws Exception {
        result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return send(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(ana.toString()))));
    }
}
