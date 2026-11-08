package com.finantrack.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTest {

    private static final String URL = "/api/v1/accounts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void createsAccountKeepingExactMoneyValue() throws Exception {
        send(post(URL), ana, body("Nubank", "CHECKING", "1234.56"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nubank"))
                .andExpect(jsonPath("$.currentBalance").value(1234.56))
                .andExpect(jsonPath("$.archived").value(false));

        Account saved = accountRepository.findAllByUserIdOrderByNameAsc(ana).getFirst();
        assertThat(saved.getInitialBalance()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void listsOnlyOwnAccountsSortedByName() throws Exception {
        accountRepository.save(new Account(ana, "Zeta", AccountType.WALLET, BigDecimal.ZERO));
        accountRepository.save(new Account(ana, "Alfa", AccountType.SAVINGS, BigDecimal.TEN));
        accountRepository.save(new Account(bruno, "Conta do Bruno", AccountType.CHECKING, BigDecimal.ONE));

        send(get(URL), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alfa"))
                .andExpect(jsonPath("$[1].name").value("Zeta"));
    }

    @Test
    void updatesAndArchives() throws Exception {
        UUID id = create(ana, "Antiga");

        send(put(URL + "/" + id), ana, """
                {"name":"Nova","type":"SAVINGS","initialBalance":50.10,"archived":true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nova"))
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void deletesAccount() throws Exception {
        UUID id = create(ana, "Temporária");

        send(delete(URL + "/" + id), ana, null).andExpect(status().isNoContent());

        send(get(URL + "/" + id), ana, null).andExpect(status().isNotFound());
    }

    @Test
    void anotherUserCannotReadUpdateOrDelete() throws Exception {
        UUID id = create(ana, "Da Ana");

        send(get(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(put(URL + "/" + id), bruno, body("Invadida", "WALLET", "1")).andExpect(status().isNotFound());
        send(delete(URL + "/" + id), bruno, null).andExpect(status().isNotFound());

        send(get(URL + "/" + id), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Da Ana"));
    }

    @Test
    void duplicateNameIgnoringCaseIsRejected() throws Exception {
        create(ana, "Nubank");

        send(post(URL), ana, body("  NUBANK ", "WALLET", "0")).andExpect(status().isConflict());
    }

    @Test
    void renamingOntoAnotherAccountIsRejectedButKeepingOwnNameIsAllowed() throws Exception {
        create(ana, "Primeira");
        UUID second = create(ana, "Segunda");

        send(put(URL + "/" + second), ana, body("primeira", "WALLET", "0")).andExpect(status().isConflict());
        send(put(URL + "/" + second), ana, body("SEGUNDA", "WALLET", "0")).andExpect(status().isOk());
    }

    @Test
    void sameNameIsAllowedForDifferentUsers() throws Exception {
        create(ana, "Carteira");

        send(post(URL), bruno, body("Carteira", "WALLET", "0")).andExpect(status().isCreated());
    }

    @Test
    void rejectsNegativeBalance() throws Exception {
        send(post(URL), ana, body("Conta", "CHECKING", "-0.01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialBalance").exists());
    }

    @Test
    void rejectsMoreThanTwoDecimals() throws Exception {
        send(post(URL), ana, body("Conta", "CHECKING", "1.234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.initialBalance").exists());
    }

    @Test
    void rejectsBlankNameAndUnknownType() throws Exception {
        send(post(URL), ana, body("   ", "CHECKING", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        send(post(URL), ana, body("Conta", "POUPANCA_X", "1")).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private UUID create(UUID user, String name) throws Exception {
        String response = send(post(URL), user, body(name, "CHECKING", "100"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(user.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String body(String name, String type, String balance) {
        return """
                {"name":"%s","type":"%s","initialBalance":%s}
                """.formatted(name, type, balance);
    }
}
