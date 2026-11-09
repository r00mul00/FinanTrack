package com.finantrack.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.card.entity.CardBrand;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.entity.TransactionType;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
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
class ReportIntegrationTest {

    private static final String URL = "/api/v1/reports/";
    private static final String[] MARCH = {"from", "2026-03-01", "to", "2026-03-31"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CreditCardRepository cardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ana;
    private UUID bruno;
    private UUID account;
    private UUID food;
    private UUID home;
    private UUID salary;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
        account = accountRepository.save(new Account(ana, "Corrente", AccountType.CHECKING, new BigDecimal("1000.00"))).getId();
        food = category("Alimentação", CategoryType.EXPENSE);
        home = category("Moradia", CategoryType.EXPENSE);
        salary = category("Salário", CategoryType.INCOME);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    /** Conjunto calculado à mão: receitas 5000,00; despesas 1500,50 (março) e pendente fora. */
    private void seedMarch() {
        tx("Salário", "5000.00", TransactionType.INCOME, TransactionStatus.COMPLETED, salary, "2026-03-05");
        tx("Mercado", "500.50", TransactionType.EXPENSE, TransactionStatus.COMPLETED, food, "2026-03-10");
        tx("Aluguel", "1000.00", TransactionType.EXPENSE, TransactionStatus.COMPLETED, home, "2026-03-15");
        tx("Prevista", "999.00", TransactionType.EXPENSE, TransactionStatus.PENDING, food, "2026-03-20");
        tx("Abril", "77.00", TransactionType.EXPENSE, TransactionStatus.COMPLETED, food, "2026-04-02");
    }

    @Test
    void monthlyHasOneRowPerDayAndHandCheckedTotals() throws Exception {
        seedMarch();

        report("monthly", "month", "2026-03")
                .andExpect(jsonPath("$.rows", hasSize(31)))
                .andExpect(jsonPath("$.rows[9][2]").value("500.50"))
                .andExpect(jsonPath("$.totals[1]").value("5000.00"))
                .andExpect(jsonPath("$.totals[2]").value("1500.50"))
                .andExpect(jsonPath("$.totals[3]").value("3499.50"))
                .andExpect(jsonPath("$.indicators[2].value").value(3499.50));
    }

    @Test
    void annualHasTwelveMonths() throws Exception {
        seedMarch();

        report("annual", "year", "2026")
                .andExpect(jsonPath("$.rows", hasSize(12)))
                .andExpect(jsonPath("$.rows[2][0]").value("03/2026"))
                .andExpect(jsonPath("$.rows[3][2]").value("77.00"))
                .andExpect(jsonPath("$.totals[2]").value("1577.50"));
    }

    @Test
    void byCategoryShowsTotalsAndPercentages() throws Exception {
        seedMarch();

        report("by-category", MARCH)
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0][0]").value("Moradia"))
                .andExpect(jsonPath("$.rows[0][2]").value("66.6"))
                .andExpect(jsonPath("$.rows[1][2]").value("33.4"))
                .andExpect(jsonPath("$.totals[1]").value("1500.50"));
    }

    @Test
    void byPeriodListsOnlyCountedEntries() throws Exception {
        seedMarch();

        report("by-period", MARCH)
                .andExpect(jsonPath("$.rows", hasSize(3)))
                .andExpect(jsonPath("$.rows[0][1]").value("Salário"))
                .andExpect(jsonPath("$.rows[0][3]").value("5000.00"))
                .andExpect(jsonPath("$.rows[0][4]").value(""))
                .andExpect(jsonPath("$.totals[3]").value("5000.00"))
                .andExpect(jsonPath("$.totals[4]").value("1500.50"));
    }

    @Test
    void incomeVsExpenseSwitchesToMonthsOnLongPeriods() throws Exception {
        seedMarch();

        report("income-vs-expense", MARCH).andExpect(jsonPath("$.rows", hasSize(31)));
        report("income-vs-expense", "from", "2026-01-01", "to", "2026-06-30").andExpect(jsonPath("$.rows", hasSize(6)));
    }

    @Test
    void evolutionAccumulatesTheResult() throws Exception {
        seedMarch();

        report("evolution", "from", "2026-03-01", "to", "2026-04-30")
                .andExpect(jsonPath("$.rows[0][4]").value("3499.50"))
                .andExpect(jsonPath("$.rows[1][4]").value("3422.50"))
                .andExpect(jsonPath("$.totals[4]").value("3422.50"));
    }

