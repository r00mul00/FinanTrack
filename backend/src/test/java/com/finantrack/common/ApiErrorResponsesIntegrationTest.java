package com.finantrack.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.repository.AccountRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * todos usam o formato RFC 9457, em português, sem nomes internos e sem virar 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorResponsesIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";

    /** Trechos que indicariam vazamento de implementação ou mensagem padrão em inglês. */
    private static final List<String> LEAKS = List.of(
            "java.", "com.finantrack", "org.springframework", "tools.jackson", "Exception",
            "Failed", "not supported", "Required", "No static resource");

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
        problem(postJson(ACCOUNTS, "{\"name\":"), 400, "O corpo da requisição está ausente ou mal formatado.");
    }

    @Test
    void missingBodyIsBadRequest() throws Exception {
        problem(send(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON)), 400,
                "O corpo da requisição está ausente ou mal formatado.");
    }

    @Test
    void unknownEnumInBodyIsBadRequest() throws Exception {
        problem(postJson(ACCOUNTS, "{\"name\":\"X\",\"type\":\"FOO\",\"initialBalance\":1}"), 400,
                "O corpo da requisição está ausente ou mal formatado.");
    }

    @Test
    void textWhereNumberIsExpectedIsBadRequest() throws Exception {
        problem(postJson(ACCOUNTS, "{\"name\":\"X\",\"type\":\"WALLET\",\"initialBalance\":\"abc\"}"), 400,
                "O corpo da requisição está ausente ou mal formatado.");
    }

    @Test
    void invalidIdInPathNamesTheParameterWithoutEchoingTheValue() throws Exception {
        problem(send(get(ACCOUNTS + "/nao-e-uuid")), 400, "O valor informado para \"id\" é inválido.");
    }

    @Test
    void invalidRequestParametersNameTheParameter() throws Exception {
        problem(send(get("/api/v1/transactions").param("page", "x")), 400, "O valor informado para \"page\" é inválido.");
        problem(send(get("/api/v1/budgets").param("month", "2026-99")), 400, "O valor informado para \"month\" é inválido.");
        problem(send(get("/api/v1/reports/annual").param("year", "abc")), 400, "O valor informado para \"year\" é inválido.");
    }

    @Test
    void invalidFilterValuesAreReportedPerField() throws Exception {
        problem(send(get("/api/v1/transactions")
                        .param("categoryId", "abc")
                        .param("from", "2026-13-40")
                        .param("type", "FOO")), 400, "Existem campos inválidos.")
                .andExpect(jsonPath("$.errors.categoryId").value("Valor inválido."))
                .andExpect(jsonPath("$.errors.from").value("Valor inválido."))
                .andExpect(jsonPath("$.errors.type").value("Valor inválido."));
    }

    @Test
    void validationMessagesStayAsWritten() throws Exception {
        problem(postJson(ACCOUNTS, "{\"name\":\"  \",\"type\":\"WALLET\",\"initialBalance\":1}"), 400,
                "Existem campos inválidos.")
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.name").value(not("Valor inválido.")));
    }

    @Test
    void missingRequiredParameterIsBadRequest() throws Exception {
        problem(send(get("/api/v1/reports/monthly/export")), 400, "Informe o parâmetro \"format\".");
    }

    @Test
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        problem(send(patch(ACCOUNTS)), 405, "Método HTTP não permitido neste endereço.")
                .andExpect(jsonPath("$.title").value("Método não permitido"));
    }

    @Test
    void unsupportedContentTypeIsUnsupportedMediaType() throws Exception {
        problem(send(post(ACCOUNTS).contentType(MediaType.TEXT_PLAIN).content("x")), 415,
                "Formato de conteúdo não suportado. Envie JSON.");
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        problem(send(get("/api/v1/nao-existe")), 404, "Endereço não encontrado.")
                .andExpect(jsonPath("$.title").value("Não encontrado"));
    }

    @Test
    void ownErrorsUsePortugueseTitles() throws Exception {
        problem(send(get(ACCOUNTS + "/" + UUID.randomUUID())), 404, "Conta não encontrada.")
                .andExpect(jsonPath("$.title").value("Não encontrado"));
    }

    @Test
    void missingTokenIsUnauthorizedProblem() throws Exception {
        problem(mockMvc.perform(get(ACCOUNTS)), 401, "Sessão inválida ou expirada. Entre novamente.")
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.instance").value(ACCOUNTS))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void invalidTokenIsUnauthorizedProblem() throws Exception {
        problem(mockMvc.perform(get(ACCOUNTS).header("Authorization", "Bearer nao-e-um-token")), 401,
                "Sessão inválida ou expirada. Entre novamente.");
    }

    @Test
    void rejectedRequestsWriteNothing() throws Exception {
        postJson(ACCOUNTS, "{\"name\":");
        postJson(ACCOUNTS, "{\"name\":\"X\",\"type\":\"FOO\",\"initialBalance\":1}");
        send(post(ACCOUNTS).contentType(MediaType.TEXT_PLAIN).content("x"));

        assertThat(accountRepository.count()).isZero();
    }

    private ResultActions problem(ResultActions result, int expectedStatus, String expectedDetail) throws Exception {
        result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(expectedDetail));

        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(LEAKS).noneMatch(body::contains);
        return result;
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return send(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(ana.toString()))));
    }
}
