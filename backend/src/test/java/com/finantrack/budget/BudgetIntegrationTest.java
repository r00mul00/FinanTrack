package com.finantrack.budget;

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
import com.finantrack.budget.repository.BudgetRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Date;
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
class BudgetIntegrationTest {

    private static final String URL = "/api/v1/budgets";
    private static final String MARCH = "2026-03";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BudgetRepository budgetRepository;

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
        account = accountRepository.save(new Account(ana, "Corrente", AccountType.CHECKING, BigDecimal.ZERO)).getId();
        food = category(ana, "Alimentação", CategoryType.EXPENSE);
        home = category(ana, "Moradia", CategoryType.EXPENSE);
        salary = category(ana, "Salário", CategoryType.INCOME);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void createsAndCalculatesUsage() throws Exception {
        tx("STANDARD", "COMPLETED", "EXPENSE", "30.00", food, "2026-03-05");

        send(post(URL), ana, body(food, MARCH, "100.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("Alimentação"))
                .andExpect(jsonPath("$.month").value(MARCH))
                .andExpect(jsonPath("$.limitAmount").value(100.00))
                .andExpect(jsonPath("$.usedAmount").value(30.00))
                .andExpect(jsonPath("$.remainingAmount").value(70.00))
                .andExpect(jsonPath("$.percentage").value(30.00))
                .andExpect(jsonPath("$.state").value("NORMAL"));
    }

    @Test
    void usageCountsCommittedExpensesOnly() throws Exception {
        create(food, MARCH, "1000.00");
        tx("STANDARD", "COMPLETED", "EXPENSE", "100.00", food, "2026-03-01");
        tx("STANDARD", "PENDING", "EXPENSE", "20.00", food, "2026-03-31");
        tx("CARD_INSTALLMENT", "PENDING", "EXPENSE", "3.00", food, "2026-03-10");
        tx("STANDARD", "CANCELED", "EXPENSE", "500.00", food, "2026-03-10");
        tx("STANDARD", "COMPLETED", "EXPENSE", "400.00", food, "2026-04-01");
        tx("STANDARD", "COMPLETED", "EXPENSE", "300.00", home, "2026-03-10");

        month(MARCH).andExpect(jsonPath("$.items[0].usedAmount").value(123.00));
    }

    @Test
    void invoicePaymentHasNoCategoryAndDoesNotBreakListing() throws Exception {
        create(food, MARCH, "100.00");
        tx("INVOICE_PAYMENT", "COMPLETED", "EXPENSE", "80.00", null, "2026-03-10");

        month(MARCH).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].usedAmount").value(0));
    }

    @Test
    void statesFollowThresholds() throws Exception {
        create(food, MARCH, "100.00");
        UUID homeBudget = create(home, MARCH, "100.00");
        tx("STANDARD", "COMPLETED", "EXPENSE", "79.99", food, "2026-03-02");
        tx("STANDARD", "COMPLETED", "EXPENSE", "100.01", home, "2026-03-02");

        month(MARCH)
                .andExpect(jsonPath("$.items[0].state").value("NORMAL"))
                .andExpect(jsonPath("$.items[0].percentage").value(79.99))
                .andExpect(jsonPath("$.items[1].state").value("EXCEEDED"))
                .andExpect(jsonPath("$.items[1].remainingAmount").value(-0.01))
                .andExpect(jsonPath("$.totalLimit").value(200.00))
                .andExpect(jsonPath("$.totalUsed").value(180.00));

        send(put(URL + "/" + homeBudget), ana, "{\"limitAmount\":125.01}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WARNING"));
    }

    @Test
    void oneBudgetPerCategoryAndMonth() throws Exception {
        create(food, MARCH, "100.00");

        send(post(URL), ana, body(food, MARCH, "50.00")).andExpect(status().isConflict());
        send(post(URL), ana, body(food, "2026-04", "50.00")).andExpect(status().isCreated());
    }

