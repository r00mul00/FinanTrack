package com.finantrack.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.repository.TransactionRepository;
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
class TransactionIntegrationTest {

    private static final String URL = "/api/v1/transactions";
    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String CATEGORIES = "/api/v1/categories";

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
    private JdbcTemplate jdbcTemplate;

    private UUID ana;
    private UUID bruno;
    private UUID account;
    private UUID food;
    private UUID salary;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
        account = account(ana, "Corrente", "1000.00");
        food = category(ana, "Alimentação", CategoryType.EXPENSE);
        salary = category(ana, "Salário", CategoryType.INCOME);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void createsWithCategoryAndAccountDetails() throws Exception {
        send(post(URL), ana, json("Mercado", "150.75", "EXPENSE", "COMPLETED", food, account, "2026-03-10"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Mercado"))
                .andExpect(jsonPath("$.amount").value(150.75))
                .andExpect(jsonPath("$.kind").value("STANDARD"))
                .andExpect(jsonPath("$.categoryName").value("Alimentação"))
                .andExpect(jsonPath("$.accountName").value("Corrente"))
                .andExpect(jsonPath("$.transactionDate").value("2026-03-10"));
    }

    @Test
    void balanceCountsOnlyCompletedTransactions() throws Exception {
        create("Salário", "500.00", "INCOME", "COMPLETED", salary, "2026-03-01");
        create("Mercado", "120.50", "EXPENSE", "COMPLETED", food, "2026-03-02");
        create("Previsto", "999.00", "INCOME", "PENDING", salary, "2026-03-03");
        create("A pagar", "888.00", "EXPENSE", "PENDING", food, "2026-03-04");
        create("Cancelada", "777.00", "EXPENSE", "CANCELED", food, "2026-03-05");

        balance().andExpect(jsonPath("$.currentBalance").value(1379.50));
        send(get(ACCOUNTS), ana, null).andExpect(jsonPath("$[0].currentBalance").value(1379.50));
    }

    @Test
    void balanceIsExactForDecimalValues() throws Exception {
        create("Dez centavos", "0.10", "INCOME", "COMPLETED", salary, "2026-03-01");
        create("Vinte centavos", "0.20", "INCOME", "COMPLETED", salary, "2026-03-01");

        BigDecimal movement = transactionRepository.sumRealized(account);
        assertThat(movement).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    void balanceMayBecomeNegative() throws Exception {
        create("Grande", "1500.00", "EXPENSE", "COMPLETED", food, "2026-03-01");

        balance().andExpect(jsonPath("$.currentBalance").value(-500.00));
    }

    @Test
    void balanceFollowsUpdateAndDelete() throws Exception {
        UUID id = create("Mercado", "100.00", "EXPENSE", "COMPLETED", food, "2026-03-01");
        balance().andExpect(jsonPath("$.currentBalance").value(900.00));

        send(put(URL + "/" + id), ana, json("Mercado", "300.00", "EXPENSE", "COMPLETED", food, account, "2026-03-01"))
                .andExpect(status().isOk());
        balance().andExpect(jsonPath("$.currentBalance").value(700.00));

        send(delete(URL + "/" + id), ana, null).andExpect(status().isNoContent());
        balance().andExpect(jsonPath("$.currentBalance").value(1000.00));
    }

    @Test
    void completeMovesPendingIntoBalance() throws Exception {
        UUID id = create("Salário", "200.00", "INCOME", "PENDING", salary, "2026-03-01");
        balance().andExpect(jsonPath("$.currentBalance").value(1000.00));

        send(post(URL + "/" + id + "/complete"), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        balance().andExpect(jsonPath("$.currentBalance").value(1200.00));
    }

    @Test
    void canceledCannotBeCompleted() throws Exception {
        UUID id = create("Cancelada", "10.00", "EXPENSE", "CANCELED", food, "2026-03-01");

        send(post(URL + "/" + id + "/complete"), ana, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void anotherUserCannotReadUpdateCompleteOrDelete() throws Exception {
        UUID id = create("Da Ana", "10.00", "EXPENSE", "PENDING", food, "2026-03-01");

        send(get(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(put(URL + "/" + id), bruno, json("X", "1", "EXPENSE", "PENDING", food, account, "2026-03-01"))
                .andExpect(status().isNotFound());
        send(post(URL + "/" + id + "/complete"), bruno, null).andExpect(status().isNotFound());
        send(delete(URL + "/" + id), bruno, null).andExpect(status().isNotFound());

        Transaction stored = transactionRepository.findById(id).orElseThrow();
        assertThat(stored.getDescription()).isEqualTo("Da Ana");
    }

    @Test
    void listNeverShowsOtherUsersTransactions() throws Exception {
        create("Da Ana", "10.00", "EXPENSE", "PENDING", food, "2026-03-01");
        UUID brunoAccount = account(bruno, "Conta B", "0");
        UUID brunoCategory = category(bruno, "Cat B", CategoryType.EXPENSE);
        send(post(URL), bruno, json("Do Bruno", "5.00", "EXPENSE", "PENDING", brunoCategory, brunoAccount, "2026-03-01"))
                .andExpect(status().isCreated());

        send(get(URL), bruno, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Do Bruno"));
    }

    @Test
    void cannotUseAnotherUsersAccountOrCategory() throws Exception {
        UUID brunoAccount = account(bruno, "Conta B", "0");
        UUID brunoCategory = category(bruno, "Cat B", CategoryType.EXPENSE);

        send(post(URL), ana, json("X", "1", "EXPENSE", "PENDING", food, brunoAccount, "2026-03-01"))
                .andExpect(status().isNotFound());
        send(post(URL), ana, json("X", "1", "EXPENSE", "PENDING", brunoCategory, account, "2026-03-01"))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryMustMatchTransactionType() throws Exception {
        send(post(URL), ana, json("X", "1", "EXPENSE", "PENDING", salary, account, "2026-03-01"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void archivedReferencesAreRejectedForNewButKeptOnExisting() throws Exception {
        UUID id = create("Antiga", "10.00", "EXPENSE", "COMPLETED", food, "2026-03-01");
        Category archived = categoryRepository.findById(food).orElseThrow();
        archived.update(archived.getName(), archived.getIcon(), archived.getColor(), true);
        categoryRepository.save(archived);

        send(post(URL), ana, json("Nova", "1", "EXPENSE", "PENDING", food, account, "2026-03-01"))
                .andExpect(status().isUnprocessableContent());
        send(put(URL + "/" + id), ana, json("Antiga editada", "10.00", "EXPENSE", "COMPLETED", food, account, "2026-03-01"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidValues() throws Exception {
        send(post(URL), ana, json("X", "0", "EXPENSE", "PENDING", food, account, "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
        send(post(URL), ana, json("X", "-5.00", "EXPENSE", "PENDING", food, account, "2026-03-01"))
                .andExpect(status().isBadRequest());
        send(post(URL), ana, json("X", "1.999", "EXPENSE", "PENDING", food, account, "2026-03-01"))
                .andExpect(status().isBadRequest());
        send(post(URL), ana, json("  ", "1", "EXPENSE", "PENDING", food, account, "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        UUID id = create("Original", "10.00", "EXPENSE", "PENDING", food, "2026-03-01");
        String withVersion = "{\"description\":\"%s\",\"amount\":10,\"type\":\"EXPENSE\",\"status\":\"PENDING\","
                + "\"categoryId\":\"" + food + "\",\"accountId\":\"" + account
                + "\",\"transactionDate\":\"2026-03-01\",\"version\":0}";

        send(put(URL + "/" + id), ana, withVersion.formatted("Primeira")).andExpect(status().isOk());

        send(put(URL + "/" + id), ana, withVersion.formatted("Segunda")).andExpect(status().isConflict());
        assertThat(transactionRepository.findById(id).orElseThrow().getDescription()).isEqualTo("Primeira");
    }

    @Test
    void filtersCanBeCombined() throws Exception {
        create("Mercado março", "10.00", "EXPENSE", "COMPLETED", food, "2026-03-10");
        create("Mercado abril", "20.00", "EXPENSE", "PENDING", food, "2026-04-10");
        create("Salário março", "30.00", "INCOME", "COMPLETED", salary, "2026-03-05");

        send(get(URL).param("search", "MERCADO"), ana, null).andExpect(jsonPath("$.totalElements").value(2));
        send(get(URL).param("type", "INCOME"), ana, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get(URL).param("status", "PENDING"), ana, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get(URL).param("categoryId", food.toString()), ana, null).andExpect(jsonPath("$.totalElements").value(2));
        send(get(URL).param("from", "2026-03-01").param("to", "2026-03-31"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(2));
        send(get(URL).param("search", "mercado").param("from", "2026-03-01").param("to", "2026-03-31")
                .param("status", "COMPLETED"), ana, null)
                .andExpect(jsonPath("$.content[*].description", contains("Mercado março")));
    }

    @Test
    void periodFilterIncludesBothEnds() throws Exception {
        create("Primeiro dia", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");
        create("Último dia", "1.00", "EXPENSE", "PENDING", food, "2026-03-31");
        create("Fora", "1.00", "EXPENSE", "PENDING", food, "2026-04-01");

        send(get(URL).param("from", "2026-03-01").param("to", "2026-03-31"), ana, null)
                .andExpect(jsonPath("$.content[*].description", containsInAnyOrder("Primeiro dia", "Último dia")));
    }

    @Test
    void searchTreatsWildcardsAsPlainText() throws Exception {
        create("Desconto 10%", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");
        create("Mercado", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");

        send(get(URL).param("search", "%"), ana, null)
                .andExpect(jsonPath("$.content[*].description", contains("Desconto 10%")));
    }

    @Test
    void paginatesWithStableMetadata() throws Exception {
        for (int day = 1; day <= 5; day++) {
            create("Item " + day, "1.00", "EXPENSE", "PENDING", food, "2026-03-0" + day);
        }

        send(get(URL).param("page", "1").param("size", "2"), ana, null)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].description", contains("Item 3", "Item 2")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void sortsByAllowedFieldsAndRejectsOthers() throws Exception {
        create("Barato", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");
        create("Caro", "9.00", "EXPENSE", "PENDING", food, "2026-03-02");

        send(get(URL).param("sort", "amount,desc"), ana, null)
                .andExpect(jsonPath("$.content[*].description", contains("Caro", "Barato")));
        send(get(URL).param("sort", "userId,asc"), ana, null).andExpect(status().isBadRequest());
        send(get(URL).param("sort", "amount,sideways"), ana, null).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidPagingAndPeriod() throws Exception {
        send(get(URL).param("size", "101"), ana, null).andExpect(status().isBadRequest());
        send(get(URL).param("page", "-1"), ana, null).andExpect(status().isBadRequest());
        send(get(URL).param("from", "2026-04-01").param("to", "2026-03-01"), ana, null)
                .andExpect(status().isBadRequest());
        send(get(URL).param("from", "ontem"), ana, null).andExpect(status().isBadRequest());
    }

    @Test
    void accountWithTransactionsCannotBeDeleted() throws Exception {
        create("Mercado", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");

        send(delete(ACCOUNTS + "/" + account), ana, null).andExpect(status().isConflict());

        assertThat(accountRepository.findById(account)).isPresent();
    }

    @Test
    void categoryInUseReportsRealUsageAndBlocksDirectDelete() throws Exception {
        create("Um", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");
        create("Dois", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");

        send(get(CATEGORIES + "/" + food + "/usage"), ana, null).andExpect(jsonPath("$.references").value(2));
        send(delete(CATEGORIES + "/" + food), ana, null).andExpect(status().isConflict());
    }

    @Test
    void deletingCategoryMovesTransactionsToDestination() throws Exception {
        UUID id = create("Um", "1.00", "EXPENSE", "PENDING", food, "2026-03-01");
        UUID target = category(ana, "Destino", CategoryType.EXPENSE);

        send(delete(CATEGORIES + "/" + food).param("reassignTo", target.toString()), ana, null)
                .andExpect(status().isNoContent());

        send(get(URL + "/" + id), ana, null).andExpect(jsonPath("$.categoryName").value("Destino"));
        assertThat(categoryRepository.findById(food)).isEmpty();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private ResultActions balance() throws Exception {
        return send(get(ACCOUNTS + "/" + account), ana, null);
    }

    private UUID account(UUID user, String name, String balance) {
        return accountRepository.save(new Account(user, name, AccountType.CHECKING, new BigDecimal(balance))).getId();
    }

    private UUID category(UUID user, String name, CategoryType type) {
        return categoryRepository.save(new Category(user, name, type, "Home", "#7C4DFF")).getId();
    }

    private UUID create(String description, String amount, String type, String status, UUID category, String date)
            throws Exception {
        String response = send(post(URL), ana, json(description, amount, type, status, category, account, date))
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

    private String json(String description, String amount, String type, String status, UUID category, UUID accountId,
            String date) {
        return """
                {"description":"%s","amount":%s,"type":"%s","status":"%s","categoryId":"%s","accountId":"%s","transactionDate":"%s"}
                """.formatted(description, amount, type, status, category, accountId, date);
    }
}
