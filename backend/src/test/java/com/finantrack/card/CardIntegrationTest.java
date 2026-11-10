package com.finantrack.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.account.entity.Account;
import com.finantrack.account.entity.AccountType;
import com.finantrack.account.repository.AccountRepository;
import com.finantrack.card.repository.CreditCardRepository;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
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
class CardIntegrationTest {

    private static final String URL = "/api/v1/cards";
    private static final String DATE = "2026-03-10";

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
    private JdbcTemplate jdbcTemplate;

    private UUID ana;
    private UUID bruno;
    private UUID account;
    private UUID food;
    private UUID salary;
    private UUID card;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
        account = accountRepository.save(new Account(ana, "Corrente", AccountType.CHECKING, new BigDecimal("5000.00"))).getId();
        food = category(ana, "Alimentação", CategoryType.EXPENSE);
        salary = category(ana, "Salário", CategoryType.INCOME);
        card = createCard(ana, "1000.00");
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void createsCardWithFullLimitAvailable() throws Exception {
        send(get(URL + "/" + card), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastFour").value("1234"))
                .andExpect(jsonPath("$.creditLimit").value(1000.00))
                .andExpect(jsonPath("$.usedLimit").value(0))
                .andExpect(jsonPath("$.availableLimit").value(1000.00));
    }

    @Test
    void neverAcceptsOrExposesSensitiveCardData() throws Exception {
        send(post(URL), ana, """
                {"nickname":"Teste","brand":"VISA","lastFour":"4242424242424242","creditLimit":10,"closingDay":5,"dueDay":15}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.lastFour").exists());

        send(post(URL), ana, """
                {"nickname":"Teste","brand":"VISA","lastFour":"4242","creditLimit":10,"closingDay":5,"dueDay":15,
                 "number":"4242424242424242","cvv":"123","password":"1234"}
                """)
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("4242424242424242"))))
                .andExpect(content().string(not(containsString("cvv"))));

        Integer sensitiveColumns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                 where table_name = 'credit_cards' and column_name in ('number', 'card_number', 'cvv', 'password')
                """, Integer.class);
        assertThat(sensitiveColumns).isZero();
    }

    @Test
    void rejectsInvalidCardData() throws Exception {
        send(post(URL), ana, """
                {"nickname":"","brand":"VISA","lastFour":"12a4","creditLimit":0,"closingDay":0,"dueDay":32}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nickname").exists())
                .andExpect(jsonPath("$.errors.lastFour").exists())
                .andExpect(jsonPath("$.errors.creditLimit").exists())
                .andExpect(jsonPath("$.errors.closingDay").exists())
                .andExpect(jsonPath("$.errors.dueDay").exists());
    }

    @Test
    void singlePurchaseGoesToOneInvoiceAndUsesLimit() throws Exception {
        purchase("Mercado", "150.75", 1, DATE).andExpect(status().isCreated());

        limit().andExpect(jsonPath("$.usedLimit").value(150.75)).andExpect(jsonPath("$.availableLimit").value(849.25));
        invoices()
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].referenceMonth").value("2026-03"))
                .andExpect(jsonPath("$[0].closingDate").value("2026-03-15"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-03-25"))
                .andExpect(jsonPath("$[0].totalAmount").value(150.75));
    }

    @Test
    void installmentsSpreadAcrossInvoicesWithExactCents() throws Exception {
        purchase("Celular", "100.00", 3, DATE).andExpect(status().isCreated());

        invoices()
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].referenceMonth", contains("2026-05", "2026-04", "2026-03")))
                .andExpect(jsonPath("$[*].totalAmount", contains(33.33, 33.33, 33.34)));
        limit().andExpect(jsonPath("$.usedLimit").value(100.00));

        send(get("/api/v1/transactions").param("sort", "transactionDate,asc"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].description").value("Celular (1/3)"))
                .andExpect(jsonPath("$.content[0].kind").value("CARD_INSTALLMENT"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].transactionDate").value("2026-03-10"))
                .andExpect(jsonPath("$.content[2].transactionDate").value("2026-05-10"));
    }

    @Test
    void purchaseAfterClosingGoesToNextInvoice() throws Exception {
        purchase("No fechamento", "10.00", 1, "2026-03-15");
        purchase("Depois", "20.00", 1, "2026-03-16");

        invoices()
                .andExpect(jsonPath("$[*].referenceMonth", contains("2026-04", "2026-03")))
                .andExpect(jsonPath("$[*].totalAmount", contains(20.00, 10.00)));
    }

    @Test
    void purchasesShareInvoiceOfSameMonth() throws Exception {
        purchase("A", "10.00", 1, DATE);
        purchase("B", "5.50", 1, "2026-03-12");

        invoices().andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].totalAmount").value(15.50));
    }

    @Test
    void purchaseAboveAvailableLimitIsRejectedAndChangesNothing() throws Exception {
        purchase("Grande", "1000.00", 1, DATE).andExpect(status().isCreated());

        purchase("Mais uma", "0.01", 1, DATE).andExpect(status().isUnprocessableContent());

        limit().andExpect(jsonPath("$.availableLimit").value(0));
    }

    @Test
    void futureInstallmentsOccupyLimit() throws Exception {
        purchase("Parcelada", "900.00", 9, DATE);

        purchase("Acima", "100.01", 1, DATE).andExpect(status().isUnprocessableContent());
        purchase("Exato", "100.00", 1, DATE).andExpect(status().isCreated());
    }

    @Test
    void concurrentPurchasesNeverExceedLimit() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            start.await();
            return purchase("Concorrente", "300.00", 1, DATE).andReturn().getResponse().getStatus();
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

        assertThat(statuses.stream().filter(status -> status == 201)).hasSize(3);
        assertThat(statuses.stream().filter(status -> status == 422)).hasSize(5);
        limit().andExpect(jsonPath("$.usedLimit").value(900.00));
        invoices().andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void reducedLimitShowsZeroAvailable() throws Exception {
        purchase("Compra", "800.00", 1, DATE);

        send(put(URL + "/" + card), ana, cardBody("Roxo", "500.00", 15, 25))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedLimit").value(800.00))
                .andExpect(jsonPath("$.availableLimit").value(0));
    }

    @Test
    void partialPaymentKeepsInvoiceOpenAndReleasesLimitProportionally() throws Exception {
        purchase("Compra", "300.00", 1, DATE);
        UUID invoice = firstInvoice();

        pay(invoice, "100.00", account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmount").value(100.00))
                .andExpect(jsonPath("$.remainingAmount").value(200.00));

        limit().andExpect(jsonPath("$.availableLimit").value(800.00));
        balance(4900.00);
        send(get("/api/v1/transactions").param("status", "PENDING"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void fullPaymentMarksPaidCompletesInstallmentsAndFreesLimit() throws Exception {
        purchase("Compra", "300.00", 1, DATE);
        UUID invoice = firstInvoice();

        pay(invoice, "300.00", account)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.remainingAmount").value(0));

        limit().andExpect(jsonPath("$.availableLimit").value(1000.00));
        balance(4700.00);
        send(get("/api/v1/transactions").param("status", "PENDING"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void paymentCannotExceedRemaining() throws Exception {
        purchase("Compra", "100.00", 1, DATE);
        UUID invoice = firstInvoice();
        pay(invoice, "60.00", account);

        pay(invoice, "40.01", account).andExpect(status().isUnprocessableContent());

        balance(4940.00);
    }

    @Test
    void paymentRejectsInvalidValuesAndArchivedAccount() throws Exception {
        purchase("Compra", "100.00", 1, DATE);
        UUID invoice = firstInvoice();

        pay(invoice, "0", account).andExpect(status().isBadRequest());

        Account stored = accountRepository.findById(account).orElseThrow();
        stored.update(stored.getName(), stored.getType(), stored.getInitialBalance(), true);
        accountRepository.save(stored);
        pay(invoice, "10.00", account).andExpect(status().isUnprocessableContent());
    }

    @Test
    void cardPurchaseCountsOnceInDashboardAndBudget() throws Exception {
        purchase("Compra", "300.00", 1, DATE);
        pay(firstInvoice(), "300.00", account);

        send(get("/api/v1/dashboard/summary").param("from", "2026-03-01").param("to", "2026-03-31"), ana, null)
                .andExpect(jsonPath("$.expenses").value(300.00));
        send(post("/api/v1/budgets"), ana, """
                {"categoryId":"%s","month":"2026-03","limitAmount":1000}
                """.formatted(food))
                .andExpect(jsonPath("$.usedAmount").value(300.00));
    }

    @Test
    void cancelFreesLimitAndCancelsInstallments() throws Exception {
        UUID purchaseId = purchaseId("Compra", "300.00", 3, DATE);

        send(delete(URL + "/" + card + "/purchases/" + purchaseId), ana, null).andExpect(status().isNoContent());

        limit().andExpect(jsonPath("$.availableLimit").value(1000.00));
        send(get("/api/v1/transactions").param("status", "CANCELED"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(3));
        send(get(URL + "/" + card + "/purchases"), ana, null).andExpect(jsonPath("$.content[0].canceled").value(true));
    }

    @Test
    void purchaseWithPaymentCannotBeCanceled() throws Exception {
        UUID purchaseId = purchaseId("Compra", "300.00", 1, DATE);
        pay(firstInvoice(), "1.00", account);

        send(delete(URL + "/" + card + "/purchases/" + purchaseId), ana, null)
                .andExpect(status().isUnprocessableContent());

        limit().andExpect(jsonPath("$.usedLimit").value(299.00));
    }

    @Test
    void invoiceDetailListsInstallments() throws Exception {
        purchase("Parcelada", "100.00", 3, DATE);
        UUID invoice = firstInvoice(); // A mais recente é a da parcela 3.

        send(get(URL + "/" + card + "/invoices/" + invoice), ana, null)
                .andExpect(jsonPath("$.invoice.totalAmount").value(33.33))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0].description").value("Parcelada"))
                .andExpect(jsonPath("$.lines[0].number").value(3))
                .andExpect(jsonPath("$.lines[0].installments").value(3));
    }

    @Test
    void invoiceStatusesAreDerivedFromDates() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        purchase("Antiga", "10.00", 1, today.minusMonths(6).toString());
        purchase("Atual", "10.00", 1, today.plusMonths(6).toString());

        invoices()
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[1].status").value("OVERDUE"));
    }

    @Test
    void installmentCannotBeEditedThroughTransactionsApi() throws Exception {
        purchase("Compra", "10.00", 1, DATE);
        String list = send(get("/api/v1/transactions"), ana, null).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(list, "$.content[0].id");

        send(delete("/api/v1/transactions/" + id), ana, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void purchaseValidatesCategoryAndCard() throws Exception {
        purchaseWith(salary, "10.00", 1).andExpect(status().isUnprocessableContent());
        purchaseWith(food, "0.02", 3).andExpect(status().isUnprocessableContent());
        purchaseWith(food, "10.00", 49).andExpect(status().isBadRequest());
        purchaseWith(food, "0", 1).andExpect(status().isBadRequest());

        send(put(URL + "/" + card), ana, cardBody("Roxo", "1000.00", 15, 25).replace("}", ",\"archived\":true}"))
                .andExpect(jsonPath("$.archived").value(true));
        purchaseWith(food, "10.00", 1).andExpect(status().isUnprocessableContent());
    }

    @Test
    void cardWithPurchasesCannotBeDeletedButEmptyCardCan() throws Exception {
        purchase("Compra", "10.00", 1, DATE);
        send(delete(URL + "/" + card), ana, null).andExpect(status().isConflict());

        UUID empty = createCard(ana, "100.00");
        send(delete(URL + "/" + empty), ana, null).andExpect(status().isNoContent());
        assertThat(cardRepository.findById(empty)).isEmpty();
    }

    @Test
    void deletingCategoryMovesPurchasesAndInstallments() throws Exception {
        purchase("Compra", "30.00", 3, DATE);
        UUID target = category(ana, "Destino", CategoryType.EXPENSE);

        send(get("/api/v1/categories/" + food + "/usage"), ana, null).andExpect(jsonPath("$.references").value(4));
        send(delete("/api/v1/categories/" + food).param("reassignTo", target.toString()), ana, null)
                .andExpect(status().isNoContent());

        send(get("/api/v1/transactions"), ana, null).andExpect(jsonPath("$.content[0].categoryName").value("Destino"));
    }

    @Test
    void anotherUserHasNoAccess() throws Exception {
        UUID purchaseId = purchaseId("Compra", "100.00", 1, DATE);
        UUID invoice = firstInvoice();
        UUID brunoAccount = accountRepository.save(new Account(bruno, "B", AccountType.CHECKING, BigDecimal.TEN)).getId();

        send(get(URL + "/" + card), bruno, null).andExpect(status().isNotFound());
        send(put(URL + "/" + card), bruno, cardBody("X", "1.00", 1, 2)).andExpect(status().isNotFound());
        send(delete(URL + "/" + card), bruno, null).andExpect(status().isNotFound());
        send(get(URL + "/" + card + "/purchases"), bruno, null).andExpect(status().isNotFound());
        send(post(URL + "/" + card + "/purchases"), bruno, purchaseBody("X", "1.00", 1, food, DATE)).andExpect(status().isNotFound());
        send(delete(URL + "/" + card + "/purchases/" + purchaseId), bruno, null).andExpect(status().isNotFound());
        send(get(URL + "/" + card + "/invoices"), bruno, null).andExpect(status().isNotFound());
        send(get(URL + "/" + card + "/invoices/" + invoice), bruno, null).andExpect(status().isNotFound());
        send(post(URL + "/" + card + "/invoices/" + invoice + "/payments"), bruno, paymentBody("1.00", brunoAccount)).andExpect(status().isNotFound());
        send(get(URL), bruno, null).andExpect(jsonPath("$", hasSize(0)));

        // Nem o dono pode usar conta ou categoria de outra pessoa.
        pay(invoice, "1.00", brunoAccount).andExpect(status().isNotFound());
        limit().andExpect(jsonPath("$.usedLimit").value(100.00));
    }

    @Test
    void invoiceOfAnotherCardIsNotReachable() throws Exception {
        purchase("Compra", "100.00", 1, DATE);
        UUID invoice = firstInvoice();
        UUID other = createCard(ana, "100.00");

        send(get(URL + "/" + other + "/invoices/" + invoice), ana, null).andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void cancelingTwiceIsHarmlessAndCountsOnce() throws Exception {
        UUID purchaseId = purchaseId("Compra", "300.00", 3, DATE);

        send(delete(URL + "/" + card + "/purchases/" + purchaseId), ana, null).andExpect(status().isNoContent());
        send(delete(URL + "/" + card + "/purchases/" + purchaseId), ana, null).andExpect(status().isNoContent());

        limit().andExpect(jsonPath("$.availableLimit").value(1000.00));
        send(get("/api/v1/transactions").param("status", "CANCELED"), ana, null)
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void purchaseOfAnotherCardCannotBeCanceledThroughThisCard() throws Exception {
        UUID purchaseId = purchaseId("Compra", "100.00", 1, DATE);
        UUID other = createCard(ana, "500.00");

        send(delete(URL + "/" + other + "/purchases/" + purchaseId), ana, null).andExpect(status().isNotFound());

        limit().andExpect(jsonPath("$.usedLimit").value(100.00));
    }

    @Test
    void archivedCategoryIsRejectedForPurchases() throws Exception {
        jdbcTemplate.update("update categories set archived = true where id = ?", food);

        purchaseWith(food, "10.00", 1).andExpect(status().isUnprocessableContent());
        limit().andExpect(jsonPath("$.usedLimit").value(0));
    }

    @Test
    void purchaseListRejectsInvalidPaging() throws Exception {
        send(get(URL + "/" + card + "/purchases").param("page", "-1"), ana, null).andExpect(status().isBadRequest());
        send(get(URL + "/" + card + "/purchases").param("size", "0"), ana, null).andExpect(status().isBadRequest());
        send(get(URL + "/" + card + "/purchases").param("size", "101"), ana, null).andExpect(status().isBadRequest());
    }

    private ResultActions limit() throws Exception {
        return send(get(URL + "/" + card), ana, null);
    }

    private ResultActions invoices() throws Exception {
        return send(get(URL + "/" + card + "/invoices"), ana, null);
    }

    private void balance(double expected) throws Exception {
        send(get("/api/v1/accounts/" + account), ana, null).andExpect(jsonPath("$.currentBalance").value(expected));
    }

    private UUID firstInvoice() throws Exception {
        String response = invoices().andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$[0].id"));
    }

    private ResultActions purchase(String description, String amount, int installments, String date) throws Exception {
        return send(post(URL + "/" + card + "/purchases"), ana, purchaseBody(description, amount, installments, food, date));
    }

    private ResultActions purchaseWith(UUID category, String amount, int installments) throws Exception {
        return send(post(URL + "/" + card + "/purchases"), ana, purchaseBody("X", amount, installments, category, DATE));
    }

    private UUID purchaseId(String description, String amount, int installments, String date) throws Exception {
        String response = purchase(description, amount, installments, date)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private ResultActions pay(UUID invoice, String amount, UUID accountId) throws Exception {
        return send(post(URL + "/" + card + "/invoices/" + invoice + "/payments"), ana, paymentBody(amount, accountId));
    }

    private UUID createCard(UUID user, String limit) throws Exception {
        String response = send(post(URL), user, cardBody("Roxo", limit, 15, 25))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private UUID category(UUID user, String name, CategoryType type) {
        return categoryRepository.save(new Category(user, name, type, "Home", "#7C4DFF")).getId();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID user, String json) throws Exception {
        request.with(jwt().jwt(token -> token.subject(user.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String cardBody(String nickname, String limit, int closing, int due) {
        return """
                {"nickname":"%s","brand":"VISA","lastFour":"1234","creditLimit":%s,"closingDay":%d,"dueDay":%d}""".formatted(nickname, limit, closing, due);
    }

    private String purchaseBody(String description, String amount, int installments, UUID category, String date) {
        return """
                {"description":"%s","totalAmount":%s,"installments":%d,"categoryId":"%s","purchaseDate":"%s"}
                """.formatted(description, amount, installments, category, date);
    }

    private String paymentBody(String amount, UUID accountId) {
        return """
                {"accountId":"%s","amount":%s,"paymentDate":"2026-03-20"}
                """.formatted(accountId, amount);
    }
}
