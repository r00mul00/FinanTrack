package com.finantrack.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.finantrack.recurring.repository.RecurringTransactionRepository;
import com.finantrack.recurring.service.RecurringJob;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecurringIntegrationTest {

    private static final String URL = "/api/v1/recurring";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

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
    private RecurringTransactionRepository recurringRepository;

    @Autowired
    private RecurringJob job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ana;
    private UUID bruno;
    private UUID account;
    private UUID rent;
    private UUID salary;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
        account = accountRepository.save(new Account(ana, "Corrente", AccountType.CHECKING, new BigDecimal("1000.00"))).getId();
        rent = category(ana, "Moradia", CategoryType.EXPENSE);
        salary = category(ana, "Salário", CategoryType.INCOME);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void createWithPastStartGeneratesDueCyclesAsPending() throws Exception {
        LocalDate start = TODAY.minusMonths(2);

        send(post(URL), ana, body("Aluguel", "EXPENSE", rent, "MONTHLY", start, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value(start.plusMonths(3).toString()));

        send(get("/api/v1/transactions").param("sort", "transactionDate,asc"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].dueDate").value(start.toString()))
                .andExpect(jsonPath("$.content[0].recurring").value(true));
        send(get("/api/v1/accounts/" + account), ana, null).andExpect(jsonPath("$.currentBalance").value(1000.00));
    }

    @Test
    void futureStartGeneratesNothingUntilItsDate() throws Exception {
        LocalDate start = TODAY.plusDays(10);
        create("Futura", start, null);

        assertThat(transactionRepository.count()).isZero();

        job.run(start.minusDays(1));
        assertThat(transactionRepository.count()).isZero();

        job.run(start);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void runningJobRepeatedlyNeverDuplicates() throws Exception {
        LocalDate start = TODAY.plusDays(1);
        create("Conta", start, null);

        job.run(start.plusMonths(3));
        job.run(start.plusMonths(3));
        job.run(start.plusMonths(3));

        assertThat(transactionRepository.count()).isEqualTo(4);
    }

    @Test
    void concurrentJobRunsNeverDuplicate() throws Exception {
        LocalDate start = TODAY.plusDays(1);
        create("Conta", start, null);
        LocalDate runDate = start.plusMonths(5);

        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(executor.submit(() -> {
                go.await();
                job.run(runDate);
                return null;
            }));
        }
        go.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(transactionRepository.count()).isEqualTo(6);
    }

    @Test
    void databaseRejectsDuplicateCycleEvenWithoutTheCursor() throws Exception {
        UUID id = create("Conta", TODAY, null);
        String sql = """
                insert into transactions (id, user_id, description, amount, type, kind, status, category_id, account_id,
                                          transaction_date, recurring_id, occurrence_date, created_at, updated_at)
                values (?, ?, 'dup', 1, 'EXPENSE', 'STANDARD', 'PENDING', ?, ?, ?, ?, ?, now(), now())
                """;

        assertThatThrownBy(() -> jdbcTemplate.update(sql, UUID.randomUUID(), ana, rent, account, Date.valueOf(TODAY), id, Date.valueOf(TODAY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletedOccurrenceIsNotRecreated() throws Exception {
        create("Conta", TODAY, null);
        String list = send(get("/api/v1/transactions"), ana, null).andReturn().getResponse().getContentAsString();
        String txId = JsonPath.read(list, "$.content[0].id");

        send(delete("/api/v1/transactions/" + txId), ana, null).andExpect(status().isNoContent());
        job.run(TODAY);

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void endDateFinishesRecurrence() throws Exception {
        LocalDate start = TODAY.minusMonths(3);
        UUID id = create("Curta", start, start.plusMonths(1));

        send(get(URL + "/" + id), ana, null)
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.nextOccurrenceDate").doesNotExist());
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void pausedRecurrenceGeneratesNothingAndResumeHasNoRetroactives() throws Exception {
        LocalDate start = TODAY.minusMonths(4);
        UUID id = create("Pausável", start, null);
        long before = transactionRepository.count();

        send(post(URL + "/" + id + "/pause"), ana, null).andExpect(jsonPath("$.status").value("PAUSED"));
        // Simula um longo período pausado.
        jdbcTemplate.update("update recurring_transactions set next_occurrence_date = ? where id = ?",
                Date.valueOf(TODAY.minusMonths(2)), id);
        job.run(TODAY.plusMonths(6));
        assertThat(transactionRepository.count()).isEqualTo(before);

        send(post(URL + "/" + id + "/resume"), ana, null).andExpect(jsonPath("$.status").value("ACTIVE"));

        String json = send(get(URL + "/" + id), ana, null).andReturn().getResponse().getContentAsString();
        assertThat(LocalDate.parse(JsonPath.<String>read(json, "$.nextOccurrenceDate"))).isAfterOrEqualTo(TODAY);
        assertThat(transactionRepository.count()).isBetween(before, before + 1);
    }

    @Test
    void upcomingListsFutureDatesAndStopsAtEnd() throws Exception {
        LocalDate start = TODAY.plusDays(1);
        UUID id = create("Conta", start, start.plusMonths(2));

        send(get(URL + "/" + id + "/upcoming").param("count", "10"), ana, null)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0]").value(start.toString()))
                .andExpect(jsonPath("$[2]").value(start.plusMonths(2).toString()));

        send(post(URL + "/" + id + "/pause"), ana, null);
        send(get(URL + "/" + id + "/upcoming"), ana, null).andExpect(jsonPath("$", hasSize(0)));
        send(get(URL + "/" + id + "/upcoming").param("count", "25"), ana, null).andExpect(status().isBadRequest());
    }

    @Test
    void editingAffectsOnlyFutureCycles() throws Exception {
        LocalDate start = TODAY;
        UUID id = create("Antiga", start, null);

        send(put(URL + "/" + id), ana, body("Nova", "EXPENSE", rent, "MONTHLY", start, null).replace("\"amount\":100.00", "\"amount\":250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Nova"));
        job.run(start.plusMonths(1));

        send(get("/api/v1/transactions").param("sort", "transactionDate,asc"), ana, null)
                .andExpect(jsonPath("$.content[0].description").value("Antiga"))
                .andExpect(jsonPath("$.content[0].amount").value(100.00))
                .andExpect(jsonPath("$.content[1].description").value("Nova"))
                .andExpect(jsonPath("$.content[1].amount").value(250.00));
    }

    @Test
    void shorteningEndDateBeforeNextCycleFinishes() throws Exception {
        UUID id = create("Conta", TODAY, null);

        send(put(URL + "/" + id), ana, body("Conta", "EXPENSE", rent, "MONTHLY", TODAY, TODAY))
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void startDateCannotChangeAndFinishedCannotBeEdited() throws Exception {
        UUID id = create("Conta", TODAY, null);
        send(put(URL + "/" + id), ana, body("Conta", "EXPENSE", rent, "MONTHLY", TODAY.plusDays(1), null))
                .andExpect(status().isUnprocessableContent());

        send(put(URL + "/" + id), ana, body("Conta", "EXPENSE", rent, "MONTHLY", TODAY, TODAY));
        send(put(URL + "/" + id), ana, body("Conta", "EXPENSE", rent, "MONTHLY", TODAY, null))
                .andExpect(status().isUnprocessableContent());
        send(post(URL + "/" + id + "/pause"), ana, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void validatesInput() throws Exception {
        send(post(URL), ana, body("X", "INCOME", rent, "MONTHLY", TODAY, null)).andExpect(status().isUnprocessableContent());
        send(post(URL), ana, body("X", "EXPENSE", rent, "MONTHLY", TODAY, TODAY.minusDays(1))).andExpect(status().isUnprocessableContent());
        send(post(URL), ana, body("X", "EXPENSE", rent, "DAILY", TODAY, null)).andExpect(status().isBadRequest());
        send(post(URL), ana, body("  ", "EXPENSE", rent, "MONTHLY", TODAY, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
        send(post(URL), ana, body("Receita", "INCOME", salary, "MONTHLY", TODAY.plusDays(1), null)).andExpect(status().isCreated());
    }

    @Test
    void deletingKeepsGeneratedTransactions() throws Exception {
        UUID id = create("Conta", TODAY, null);

        send(delete(URL + "/" + id), ana, null).andExpect(status().isNoContent());

        assertThat(recurringRepository.findById(id)).isEmpty();
        send(get("/api/v1/transactions"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].recurring").value(false));
    }

    @Test
    void recurrenceCountsAsUsageOfCategoryAndAccount() throws Exception {
        UUID id = create("Conta", TODAY.plusDays(5), null);

        send(get("/api/v1/categories/" + rent + "/usage"), ana, null).andExpect(jsonPath("$.references").value(1));
        send(delete("/api/v1/accounts/" + account), ana, null).andExpect(status().isConflict());

        UUID target = category(ana, "Destino", CategoryType.EXPENSE);
        send(delete("/api/v1/categories/" + rent).param("reassignTo", target.toString()), ana, null)
                .andExpect(status().isNoContent());
        send(get(URL + "/" + id), ana, null).andExpect(jsonPath("$.categoryName").value("Destino"));
    }

    @Test
    void anotherUserHasNoAccess() throws Exception {
        UUID id = create("Conta", TODAY.plusDays(1), null);
        UUID brunoAccount = accountRepository.save(new Account(bruno, "B", AccountType.CHECKING, BigDecimal.TEN)).getId();
        UUID brunoCategory = category(bruno, "B", CategoryType.EXPENSE);

        send(get(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(put(URL + "/" + id), bruno, body("X", "EXPENSE", brunoCategory, "MONTHLY", TODAY.plusDays(1), null).replace(account.toString(), brunoAccount.toString())).andExpect(status().isNotFound());
        send(post(URL + "/" + id + "/pause"), bruno, null).andExpect(status().isNotFound());
        send(post(URL + "/" + id + "/resume"), bruno, null).andExpect(status().isNotFound());
        send(get(URL + "/" + id + "/upcoming"), bruno, null).andExpect(status().isNotFound());
        send(delete(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(get(URL), bruno, null).andExpect(jsonPath("$", hasSize(0)));
        send(post(URL), bruno, body("X", "EXPENSE", rent, "MONTHLY", TODAY, null).replace(account.toString(), brunoAccount.toString())).andExpect(status().isNotFound());

        assertThat(recurringRepository.findById(id)).isPresent();
    }

    @Test
    void staleVersionIsRejectedAndChangesNothing() throws Exception {
        UUID id = create("Original", TODAY.plusDays(1), null);

        send(put(URL + "/" + id), ana, withVersion(body("Alterada", "EXPENSE", rent, "MONTHLY", TODAY.plusDays(1), null), 99))
                .andExpect(status().isConflict());

        send(get(URL + "/" + id), ana, null).andExpect(jsonPath("$.description").value("Original"));
    }

    @Test
    void changingFrequencyRealignsNextCycleFromStartDate() throws Exception {
        UUID id = create("Conta", TODAY, null);
        LocalDate previousNext = TODAY.plusMonths(1);
        long weeks = (java.time.temporal.ChronoUnit.DAYS.between(TODAY, previousNext) + 6) / 7;
        LocalDate expected = TODAY.plusWeeks(weeks);

        send(put(URL + "/" + id), ana, body("Conta", "EXPENSE", rent, "WEEKLY", TODAY, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextOccurrenceDate").value(expected.toString()));

        send(get(URL + "/" + id + "/upcoming").param("count", "2"), ana, null)
                .andExpect(jsonPath("$[0]").value(expected.toString()))
                .andExpect(jsonPath("$[1]").value(expected.plusWeeks(1).toString()));
        // O ciclo de hoje, gerado na frequência antiga, continua sendo o único lançamento.
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void finishedRecurrenceCannotBeResumed() throws Exception {
        LocalDate start = TODAY.minusMonths(3);
        UUID id = create("Curta", start, start.plusMonths(1));

        send(post(URL + "/" + id + "/resume"), ana, null).andExpect(status().isUnprocessableContent());
        send(get(URL + "/" + id), ana, null).andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void resumingActiveRecurrenceChangesNothing() throws Exception {
        UUID id = create("Conta", TODAY.plusDays(3), null);

        send(post(URL + "/" + id + "/resume"), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextOccurrenceDate").value(TODAY.plusDays(3).toString()));
    }

    @Test
    void archivedReferencesAreRejectedForNewButKeptOnExisting() throws Exception {
        UUID id = create("Conta", TODAY.plusDays(1), null);
        jdbcTemplate.update("update categories set archived = true where id = ?", rent);
        jdbcTemplate.update("update accounts set archived = true where id = ?", account);

        send(post(URL), ana, body("Nova", "EXPENSE", rent, "MONTHLY", TODAY.plusDays(1), null))
                .andExpect(status().isUnprocessableContent());
        send(put(URL + "/" + id), ana, body("Mantida", "EXPENSE", rent, "MONTHLY", TODAY.plusDays(1), null))
                .andExpect(status().isOk());

        UUID otherArchived = category(ana, "Arquivada", CategoryType.EXPENSE);
        jdbcTemplate.update("update categories set archived = true where id = ?", otherArchived);
        send(put(URL + "/" + id), ana, body("Mantida", "EXPENSE", otherArchived, "MONTHLY", TODAY.plusDays(1), null))
                .andExpect(status().isUnprocessableContent());

        UUID otherAccount = accountRepository.save(new Account(ana, "Outra", AccountType.CHECKING, BigDecimal.ZERO)).getId();
        jdbcTemplate.update("update categories set archived = false where id = ?", rent);
        send(post(URL), ana, body("Nova", "EXPENSE", rent, "MONTHLY", TODAY.plusDays(1), null))
                .andExpect(status().isUnprocessableContent());
        send(post(URL), ana, body("Nova", "EXPENSE", rent, "MONTHLY", TODAY.plusDays(1), null)
                        .replace(account.toString(), otherAccount.toString()))
                .andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private String withVersion(String json, long version) {
        return json.trim().replaceFirst("}$", ",\"version\":" + version + "}");
    }

    private UUID category(UUID user, String name, CategoryType type) {
        return categoryRepository.save(new Category(user, name, type, "Home", "#7C4DFF")).getId();
    }

    private UUID create(String description, LocalDate start, LocalDate end) throws Exception {
        String response = send(post(URL), ana, body(description, "EXPENSE", rent, "MONTHLY", start, end))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(user.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String body(String description, String type, UUID category, String frequency, LocalDate start, LocalDate end) {
        return """
                {"description":"%s","amount":100.00,"type":"%s","categoryId":"%s","accountId":"%s","frequency":"%s","startDate":"%s"%s}
                """.formatted(description, type, category, account, frequency, start,
                end == null ? "" : ",\"endDate\":\"" + end + "\"");
    }
}
