package com.finantrack.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.budget.entity.Budget;
import com.finantrack.budget.repository.BudgetRepository;
import com.finantrack.card.entity.CardBrand;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.notification.entity.Notification;
import com.finantrack.notification.entity.NotificationType;
import com.finantrack.notification.repository.NotificationRepository;
import com.finantrack.notification.service.NotificationJob;
import com.finantrack.transaction.entity.Transaction;
import com.finantrack.transaction.entity.TransactionStatus;
import com.finantrack.transaction.entity.TransactionType;
import com.finantrack.transaction.repository.TransactionRepository;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import java.math.BigDecimal;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    private static final String URL = "/api/v1/notifications";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    /** Com fechamento dia 15 e vencimento dia 25, a compra de 10/03 vence em 25/03. */
    private static final LocalDate INVOICE_DUE = LocalDate.of(2026, 3, 25);

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
    private TransactionRepository transactionRepository;

    @Autowired
    private CreditCardRepository cardRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJob job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ana;
    private UUID bruno;
    private UUID account;
    private UUID food;

    @BeforeEach
    void setUp() {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
        account = accountRepository.save(new Account(ana, "Corrente", AccountType.CHECKING, new BigDecimal("1000.00"))).getId();
        food = categoryRepository.save(new Category(ana, "Alimentação", CategoryType.EXPENSE, "Home", "#7C4DFF")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void budgetBelowEightyPercentDoesNotNotify() {
        budget("100.00");
        expense("79.99", TransactionStatus.COMPLETED, null);

        job.run(TODAY);

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void budgetAtEightyPercentAlerts() throws Exception {
        budget("100.00");
        expense("80.00", TransactionStatus.COMPLETED, null);

        job.run(TODAY);

        assertThat(types()).containsExactly(NotificationType.BUDGET_ALERT);
        list(ana).andExpect(jsonPath("$.notifications.content[0].message", containsString("80% do orçamento de Alimentação")));
    }

    @Test
    void budgetAboveLimitNotifiesOnlyExceeded() {
        budget("100.00");
        expense("100.01", TransactionStatus.COMPLETED, null);

        job.run(TODAY);

        assertThat(types()).containsExactly(NotificationType.BUDGET_EXCEEDED);
    }

    @Test
    void budgetCrossingBothThresholdsNotifiesEachOnce() {
        budget("100.00");
        expense("85.00", TransactionStatus.COMPLETED, null);
        job.run(TODAY);

        expense("20.00", TransactionStatus.COMPLETED, null);
        job.run(TODAY);
        job.run(TODAY);

        assertThat(types()).containsExactlyInAnyOrder(NotificationType.BUDGET_ALERT, NotificationType.BUDGET_EXCEEDED);
    }

    @Test
    void repeatedRunsDoNotDuplicate() {
        budget("100.00");
        expense("90.00", TransactionStatus.PENDING, TODAY.plusDays(1));

        job.run(TODAY);
        job.run(TODAY);
        job.run(TODAY.plusDays(1));

        assertThat(types()).containsExactlyInAnyOrder(NotificationType.BUDGET_ALERT, NotificationType.DUE_DATE);
    }

    @Test
    void pendingExpenseIsNotifiedOnlyInsideTheWindow() {
        expense("10.00", TransactionStatus.PENDING, TODAY.plusDays(4));

        job.run(TODAY);
        assertThat(notificationRepository.count()).isZero();

        job.run(TODAY.plusDays(1));
        assertThat(types()).containsExactly(NotificationType.DUE_DATE);
    }

    @Test
    void overdueCompletedAndIncomeDoNotCreateDueNotifications() {
        expense("10.00", TransactionStatus.PENDING, TODAY.minusDays(1));
        expense("10.00", TransactionStatus.COMPLETED, TODAY.plusDays(1));
        transactionRepository.save(new Transaction(ana, new Transaction.Details("Salário", new BigDecimal("10.00"),
                TransactionType.INCOME, TransactionStatus.PENDING, food, account, TODAY, TODAY.plusDays(1), null)));

        job.run(TODAY);

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void openInvoiceIsNotifiedWithoutDuplicatingItsInstallments() throws Exception {
        UUID card = card();
        purchase(card, "50.00");

        job.run(INVOICE_DUE.minusDays(4));
        assertThat(notificationRepository.count()).isZero();

        job.run(INVOICE_DUE.minusDays(3));
        job.run(INVOICE_DUE);

        assertThat(types()).containsExactly(NotificationType.INVOICE_DUE);
        list(ana).andExpect(jsonPath("$.notifications.content[0].message", containsString("Nubank vence em 25/03/2026")));
    }

    @Test
    void paidInvoiceIsNotNotified() throws Exception {
        UUID card = card();
        purchase(card, "50.00");
        String invoices = send(get("/api/v1/cards/" + card + "/invoices"), ana, null)
                .andReturn().getResponse().getContentAsString();
        String invoice = com.jayway.jsonpath.JsonPath.read(invoices, "$[0].id");
        send(post("/api/v1/cards/" + card + "/invoices/" + invoice + "/payments"), ana, """
                {"accountId":"%s","amount":50.00,"paymentDate":"2026-03-20"}
                """.formatted(account)).andExpect(status().is2xxSuccessful());

        job.run(INVOICE_DUE);

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void marksOneAsReadAndFiltersUnread() throws Exception {
        expense("10.00", TransactionStatus.PENDING, TODAY.plusDays(1));
        expense("20.00", TransactionStatus.PENDING, TODAY.plusDays(2));
        job.run(TODAY);
        list(ana).andExpect(jsonPath("$.unreadCount").value(2));

        UUID id = notificationRepository.findAll().getFirst().getId();
        send(post(URL + "/" + id + "/read"), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        list(ana).andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications.content", hasSize(2)));
        send(get(URL).param("unread", "true"), ana, null).andExpect(jsonPath("$.notifications.content", hasSize(1)));
    }

    @Test
    void readAllAffectsOnlyTheAuthenticatedUser() throws Exception {
        expense("10.00", TransactionStatus.PENDING, TODAY.plusDays(1));
        notificationRepository.insertIfAbsent(UUID.randomUUID(), bruno, "DUE_DATE", "Conta", "Mensagem", "k", java.time.Instant.now());
        job.run(TODAY);

        send(post(URL + "/read-all"), ana, null).andExpect(status().isNoContent());

        list(ana).andExpect(jsonPath("$.unreadCount").value(0));
        list(bruno).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void otherUserCannotSeeOrReadNotifications() throws Exception {
        expense("10.00", TransactionStatus.PENDING, TODAY.plusDays(1));
        job.run(TODAY);
        UUID id = notificationRepository.findAll().getFirst().getId();

        list(bruno).andExpect(jsonPath("$.notifications.content", hasSize(0)));
        send(post(URL + "/" + id + "/read"), bruno, null).andExpect(status().isNotFound());

        Notification untouched = notificationRepository.findById(id).orElseThrow();
        assertThat(untouched.getReadAt()).isNull();
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private List<NotificationType> types() {
        return notificationRepository.findAll().stream().map(Notification::getType).toList();
    }

    private void budget(String limit) {
        budgetRepository.save(new Budget(ana, food, TODAY.withDayOfMonth(1), new BigDecimal(limit)));
    }

    private void expense(String amount, TransactionStatus status, LocalDate dueDate) {
        transactionRepository.save(new Transaction(ana, new Transaction.Details("Mercado", new BigDecimal(amount),
                TransactionType.EXPENSE, status, food, account, TODAY, dueDate, null)));
    }

    private UUID card() {
        return cardRepository.save(new CreditCard(ana, "Nubank", CardBrand.values()[0], "1234", new BigDecimal("1000.00"), 15, 25)).getId();
    }

    private void purchase(UUID card, String amount) throws Exception {
        send(post("/api/v1/cards/" + card + "/purchases"), ana, """
                {"description":"Compra","totalAmount":%s,"installments":1,"categoryId":"%s","purchaseDate":"2026-03-10"}
                """.formatted(amount, food)).andExpect(status().isCreated());
    }

    private ResultActions list(UUID user) throws Exception {
        return send(get(URL), user, null).andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(user.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }
}
