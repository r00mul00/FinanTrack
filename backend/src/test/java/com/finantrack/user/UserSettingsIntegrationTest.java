package com.finantrack.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.auth.service.EmailSender;
import com.finantrack.budget.entity.Budget;
import com.finantrack.budget.repository.BudgetRepository;
import com.finantrack.card.entity.CardBrand;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.notification.repository.NotificationRepository;
import com.finantrack.notification.service.NotificationJob;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.finantrack.user.service.AccountDeletionJob;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSettingsIntegrationTest {

    private static final String ME = "/api/v1/users/me";
    private static final String PASSWORD = "senha-bem-segura";
    private static final Pattern TOKEN = Pattern.compile("token=([\\w-]+)");

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
    private CreditCardRepository cardRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJob notificationJob;

    @Autowired
    private AccountDeletionJob deletionJob;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailSender emailSender;

    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void setUp() {
        clean();
        ana = user("Ana", "ana@exemplo.com");
        bruno = user("Bruno", "bruno@exemplo.com");
    }

    @AfterEach
    void clean() {
        jdbc.execute("truncate table users cascade");
        jdbc.execute("truncate table audit_logs");
    }

    @Test
    void renameTrimsAndValidates() throws Exception {
        send(patch(ME), ana, "{\"name\":\"  Ana Maria  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Maria"));

        send(patch(ME), ana, "{\"name\":\"  \"}").andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordRequiresCurrentAndEndsSessions() throws Exception {
        session(ana);

        send(put(ME + "/password"), ana, "{\"currentPassword\":\"errada-errada\",\"newPassword\":\"nova-senha-segura\"}")
                .andExpect(status().isUnprocessableContent());
        assertThat(activeSessions(ana)).isEqualTo(1);

        send(put(ME + "/password"), ana, "{\"currentPassword\":\"%s\",\"newPassword\":\"nova-senha-segura\"}".formatted(PASSWORD))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("nova-senha-segura", userRepository.findById(ana).orElseThrow().getPasswordHash())).isTrue();
        assertThat(activeSessions(ana)).isZero();
        assertThat(auditCount(ana, "PASSWORD_CHANGE")).isEqualTo(1);
    }

    @Test
    void shortNewPasswordIsRejected() throws Exception {
        send(put(ME + "/password"), ana, "{\"currentPassword\":\"%s\",\"newPassword\":\"curta\"}".formatted(PASSWORD))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emailOnlyChangesAfterConfirmingLinkSentToNewAddress() throws Exception {
        requestEmailChange(ana, "Ana.Nova@Exemplo.com").andExpect(status().isAccepted());
        assertThat(emailOf(ana)).isEqualTo("ana@exemplo.com");

        confirm(ana, tokenSentTo("ana.nova@exemplo.com")).andExpect(status().isNoContent());

        assertThat(emailOf(ana)).isEqualTo("ana.nova@exemplo.com");
        verify(emailSender).send(eq("ana@exemplo.com"), anyString(), anyString());
        assertThat(auditCount(ana, "EMAIL_CHANGE")).isEqualTo(1);
    }

    @Test
    void emailChangeLinkIsSingleUseAndBelongsToItsOwner() throws Exception {
        requestEmailChange(ana, "nova@exemplo.com").andExpect(status().isAccepted());
        String token = tokenSentTo("nova@exemplo.com");

        confirm(bruno, token).andExpect(status().isUnprocessableContent());
        assertThat(emailOf(bruno)).isEqualTo("bruno@exemplo.com");

        confirm(ana, token).andExpect(status().isNoContent());
        confirm(ana, token).andExpect(status().isUnprocessableContent());
    }

    @Test
    void emailChangeDoesNotRevealTakenAddress() throws Exception {
        requestEmailChange(ana, "bruno@exemplo.com").andExpect(status().isAccepted());

        verify(emailSender, never()).send(eq("bruno@exemplo.com"), anyString(), anyString());
        assertThat(emailOf(ana)).isEqualTo("ana@exemplo.com");
    }

    @Test
    void emailChangeRequiresPasswordAndDifferentAddress() throws Exception {
        send(post(ME + "/email-change"), ana, "{\"newEmail\":\"nova@exemplo.com\",\"currentPassword\":\"errada-errada\"}")
                .andExpect(status().isUnprocessableContent());
        requestEmailChange(ana, "ANA@exemplo.com").andExpect(status().isUnprocessableContent());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void addressTakenBeforeConfirmationConflicts() throws Exception {
        requestEmailChange(ana, "disputado@exemplo.com").andExpect(status().isAccepted());
        String token = tokenSentTo("disputado@exemplo.com");
        user("Carla", "disputado@exemplo.com");

        confirm(ana, token).andExpect(status().isConflict());
        assertThat(emailOf(ana)).isEqualTo("ana@exemplo.com");
    }

    @Test
    void preferencesPatchKeepsMissingFields() throws Exception {
        send(get(ME + "/preferences"), ana, null)
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.notifyBudget").value(true));

        send(patch(ME + "/preferences"), ana, "{\"theme\":\"LIGHT\",\"notifyBudget\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.theme").value("LIGHT"))
                .andExpect(jsonPath("$.notifyBudget").value(false))
                .andExpect(jsonPath("$.notifyDueDates").value(true));

        send(patch(ME + "/preferences"), ana, "{\"currency\":\"USD\"}")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.theme").value("LIGHT"));
        send(get(ME), bruno, null).andExpect(jsonPath("$.currency").value("BRL"));
    }

    @Test
    void unsupportedPreferenceValuesAreRejected() throws Exception {
        send(patch(ME + "/preferences"), ana, "{\"currency\":\"XXX\"}").andExpect(status().isBadRequest());
        send(patch(ME + "/preferences"), ana, "{\"theme\":\"BLUE\"}").andExpect(status().isBadRequest());
    }

    @Test
    void mutedUsersDoNotReceiveNotifications() throws Exception {
        LocalDate today = LocalDate.now();
        Seed seed = seedFinancialData(ana, today);
        Seed other = seedFinancialData(bruno, today);
        jdbc.update("update users set notify_budget = false, notify_due_dates = false where id = ?", ana);

        notificationJob.run(today);

        assertThat(notificationRepository.count()).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class, ana)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?", Long.class, bruno)).isPositive();
        assertThat(seed).isNotNull();
        assertThat(other).isNotNull();
    }

    @Test
    void exportHasOwnDataOnlyAndNoSecrets() throws Exception {
        LocalDate today = LocalDate.now();
        seedFinancialData(ana, today);
        seedFinancialData(bruno, today);
        session(ana);

        var response = send(get(ME + "/export"), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.goalMovements.length()").value(1))
                .andExpect(jsonPath("$.cardPurchases.length()").value(1))
                .andExpect(jsonPath("$.cardInstallments.length()").value(1))
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("finantrack-dados.json");
        String body = response.getContentAsString();
        assertThat(body).doesNotContain("password", "token_hash", "bruno@exemplo.com");
        assertThat(auditCount(ana, "DATA_EXPORT")).isEqualTo(1);
    }

    @Test
    void deletionRequestNeedsPasswordAndEndsSessions() throws Exception {
        session(ana);

        send(post(ME + "/deletion-request"), ana, "{\"currentPassword\":\"errada-errada\"}")
                .andExpect(status().isUnprocessableContent());
        assertThat(deletionRequestedAt(ana)).isNull();

        requestDeletion(ana).andExpect(status().isAccepted());

        assertThat(deletionRequestedAt(ana)).isNotNull();
        assertThat(activeSessions(ana)).isZero();
        verify(emailSender).send(eq("ana@exemplo.com"), anyString(), anyString());
    }

    @Test
    void loginDuringGracePeriodCancelsDeletion() throws Exception {
        requestDeletion(ana).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@exemplo.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk());

        assertThat(deletionRequestedAt(ana)).isNull();
        assertThat(auditCount(ana, "DELETION_CANCEL")).isEqualTo(1);
    }

    @Test
    void wrongPasswordDoesNotCancelDeletion() throws Exception {
        requestDeletion(ana).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@exemplo.com\",\"password\":\"errada-errada\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(deletionRequestedAt(ana)).isNotNull();
    }

    @Test
    void jobKeepsAccountsInsideGracePeriod() {
        Instant now = Instant.now();
        requestedAt(ana, now.minus(29, ChronoUnit.DAYS));

        deletionJob.run(now);

        assertThat(userRepository.existsById(ana)).isTrue();
    }

    @Test
    void jobErasesEveryRowOfExpiredAccountAndKeepsOthersAndAudit() throws Exception {
        LocalDate today = LocalDate.now();
        seedFinancialData(ana, today);
        seedFinancialData(bruno, today);
        session(ana);
        notificationJob.run(today);
        Instant now = Instant.now();
        requestedAt(ana, now.minus(30, ChronoUnit.DAYS));

        deletionJob.run(now);

        assertThat(userRepository.existsById(ana)).isFalse();
        assertThat(rowsOwnedBy(ana)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from goal_movements", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from credit_card_installments", Long.class)).isEqualTo(1);
        assertThat(userRepository.existsById(bruno)).isTrue();
        assertThat(rowsOwnedBy(bruno)).isNotEmpty();
        assertThat(auditCount(ana, "ACCOUNT_DELETION")).isEqualTo(1);
    }

    /** Percorre o catálogo do banco: uma tabela nova com user_id não escapa da exclusão sem o teste notar. */
    private List<String> rowsOwnedBy(UUID userId) {
        return jdbc.queryForList("""
                select table_name from information_schema.columns
                 where table_schema = 'public' and column_name = 'user_id' and table_name <> 'audit_logs'
                """, String.class).stream()
                .filter(table -> jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, userId) > 0)
                .toList();
    }

    private Seed seedFinancialData(UUID owner, LocalDate today) throws Exception {
        UUID account = accountRepository.save(new Account(owner, "Corrente", AccountType.CHECKING, new BigDecimal("1000.00"))).getId();
        UUID food = categoryRepository.save(new Category(owner, "Alimentação", CategoryType.EXPENSE, "Home", "#7C4DFF")).getId();
        budgetRepository.save(new Budget(owner, food, today.withDayOfMonth(1), new BigDecimal("10.00")));

        send(post("/api/v1/transactions"), owner, """
                {"description":"Conta de luz","amount":50.00,"type":"EXPENSE","status":"PENDING","categoryId":"%s","accountId":"%s","transactionDate":"%s","dueDate":"%s"}
                """.formatted(food, account, today, today)).andExpect(status().isCreated());

        UUID card = cardRepository.save(new CreditCard(owner, "Nubank", CardBrand.VISA, "1234", new BigDecimal("1000.00"), 15, 25)).getId();
        send(post("/api/v1/cards/" + card + "/purchases"), owner, """
                {"description":"Compra","totalAmount":100.00,"installments":1,"categoryId":"%s","purchaseDate":"%s"}
                """.formatted(food, today)).andExpect(status().isCreated());

        String goal = send(post("/api/v1/goals"), owner, """
                {"name":"Viagem","targetAmount":1000.00,"initialAmount":0,"deadline":"%s"}
                """.formatted(today.plusYears(1))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        send(post("/api/v1/goals/" + JsonPath.read(goal, "$.id") + "/deposits"), owner, "{\"amount\":10.00}")
                .andExpect(status().isOk());

        send(post("/api/v1/recurring"), owner, """
                {"description":"Internet","amount":100.00,"type":"EXPENSE","categoryId":"%s","accountId":"%s","frequency":"MONTHLY","startDate":"%s"}
                """.formatted(food, account, today.plusDays(10))).andExpect(status().isCreated());
        return new Seed(account, food);
    }

    private record Seed(UUID account, UUID category) {
    }

    private UUID user(String name, String email) {
        User user = new User(name, email, passwordEncoder.encode(PASSWORD));
        user.markEmailVerified(Instant.now());
        return userRepository.save(user).getId();
    }

    /** Sessão ativa gravada diretamente, sem passar pelo login. */
    private void session(UUID owner) {
        jdbc.update("""
                insert into refresh_tokens (id, user_id, family_id, token_hash, expires_at, created_at, updated_at)
                values (?, ?, ?, ?, now() + interval '1 day', now(), now())
                """, UUID.randomUUID(), owner, UUID.randomUUID(), UUID.randomUUID().toString().replace("-", "").repeat(2));
    }

    private long activeSessions(UUID owner) {
        return jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Long.class, owner);
    }

    private long auditCount(UUID owner, String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where user_id = ? and action = ?", Long.class, owner, action);
    }

    private String emailOf(UUID owner) {
        return userRepository.findById(owner).orElseThrow().getEmail();
    }

    private Instant deletionRequestedAt(UUID owner) {
        return userRepository.findById(owner).orElseThrow().getDeletionRequestedAt();
    }

    private void requestedAt(UUID owner, Instant when) {
        jdbc.update("update users set deletion_requested_at = ? where id = ?", Timestamp.from(when), owner);
    }

    private String tokenSentTo(String address) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(address), anyString(), body.capture());
        Matcher matcher = TOKEN.matcher(body.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private ResultActions requestEmailChange(UUID user, String address) throws Exception {
        return send(post(ME + "/email-change"), user, "{\"newEmail\":\"%s\",\"currentPassword\":\"%s\"}".formatted(address, PASSWORD));
    }

    private ResultActions confirm(UUID user, String token) throws Exception {
        return send(post(ME + "/email-change/confirm"), user, "{\"token\":\"%s\"}".formatted(token));
    }

    private ResultActions requestDeletion(UUID user) throws Exception {
        return send(post(ME + "/deletion-request"), user, "{\"currentPassword\":\"%s\"}".formatted(PASSWORD));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(user.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }
}
