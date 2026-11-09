package com.finantrack.dashboard;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.card.entity.CardBrand;
import com.finantrack.card.entity.CreditCard;
import com.finantrack.card.entity.CreditCardInvoice;
import com.finantrack.card.repository.CreditCardInvoiceRepository;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
class UpcomingDueIntegrationTest {

    private static final String URL = "/api/v1/dashboard/upcoming";
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
    private CreditCardRepository cardRepository;

    @Autowired
    private CreditCardInvoiceRepository invoiceRepository;

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
    void listsPendingBillsOrderedAndMarksOverdue() throws Exception {
        bill("Luz", TODAY.plusDays(5));
        bill("Água", TODAY.minusDays(2));
        bill("Internet", TODAY);

        upcoming(ana)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].description").value("Água"))
                .andExpect(jsonPath("$[0].type").value("BILL"))
                .andExpect(jsonPath("$[0].overdue").value(true))
                .andExpect(jsonPath("$[1].description").value("Internet"))
                .andExpect(jsonPath("$[1].overdue").value(false))
                .andExpect(jsonPath("$[2].description").value("Luz"));
    }

    @Test
    void respectsTheThirtyDayWindow() throws Exception {
        bill("Dentro", TODAY.plusDays(30));
        bill("Fora", TODAY.plusDays(31));

        upcoming(ana)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Dentro"));
    }

    @Test
    void ignoresCompletedIncomeAndBillsWithoutDueDate() throws Exception {
        save("Paga", TransactionType.EXPENSE, TransactionStatus.COMPLETED, TODAY.plusDays(1));
        save("Salário", TransactionType.INCOME, TransactionStatus.PENDING, TODAY.plusDays(1));
        save("Sem vencimento", TransactionType.EXPENSE, TransactionStatus.PENDING, null);

        upcoming(ana).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void limitsToEightItemsKeepingTheEarliest() throws Exception {
        for (int day = 1; day <= 10; day++) {
            bill("Conta " + day, TODAY.plusDays(day));
        }

        upcoming(ana)
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].description").value("Conta 1"))
                .andExpect(jsonPath("$[7].description").value("Conta 8"));
    }

    @Test
    void invoiceShowsRemainingAmountAndCard() throws Exception {
        UUID card = card();
        purchase(card, "100.00");
        CreditCardInvoice invoice = moveInvoiceDueDateToSoon(card);
        pay(card, invoice.getId(), "30.00");

        upcoming(ana)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("INVOICE"))
                .andExpect(jsonPath("$[0].description").value("Fatura Nubank"))
                .andExpect(jsonPath("$[0].cardId").value(card.toString()))
                .andExpect(jsonPath("$[0].amount").value(70.00));
    }

    @Test
    void paidAndEmptyInvoicesAreIgnored() throws Exception {
        UUID card = card();
        purchase(card, "100.00");
        CreditCardInvoice invoice = moveInvoiceDueDateToSoon(card);
        pay(card, invoice.getId(), "100.00");
        YearMonth other = YearMonth.from(TODAY).plusMonths(6);
        invoiceRepository.save(new CreditCardInvoice(card, other.atDay(1), TODAY, TODAY.plusDays(1)));

        upcoming(ana).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void neverReturnsAnotherUsersItems() throws Exception {
        bill("Luz", TODAY.plusDays(1));
        UUID card = card();
        purchase(card, "100.00");
        moveInvoiceDueDateToSoon(card);

        upcoming(bruno).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private void bill(String description, LocalDate dueDate) {
        save(description, TransactionType.EXPENSE, TransactionStatus.PENDING, dueDate);
    }

    private void save(String description, TransactionType type, TransactionStatus status, LocalDate dueDate) {
        transactionRepository.save(new Transaction(ana, new Transaction.Details(description, new BigDecimal("10.00"), type,
                status, food, account, TODAY, dueDate, null)));
    }

    private UUID card() {
        return cardRepository.save(new CreditCard(ana, "Nubank", CardBrand.VISA, "1234", new BigDecimal("1000.00"), 15, 25)).getId();
    }

    private void purchase(UUID card, String amount) throws Exception {
        send(post("/api/v1/cards/" + card + "/purchases"), ana, """
                {"description":"Compra","totalAmount":%s,"installments":1,"categoryId":"%s","purchaseDate":"2026-03-10"}
                """.formatted(amount, food)).andExpect(status().isCreated());
    }

    /** A compra usa uma data fixa; o vencimento é trazido para perto de hoje, sem depender do calendário. */
    private CreditCardInvoice moveInvoiceDueDateToSoon(UUID card) {
        jdbcTemplate.update("update credit_card_invoices set due_date = ? where card_id = ?",
                java.sql.Date.valueOf(TODAY.plusDays(2)), card);
        return invoiceRepository.findAllByCardIdOrderByReferenceMonthDesc(card).getFirst();
    }

    private void pay(UUID card, UUID invoice, String amount) throws Exception {
        send(post("/api/v1/cards/" + card + "/invoices/" + invoice + "/payments"), ana, """
                {"accountId":"%s","amount":%s,"paymentDate":"2026-03-20"}
                """.formatted(account, amount)).andExpect(status().is2xxSuccessful());
    }

    private ResultActions upcoming(UUID user) throws Exception {
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
