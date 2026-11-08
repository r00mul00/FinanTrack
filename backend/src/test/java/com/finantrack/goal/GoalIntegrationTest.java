package com.finantrack.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.goal.entity.FinancialGoal;
import com.finantrack.goal.repository.FinancialGoalRepository;
import com.finantrack.goal.repository.GoalMovementRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalIntegrationTest {

    private static final String URL = "/api/v1/goals";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final String FUTURE = TODAY.plusYears(1).toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FinancialGoalRepository goalRepository;

    @Autowired
    private GoalMovementRepository movementRepository;

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
    void createsWithInitialAmountAsBalance() throws Exception {
        send(post(URL), ana, goal("Viagem", "1000.00", "250.00", FUTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentAmount").value(250.00))
                .andExpect(jsonPath("$.remainingAmount").value(750.00))
                .andExpect(jsonPath("$.progress").value(25.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void depositIncreasesBalanceAndRecordsHistory() throws Exception {
        UUID id = create("1000.00", "0");

        move(id, "deposits", "300.50", ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAmount").value(300.50))
                .andExpect(jsonPath("$.progress").value(30.05));

        send(get(URL + "/" + id + "/movements"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[0].amount").value(300.50));
    }

    @Test
    void withdrawalUpToBalanceWorks() throws Exception {
        UUID id = create("1000.00", "100.00");

        move(id, "withdrawals", "100.00", ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAmount").value(0));
    }

    @Test
    void withdrawalAboveBalanceIsRejectedAndChangesNothing() throws Exception {
        UUID id = create("1000.00", "100.00");

        move(id, "withdrawals", "100.01", ana).andExpect(status().isUnprocessableContent());

        assertThat(balance(id)).isEqualByComparingTo("100.00");
        assertThat(movementRepository.count()).isZero();
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        UUID id = create("1000.00", "100.00");
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            start.await();
            return move(id, "withdrawals", "30.00", ana).andReturn().getResponse().getStatus();
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

        assertThat(statuses.stream().filter(status -> status == 200)).hasSize(3);
        assertThat(statuses.stream().filter(status -> status == 422)).hasSize(5);
        assertThat(balance(id)).isEqualByComparingTo("10.00");
        assertThat(movementRepository.count()).isEqualTo(3);
    }

    @Test
    void concurrentDepositsAreAllCounted() throws Exception {
        UUID id = create("1000.00", "0");
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            start.await();
            return move(id, "deposits", "10.00", ana).andReturn().getResponse().getStatus();
        };
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(task));
        }
        start.countDown();
        for (Future<Integer> future : futures) {
            assertThat(future.get()).isEqualTo(200);
        }
        executor.shutdown();

        assertThat(balance(id)).isEqualByComparingTo("80.00");
    }

    @Test
    void rejectsNonPositiveMovements() throws Exception {
        UUID id = create("1000.00", "100.00");

        move(id, "deposits", "0", ana).andExpect(status().isBadRequest());
        move(id, "deposits", "-5.00", ana).andExpect(status().isBadRequest());
        move(id, "withdrawals", "1.999", ana).andExpect(status().isBadRequest());
    }

    @Test
    void progressIsCappedAtHundred() throws Exception {
        UUID id = create("100.00", "0");

        move(id, "deposits", "150.00", ana)
                .andExpect(jsonPath("$.currentAmount").value(150.00))
                .andExpect(jsonPath("$.progress").value(100.00))
                .andExpect(jsonPath("$.remainingAmount").value(0));
    }

    @Test
    void reachingTargetDoesNotCompleteAutomatically() throws Exception {
        UUID id = create("100.00", "0");

        move(id, "deposits", "100.00", ana).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void completedGoalRejectsMovementsAndCompleteIsIdempotent() throws Exception {
        UUID id = create("100.00", "50.00");

        send(post(URL + "/" + id + "/complete"), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        send(post(URL + "/" + id + "/complete"), ana, null).andExpect(status().isOk());

        move(id, "deposits", "1.00", ana).andExpect(status().isUnprocessableContent());
        move(id, "withdrawals", "1.00", ana).andExpect(status().isUnprocessableContent());
    }

    @Test
    void historyIsNewestFirst() throws Exception {
        UUID id = create("1000.00", "100.00");
        move(id, "deposits", "1.00", ana);
        Thread.sleep(5);
        move(id, "withdrawals", "2.00", ana);

        send(get(URL + "/" + id + "/movements"), ana, null)
                .andExpect(jsonPath("$.content[*].type", contains("WITHDRAWAL", "DEPOSIT")));
    }

    @Test
    void editingInitialAmountShiftsBalance() throws Exception {
        UUID id = create("1000.00", "100.00");
        move(id, "deposits", "50.00", ana);

        send(put(URL + "/" + id), ana, goal("Viagem", "2000.00", "120.00", FUTURE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAmount").value(170.00))
                .andExpect(jsonPath("$.targetAmount").value(2000.00));
    }

    @Test
    void initialAmountCannotMakeBalanceNegative() throws Exception {
        UUID id = create("1000.00", "100.00");
        move(id, "withdrawals", "80.00", ana);

        send(put(URL + "/" + id), ana, goal("Viagem", "1000.00", "10.00", FUTURE))
                .andExpect(status().isUnprocessableContent());

        assertThat(balance(id)).isEqualByComparingTo("20.00");
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        UUID id = create("1000.00", "0");
        String stale = "{\"name\":\"X\",\"targetAmount\":10,\"initialAmount\":0,\"deadline\":\"" + FUTURE + "\",\"version\":0}";

        move(id, "deposits", "1.00", ana);

        send(put(URL + "/" + id), ana, stale).andExpect(status().isConflict());
    }

    @Test
    void deadlineCannotBeInThePastOnCreationButOverdueIsFlagged() throws Exception {
        send(post(URL), ana, goal("Velha", "10.00", "0", TODAY.minusDays(1).toString()))
                .andExpect(status().isUnprocessableContent());
        send(post(URL), ana, goal("Hoje", "10.00", "0", TODAY.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overdue").value(false));

        goalRepository.save(new FinancialGoal(ana, "Atrasada", null, BigDecimal.TEN, BigDecimal.ZERO, TODAY.minusDays(1)));

        send(get(URL), ana, null)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Atrasada"))
                .andExpect(jsonPath("$[0].overdue").value(true));
    }

    @Test
    void rejectsInvalidGoalData() throws Exception {
        send(post(URL), ana, goal("  ", "0", "-1", FUTURE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.targetAmount").exists())
                .andExpect(jsonPath("$.errors.initialAmount").exists());
    }

    @Test
    void anotherUserHasNoAccess() throws Exception {
        UUID id = create("1000.00", "100.00");

        send(get(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(put(URL + "/" + id), bruno, goal("X", "1.00", "0", FUTURE)).andExpect(status().isNotFound());
        send(delete(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(post(URL + "/" + id + "/complete"), bruno, null).andExpect(status().isNotFound());
        send(get(URL + "/" + id + "/movements"), bruno, null).andExpect(status().isNotFound());
        move(id, "deposits", "1.00", bruno).andExpect(status().isNotFound());
        move(id, "withdrawals", "1.00", bruno).andExpect(status().isNotFound());
        send(get(URL), bruno, null).andExpect(jsonPath("$", hasSize(0)));

        assertThat(balance(id)).isEqualByComparingTo("100.00");
    }

    @Test
    void deletingGoalRemovesHistory() throws Exception {
        UUID id = create("1000.00", "0");
        move(id, "deposits", "1.00", ana);

        send(delete(URL + "/" + id), ana, null).andExpect(status().isNoContent());

        assertThat(goalRepository.findById(id)).isEmpty();
        assertThat(movementRepository.count()).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private BigDecimal balance(UUID id) {
        return goalRepository.findById(id).orElseThrow().getCurrentAmount();
    }

    private UUID create(String target, String initial) throws Exception {
        String response = send(post(URL), ana, goal("Viagem", target, initial, FUTURE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private ResultActions move(UUID id, String kind, String amount, UUID user) throws Exception {
        return send(post(URL + "/" + id + "/" + kind), user, "{\"amount\":" + amount + "}");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(user.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String goal(String name, String target, String initial, String deadline) {
        return """
                {"name":"%s","targetAmount":%s,"initialAmount":%s,"deadline":"%s"}
                """.formatted(name, target, initial, deadline);
    }
}