    @Test
    void onlyActiveExpenseCategoriesAreAccepted() throws Exception {
        send(post(URL), ana, body(salary, MARCH, "50.00")).andExpect(status().isUnprocessableContent());

        Category archived = categoryRepository.findById(home).orElseThrow();
        archived.update(archived.getName(), archived.getIcon(), archived.getColor(), true);
        categoryRepository.save(archived);
        send(post(URL), ana, body(home, MARCH, "50.00")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsInvalidValues() throws Exception {
        send(post(URL), ana, body(food, MARCH, "0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.limitAmount").exists());
        send(post(URL), ana, body(food, MARCH, "-1.00")).andExpect(status().isBadRequest());
        send(post(URL), ana, body(food, MARCH, "1.999")).andExpect(status().isBadRequest());
        send(post(URL), ana, body(food, "2026-13", "1.00")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.month").exists());
        send(get(URL).param("month", "marco"), ana, null).andExpect(status().isBadRequest());
    }

    @Test
    void anotherUserCannotSeeUpdateDeleteOrUseCategory() throws Exception {
        UUID id = create(food, MARCH, "100.00");

        send(put(URL + "/" + id), bruno, "{\"limitAmount\":1}").andExpect(status().isNotFound());
        send(delete(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(post(URL), bruno, body(food, MARCH, "1")).andExpect(status().isNotFound());
        send(get(URL).param("month", MARCH), bruno, null).andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(budgetRepository.findById(id)).isPresent();
    }

    @Test
    void deletesBudget() throws Exception {
        UUID id = create(food, MARCH, "100.00");

        send(delete(URL + "/" + id), ana, null).andExpect(status().isNoContent());

        assertThat(budgetRepository.findById(id)).isEmpty();
    }

    @Test
    void budgetCountsAsCategoryUsage() throws Exception {
        create(food, MARCH, "100.00");

        send(get("/api/v1/categories/" + food + "/usage"), ana, null).andExpect(jsonPath("$.references").value(1));
        send(delete("/api/v1/categories/" + food), ana, null).andExpect(status().isConflict());
    }

    @Test
    void deletingCategoryMovesBudgetsAndDropsConflictingMonths() throws Exception {
        create(food, MARCH, "100.00");
        create(food, "2026-04", "200.00");
        UUID keptAtDestination = create(home, MARCH, "999.00");

        send(delete("/api/v1/categories/" + food).param("reassignTo", home.toString()), ana, null)
                .andExpect(status().isNoContent());

        month(MARCH)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(keptAtDestination.toString()))
                .andExpect(jsonPath("$.items[0].limitAmount").value(999.00));
        month("2026-04")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].categoryName").value("Moradia"))
                .andExpect(jsonPath("$.items[0].limitAmount").value(200.00));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private ResultActions month(String month) throws Exception {
        return send(get(URL).param("month", month), ana, null);
    }

    private UUID category(UUID user, String name, CategoryType type) {
        return categoryRepository.save(new Category(user, name, type, "Home", "#7C4DFF")).getId();
    }

    private UUID create(UUID category, String month, String limit) throws Exception {
        String response = send(post(URL), ana, body(category, month, limit))
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

    private String body(UUID category, String month, String limit) {
        return """
                {"categoryId":"%s","month":"%s","limitAmount":%s}
                """.formatted(category, month, limit);
    }

    /** Parcelas e pagamentos de fatura só nascem na Fase 6, por isso a gravação direta. */
    private void tx(String kind, String status, String type, String amount, UUID category, String date) {
        UUID accountId = "CARD_INSTALLMENT".equals(kind) ? null : account;
        jdbcTemplate.update("""
                insert into transactions (id, user_id, description, amount, type, kind, status, category_id, account_id,
                                          transaction_date, created_at, updated_at)
                values (?, ?, 'teste', ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, UUID.randomUUID(), ana, new BigDecimal(amount), type, kind, status, category, accountId,
                Date.valueOf(date));
    }
}