    @Test
    void byCardUsesInstallmentDatesAndIgnoresCanceled() throws Exception {
        UUID card = cardRepository.save(new CreditCard(ana, "Nubank", CardBrand.VISA, "1234", new BigDecimal("5000.00"), 15, 25)).getId();
        purchase(card, "300.00", 3);
        String canceled = purchase(card, "50.00", 1);
        send(delete("/api/v1/cards/" + card + "/purchases/" + canceled), ana, null).andExpect(status().isNoContent());

        report("by-card", MARCH)
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0][0]").value("Nubank"))
                .andExpect(jsonPath("$.rows[0][1]").value("1"))
                .andExpect(jsonPath("$.rows[0][2]").value("100.00"))
                .andExpect(jsonPath("$.totals[2]").value("100.00"));
    }

    @Test
    void csvHasTheSameTotalsAsTheScreen() throws Exception {
        seedMarch();

        String csv = export("by-category", "csv").andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("relatorio-by-category-2026-03-01-a-2026-03-31.csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Categoria;Total;Percentual").contains("Total;1500,50;100,0");
    }

    @Test
    void pdfHasTheSameTotalsAsTheScreen() throws Exception {
        seedMarch();

        byte[] pdf = export("by-period", "pdf").andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        try (PdfReader reader = new PdfReader(pdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Lançamentos do período", "R$ 5.000,00", "R$ 1.500,50", "R$ 3.499,50");
        }
    }

    @Test
    void csvNeutralizesFormulasAndQuotesSeparators() throws Exception {
        tx("=HYPERLINK(1)", "10.00", TransactionType.EXPENSE, TransactionStatus.COMPLETED, food, "2026-03-05");
        tx("Pão; leite", "10.00", TransactionType.EXPENSE, TransactionStatus.COMPLETED, food, "2026-03-06");

        String csv = export("by-period", "csv").andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains(";'=HYPERLINK(1);", ";\"Pão; leite\";");
    }

    @Test
    void emptyPeriodStillProducesAllFormats() throws Exception {
        report("by-category", MARCH).andExpect(jsonPath("$.rows", hasSize(0))).andExpect(jsonPath("$.totals[1]").value("0.00"));
        export("by-category", "csv").andExpect(status().isOk());
        export("by-category", "pdf").andExpect(status().isOk());
    }

    @Test
    void neverIncludesAnotherUsersData() throws Exception {
        seedMarch();
        String body = report("by-period", bruno, MARCH).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<java.util.List<?>>read(body, "$.rows")).isEmpty();
        report("monthly", bruno, "month", "2026-03").andExpect(jsonPath("$.totals[2]").value("0.00"));
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        get404("desconhecido");
        send(get(URL + "monthly").param("month", "marco"), ana, null).andExpect(status().isBadRequest());
        send(get(URL + "by-period").param("from", "2026-04-01").param("to", "2026-03-01"), ana, null).andExpect(status().isBadRequest());
        send(get(URL + "by-period/export").param("format", "xlsx"), ana, null).andExpect(status().isBadRequest());
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get(URL + "monthly")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "monthly/export").param("format", "csv")).andExpect(status().isUnauthorized());
    }

    private void get404(String type) throws Exception {
        send(get(URL + type), ana, null).andExpect(status().isNotFound());
    }

    private UUID category(String name, CategoryType type) {
        return categoryRepository.save(new Category(ana, name, type, "Home", "#7C4DFF")).getId();
    }

    private void tx(String description, String amount, TransactionType type, TransactionStatus status, UUID category, String date) {
        transactionRepository.save(new Transaction(ana, new Transaction.Details(description, new BigDecimal(amount), type, status,
                category, account, LocalDate.parse(date), null, null)));
    }

    private String purchase(UUID card, String amount, int installments) throws Exception {
        String response = send(post("/api/v1/cards/" + card + "/purchases"), ana, """
                {"description":"Compra","totalAmount":%s,"installments":%d,"categoryId":"%s","purchaseDate":"2026-03-10"}
                """.formatted(amount, installments, food)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions report(String type, String... params) throws Exception {
        return report(type, ana, params);
    }

    private ResultActions report(String type, UUID user, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(URL + type);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return send(request, user, null).andExpect(status().isOk());
    }

    private ResultActions export(String type, String format) throws Exception {
        return send(get(URL + type + "/export").param("format", format).param("from", "2026-03-01").param("to", "2026-03-31"), ana, null)
                .andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(user.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }
}
