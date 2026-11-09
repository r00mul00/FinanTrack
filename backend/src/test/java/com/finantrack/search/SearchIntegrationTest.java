package com.finantrack.search;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.finantrack.goal.entity.FinancialGoal;
import com.finantrack.goal.repository.FinancialGoalRepository;
import com.finantrack.recurring.entity.RecurrenceFrequency;
import com.finantrack.recurring.entity.RecurringTransaction;
import com.finantrack.recurring.repository.RecurringTransactionRepository;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.entity.TransactionType;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.math.BigDecimal;
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
class SearchIntegrationTest {

    private static final String URL = "/api/v1/search";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

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
    private FinancialGoalRepository goalRepository;

    @Autowired
    private CreditCardRepository cardRepository;

    @Autowired
    private RecurringTransactionRepository recurringRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ana;
    private UUID bruno;
    private UUID account;
    private UUID category;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
        account = accountRepository.save(new Account(ana, "Corrente", AccountType.CHECKING, new BigDecimal("1000.00"))).getId();
        category = categoryRepository.save(new Category(ana, "Geral", CategoryType.EXPENSE, "Home", "#7C4DFF")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void findsEveryTypeIgnoringCase() throws Exception {
        transaction("Viagem de ônibus");
        categoryRepository.save(new Category(ana, "Viagem", CategoryType.EXPENSE, "Home", "#7C4DFF"));
        goalRepository.save(new FinancialGoal(ana, "Viagem à praia", null, new BigDecimal("500.00"), BigDecimal.ZERO, DATE.plusYears(1)));
        cardRepository.save(new CreditCard(ana, "Cartão viagem", CardBrand.VISA, "1234", new BigDecimal("1000.00"), 15, 25));
        recurringRepository.save(new RecurringTransaction(ana, "Seguro viagem", new BigDecimal("30.00"), TransactionType.EXPENSE,
                category, account, RecurrenceFrequency.MONTHLY, DATE, null));

        search(ana, "VIAGEM")
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("TRANSACTION", "CATEGORY", "GOAL", "CARD", "RECURRING")));
    }

    @Test
    void cardDetailShowsOnlyLastFourDigits() throws Exception {
        cardRepository.save(new CreditCard(ana, "Nubank", CardBrand.VISA, "1234", new BigDecimal("1000.00"), 15, 25));

        search(ana, "nubank").andExpect(jsonPath("$[0].detail").value("Final 1234"));
    }

    @Test
    void limitsToFivePerTypeNewestFirst() throws Exception {
        for (int day = 1; day <= 7; day++) {
            transaction("Mercado", LocalDate.of(2026, 3, day));
        }

        search(ana, "mercado")
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].detail").value("07/03/2026"));
    }

    @Test
    void wildcardsAreTreatedAsPlainText() throws Exception {
        transaction("Mercado");
        transaction("Desconto 10% loja");

        search(ana, "0%").andExpect(jsonPath("$", hasSize(1)));
        search(ana, "__").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shortOrBlankQueryReturnsNothing() throws Exception {
        transaction("Mercado");

        search(ana, "m").andExpect(jsonPath("$", hasSize(0)));
        search(ana, "   ").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void neverReturnsAnotherUsersData() throws Exception {
        transaction("Mercado");

        search(bruno, "mercado").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get(URL).param("q", "mercado")).andExpect(status().isUnauthorized());
    }

    private void transaction(String description) {
        transaction(description, DATE);
    }

    private void transaction(String description, LocalDate date) {
        transactionRepository.save(new Transaction(ana, new Transaction.Details(description, new BigDecimal("10.00"),
                TransactionType.EXPENSE, TransactionStatus.COMPLETED, category, account, date, null, null)));
    }

    private ResultActions search(UUID user, String query) throws Exception {
        return mockMvc.perform(get(URL).param("q", query).with(jwt().jwt(token -> token.subject(user.toString()))))
                .andExpect(status().isOk());
    }
}
