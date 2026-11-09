package com.finantrack.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.audit.entity.AuditAction;
import com.finantrack.audit.entity.AuditEntityType;
import com.finantrack.audit.entity.AuditLog;
import com.finantrack.audit.repository.AuditLogRepository;
import com.finantrack.audit.service.AuditRecorder;
import com.finantrack.auth.entity.UserTokenType;
import com.finantrack.auth.service.PasswordResetService;
import com.finantrack.auth.service.RefreshTokenService;
import com.finantrack.auth.service.UserTokenService;
import com.finantrack.card.entity.CardBrand;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.goal.entity.FinancialGoal;
import com.finantrack.goal.repository.FinancialGoalRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditIntegrationTest {

    private static final String TRANSACTIONS = "/api/v1/transactions";
    private static final String CARDS = "/api/v1/cards";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CreditCardRepository cardRepository;

    @Autowired
    private FinancialGoalRepository goalRepository;

    @Autowired
    private AuditLogRepository auditRepository;

    @Autowired
    private AuditRecorder recorder;

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private UserTokenService userTokens;

    @Autowired
    private PasswordResetService passwordReset;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private UUID ana;
    private UUID account;
    private UUID food;

    @BeforeEach
    void setUp() {
        clean();
        user = userRepository.save(new User("Ana", "ana@exemplo.com", passwordEncoder.encode("senha-original-1")));
        ana = user.getId();
        account = accountRepository.save(new Account(ana, "Corrente", AccountType.CHECKING, new BigDecimal("1000.00"))).getId();
        food = categoryRepository.save(new Category(ana, "Alimentação", CategoryType.EXPENSE, "Home", "#7C4DFF")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
        jdbcTemplate.execute("truncate table audit_logs");
    }

    @Test
    void transactionLifecycleRecordsBeforeAndAfter() throws Exception {
        UUID id = createTransaction("50.00");
        send(put(TRANSACTIONS + "/" + id), transactionBody("75.00")).andExpect(status().isOk());
        send(delete(TRANSACTIONS + "/" + id), null).andExpect(status().isNoContent());

        List<AuditLog> logs = logs();
        assertThat(logs).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getUserId()).isEqualTo(ana);
            assertThat(log.getEntityType()).isEqualTo(AuditEntityType.TRANSACTION);
            assertThat(log.getEntityId()).isEqualTo(id);
        });

        AuditLog update = only(AuditAction.UPDATE);
        assertThat(amountAt(update, "$.before.amount")).isEqualTo(50.0);
        assertThat(amountAt(update, "$.after.amount")).isEqualTo(75.0);
        assertThat(amountAt(only(AuditAction.DELETE), "$.before.amount")).isEqualTo(75.0);
    }

    @Test
    void notesAreNotAudited() throws Exception {
        send(post(TRANSACTIONS), """
                {"description":"Mercado","amount":10.00,"type":"EXPENSE","status":"COMPLETED","categoryId":"%s",
                 "accountId":"%s","transactionDate":"2026-03-10","notes":"anotacao-privada"}
                """.formatted(food, account)).andExpect(status().isCreated());

        assertThat(only(AuditAction.CREATE).getChanges()).doesNotContain("anotacao-privada");
    }

    @Test
    void readsAndRejectedOperationsRecordNothing() throws Exception {
        send(get(TRANSACTIONS), null).andExpect(status().isOk());
        send(post(TRANSACTIONS), transactionBody("-1.00")).andExpect(status().isBadRequest());

        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void recordIsUndoneWithTheCallersTransaction() {
        transactionTemplate.executeWithoutResult(tx -> {
            recorder.record(ana, AuditAction.UPDATE, AuditEntityType.TRANSACTION, UUID.randomUUID());
            tx.setRollbackOnly();
        });

        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void purchaseCreationAndCancellationAreAudited() throws Exception {
        UUID card = card();
        String response = purchase(card).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID purchase = UUID.fromString(JsonPath.read(response, "$.id"));

        send(delete(CARDS + "/" + card + "/purchases/" + purchase), null).andExpect(status().isNoContent());

        AuditLog created = only(AuditAction.CREATE);
        assertThat(created.getEntityType()).isEqualTo(AuditEntityType.CARD_PURCHASE);
        assertThat(amountAt(created, "$.after.totalAmount")).isEqualTo(100.0);
        assertThat(only(AuditAction.CANCEL).getEntityId()).isEqualTo(purchase);
    }

    @Test
    void invoicePaymentIsAudited() throws Exception {
        UUID card = card();
        purchase(card).andExpect(status().isCreated());
        String invoices = send(get(CARDS + "/" + card + "/invoices"), null).andReturn().getResponse().getContentAsString();
        String invoice = JsonPath.read(invoices, "$[0].id");
        send(post(CARDS + "/" + card + "/invoices/" + invoice + "/payments"), """
                {"accountId":"%s","amount":40.00,"paymentDate":"2026-03-20"}
                """.formatted(account)).andExpect(status().is2xxSuccessful());

        AuditLog payment = logs().stream()
                .filter(log -> log.getEntityType() == AuditEntityType.INVOICE_PAYMENT).findFirst().orElseThrow();
        assertThat(amountAt(payment, "$.after.amount")).isEqualTo(40.0);
    }

    @Test
    void onlyLimitChangesAreAuditedOnCardUpdate() throws Exception {
        UUID card = card();

        send(put(CARDS + "/" + card), cardBody("Novo apelido", "1000.00")).andExpect(status().isOk());
        assertThat(auditRepository.count()).isZero();

        send(put(CARDS + "/" + card), cardBody("Novo apelido", "1500.00")).andExpect(status().isOk());

        AuditLog change = only(AuditAction.LIMIT_CHANGE);
        assertThat(amountAt(change, "$.before.creditLimit")).isEqualTo(1000.0);
        assertThat(amountAt(change, "$.after.creditLimit")).isEqualTo(1500.0);
    }

    @Test
    void goalMovementsAreAudited() throws Exception {
        UUID goal = goalRepository.save(new FinancialGoal(ana, "Viagem", null, new BigDecimal("500.00"),
                new BigDecimal("100.00"), LocalDate.now().plusYears(1))).getId();

        send(post("/api/v1/goals/" + goal + "/withdrawals"), """
                {"amount":30.00}
                """).andExpect(status().isOk());

        AuditLog movement = only(AuditAction.CREATE);
        assertThat(movement.getEntityType()).isEqualTo(AuditEntityType.GOAL_MOVEMENT);
        assertThat(JsonPath.<String>read(movement.getChanges(), "$.after.type")).isEqualTo("WITHDRAWAL");
    }

    @Test
    void sessionEventsAreAuditedWithoutTokens() {
        String raw = refreshTokens.startSession(user, "teste").refreshToken();

        refreshTokens.revokeSession(raw);

        assertThat(only(AuditAction.LOGIN).getUserId()).isEqualTo(ana);
        assertThat(only(AuditAction.LOGOUT).getChanges()).isNull();
    }

    @Test
    void refreshTokenReuseIsAudited() {
        String first = refreshTokens.startSession(user, "teste").refreshToken();
        refreshTokens.rotate(first, "teste");
        jdbcTemplate.update("update refresh_tokens set rotated_at = rotated_at - interval '1 minute'"
                + " where rotated_at is not null");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> refreshTokens.rotate(first, "teste"))
                .isInstanceOf(com.finantrack.common.exception.InvalidRefreshTokenException.class);

        assertThat(only(AuditAction.REFRESH_TOKEN_REUSE).getUserId()).isEqualTo(ana);
    }

    @Test
    void passwordResetIsAuditedWithoutThePassword() {
        String token = userTokens.issue(user, UserTokenType.PASSWORD_RESET, Duration.ofMinutes(30));

        passwordReset.reset(token, "senha-nova-segura-2");

        AuditLog reset = only(AuditAction.PASSWORD_RESET);
        assertThat(reset.getEntityId()).isEqualTo(ana);
        assertThat(reset.getChanges()).isNull();
    }

    @Test
    void auditSurvivesUserRemovalBecauseItHasNoForeignKey() {
        recorder.record(ana, AuditAction.LOGIN, AuditEntityType.USER, ana);

        jdbcTemplate.execute("truncate table users cascade");

        assertThat(auditRepository.count()).isEqualTo(1);
    }

    private List<AuditLog> logs() {
        return auditRepository.findAll();
    }

    private AuditLog only(AuditAction action) {
        List<AuditLog> matches = logs().stream().filter(log -> log.getAction() == action).toList();
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    private double amountAt(AuditLog log, String path) {
        return JsonPath.<Number>read(log.getChanges(), path).doubleValue();
    }

    private UUID createTransaction(String amount) throws Exception {
        String response = send(post(TRANSACTIONS), transactionBody(amount))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private String transactionBody(String amount) {
        return """
                {"description":"Mercado","amount":%s,"type":"EXPENSE","status":"COMPLETED","categoryId":"%s","accountId":"%s","transactionDate":"2026-03-10"}
                """.formatted(amount, food, account);
    }

    private UUID card() {
        return cardRepository.save(new CreditCard(ana, "Nubank", CardBrand.VISA, "1234", new BigDecimal("1000.00"), 15, 25)).getId();
    }

    private ResultActions purchase(UUID card) throws Exception {
        return send(post(CARDS + "/" + card + "/purchases"), """
                {"description":"Compra","totalAmount":100.00,"installments":1,"categoryId":"%s","purchaseDate":"2026-03-10"}
                """.formatted(food));
    }

    private String cardBody(String nickname, String limit) {
        return """
                {"nickname":"%s","brand":"VISA","lastFour":"1234","creditLimit":%s,"closingDay":15,"dueDay":25,"archived":false}
                """.formatted(nickname, limit);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(ana.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }
}
