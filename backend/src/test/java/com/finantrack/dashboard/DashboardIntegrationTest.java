package com.finantrack.dashboard;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {

    private static final String URL = "/api/v1/dashboard";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
        account = account(ana, "Corrente", "1000.00");
        food = category(ana, "Alimentação", CategoryType.EXPENSE, "#FF8A65");
        home = category(ana, "Moradia", CategoryType.EXPENSE, "#7C4DFF");
        salary = category(ana, "Salário", CategoryType.INCOME, "#20C997");
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void summarizesPeriodAndComparesWithPreviousMonth() throws Exception {
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "5000.00", salary, account, "2026-03-05");
        tx(ana, "EXPENSE", "STANDARD", "COMPLETED", "1500.50", food, account, "2026-03-10");
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "4000.00", salary, account, "2026-02-05");
        tx(ana, "EXPENSE", "STANDARD", "COMPLETED", "2000.00", food, account, "2026-02-10");

        summary("2026-03-01", "2026-03-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(5000.00))
                .andExpect(jsonPath("$.expenses").value(1500.50))
                .andExpect(jsonPath("$.result").value(3499.50))
                .andExpect(jsonPath("$.previousFrom").value("2026-02-01"))
                .andExpect(jsonPath("$.previousTo").value("2026-02-28"))
                .andExpect(jsonPath("$.previousIncome").value(4000.00))
                .andExpect(jsonPath("$.previousExpenses").value(2000.00))
                .andExpect(jsonPath("$.previousResult").value(2000.00))
                .andExpect(jsonPath("$.incomeChange").value(25.0))
                .andExpect(jsonPath("$.expensesChange").value(-25.0))
                .andExpect(jsonPath("$.resultChange").value(75.0));
    }

    @Test
    void pendingAndCanceledDoNotCount() throws Exception {
        tx(ana, "INCOME", "STANDARD", "PENDING", "999.00", salary, account, "2026-03-05");
        tx(ana, "EXPENSE", "STANDARD", "PENDING", "888.00", food, account, "2026-03-05");
        tx(ana, "EXPENSE", "STANDARD", "CANCELED", "777.00", food, account, "2026-03-05");

        summary("2026-03-01", "2026-03-31")
                .andExpect(jsonPath("$.income").value(0))
                .andExpect(jsonPath("$.expenses").value(0))
                .andExpect(jsonPath("$.totalBalance").value(1000.00));
    }

    @Test
    void cardPurchaseCountsOnceAndInvoicePaymentOnlyAffectsBalance() throws Exception {
        tx(ana, "EXPENSE", "CARD_INSTALLMENT", "PENDING", "300.00", food, null, "2026-03-05");
        tx(ana, "EXPENSE", "CARD_INSTALLMENT", "CANCELED", "50.00", food, null, "2026-03-06");
        tx(ana, "EXPENSE", "INVOICE_PAYMENT", "COMPLETED", "300.00", null, account, "2026-03-20");

        summary("2026-03-01", "2026-03-31")
                .andExpect(jsonPath("$.expenses").value(300.00))
                .andExpect(jsonPath("$.result").value(-300.00))
                .andExpect(jsonPath("$.totalBalance").value(700.00));
        categories("2026-03-01", "2026-03-31")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(300.00));
    }

    @Test
    void totalBalanceIgnoresPeriodAndArchivedAccounts() throws Exception {
        UUID archived = account(ana, "Antiga", "500.00");
        Account entity = accountRepository.findById(archived).orElseThrow();
        entity.update(entity.getName(), entity.getType(), entity.getInitialBalance(), true);
        accountRepository.save(entity);
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "100.00", salary, account, "2020-01-01");

        summary("2026-03-01", "2026-03-31").andExpect(jsonPath("$.totalBalance").value(1100.00));
    }

    @Test
    void changeIsNullWithoutPreviousValueAndHandlesNegativeBase() throws Exception {
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "100.00", salary, account, "2026-03-05");
        summary("2026-03-01", "2026-03-31")
                .andExpect(jsonPath("$.incomeChange").value(nullValue()))
                .andExpect(jsonPath("$.resultChange").value(nullValue()));

        tx(ana, "EXPENSE", "STANDARD", "COMPLETED", "200.00", food, account, "2026-02-05");
        // Resultado foi de -200 para +100: melhora de 150%, não piora.
        summary("2026-03-01", "2026-03-31").andExpect(jsonPath("$.resultChange").value(150.0));
    }

    @Test
    void defaultsToCurrentMonth() throws Exception {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "42.00", salary, account, today.toString());

        send(URL + "/summary")
                .andExpect(jsonPath("$.income").value(42.00))
                .andExpect(jsonPath("$.from").value(today.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.to").value(today.withDayOfMonth(today.lengthOfMonth()).toString()));
    }

    @Test
    void periodBoundariesAreInclusive() throws Exception {
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "1.00", salary, account, "2026-03-01");
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "2.00", salary, account, "2026-03-31");
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "4.00", salary, account, "2026-04-01");

        summary("2026-03-01", "2026-03-31").andExpect(jsonPath("$.income").value(3.00));
    }

    @Test
    void dailySeriesFillsDaysWithoutMovement() throws Exception {
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "10.00", salary, account, "2026-03-02");
        tx(ana, "EXPENSE", "STANDARD", "COMPLETED", "4.00", food, account, "2026-03-02");
        tx(ana, "EXPENSE", "STANDARD", "PENDING", "99.00", food, account, "2026-03-02");

        series("2026-03-01", "2026-03-03")
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.points", hasSize(3)))
                .andExpect(jsonPath("$.points[0].date").value("2026-03-01"))
                .andExpect(jsonPath("$.points[0].income").value(0))
                .andExpect(jsonPath("$.points[1].income").value(10.00))
                .andExpect(jsonPath("$.points[1].expenses").value(4.00))
                .andExpect(jsonPath("$.points[2].expenses").value(0));
    }

    @Test
    void longPeriodIsGroupedByMonth() throws Exception {
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "10.00", salary, account, "2026-01-02");
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "5.00", salary, account, "2026-01-31");
        tx(ana, "EXPENSE", "STANDARD", "COMPLETED", "7.00", food, account, "2026-03-15");

        series("2026-01-01", "2026-04-30")
                .andExpect(jsonPath("$.granularity").value("MONTH"))
                .andExpect(jsonPath("$.points", hasSize(4)))
                .andExpect(jsonPath("$.points[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$.points[0].income").value(15.00))
                .andExpect(jsonPath("$.points[1].income").value(0))
                .andExpect(jsonPath("$.points[2].expenses").value(7.00));
    }

    @Test
    void expensesByCategoryAreSortedWithPercentages() throws Exception {
        tx(ana, "EXPENSE", "STANDARD", "COMPLETED", "25.00", food, account, "2026-03-05");
        tx(ana, "EXPENSE", "STANDARD", "COMPLETED", "75.00", home, account, "2026-03-06");
        tx(ana, "INCOME", "STANDARD", "COMPLETED", "500.00", salary, account, "2026-03-06");

        categories("2026-03-01", "2026-03-31")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Moradia"))
                .andExpect(jsonPath("$[0].amount").value(75.00))
                .andExpect(jsonPath("$[0].percentage").value(75.0))
                .andExpect(jsonPath("$[0].color").value("#7C4DFF"))
                .andExpect(jsonPath("$[1].percentage").value(25.0));
    }

    @Test
    void noExpensesReturnsEmptyListWithoutDividingByZero() throws Exception {
        categories("2026-03-01", "2026-03-31").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void neverIncludesOtherUsersData() throws Exception {
        UUID brunoAccount = account(bruno, "Conta B", "9000.00");
        UUID brunoCategory = category(bruno, "Cat B", CategoryType.EXPENSE, "#000000");
        tx(bruno, "EXPENSE", "STANDARD", "COMPLETED", "800.00", brunoCategory, brunoAccount, "2026-03-05");

        summary("2026-03-01", "2026-03-31")
                .andExpect(jsonPath("$.expenses").value(0))
                .andExpect(jsonPath("$.totalBalance").value(1000.00));
        categories("2026-03-01", "2026-03-31").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsInvalidPeriods() throws Exception {
        summary("2026-03-31", "2026-03-01").andExpect(status().isBadRequest());
        send(URL + "/summary?from=2026-03-01").andExpect(status().isBadRequest());
        send(URL + "/summary?from=ontem&to=hoje").andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL + "/summary")).andExpect(status().isUnauthorized());
    }

    private ResultActions summary(String from, String to) throws Exception {
        return send(URL + "/summary?from=" + from + "&to=" + to);
    }

    private ResultActions series(String from, String to) throws Exception {
        return send(URL + "/income-vs-expense?from=" + from + "&to=" + to);
    }

    private ResultActions categories(String from, String to) throws Exception {
        return send(URL + "/expenses-by-category?from=" + from + "&to=" + to);
    }

    private ResultActions send(String url) throws Exception {
        return mockMvc.perform(get(url).with(jwt().jwt(token -> token.subject(ana.toString()))));
    }

    private UUID account(UUID user, String name, String balance) {
        return accountRepository.save(new Account(user, name, AccountType.CHECKING, new BigDecimal(balance))).getId();
    }

    private UUID category(UUID user, String name, CategoryType type, String color) {
        return categoryRepository.save(new Category(user, name, type, "Home", color)).getId();
    }

    /** Grava direto no banco para cobrir parcelas e pagamentos de fatura, criados só na Fase 6. */
    private void tx(UUID user, String type, String kind, String status, String amount, UUID categoryId, UUID accountId,
            String date) {
        jdbcTemplate.update("""
                insert into transactions (id, user_id, description, amount, type, kind, status, category_id, account_id,
                                          transaction_date, created_at, updated_at)
                values (?, ?, 'teste', ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, UUID.randomUUID(), user, new BigDecimal(amount), type, kind, status, categoryId, accountId,
                Date.valueOf(date));
    }
}
