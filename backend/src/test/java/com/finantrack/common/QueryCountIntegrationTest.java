package com.finantrack.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.notification.service.NotificationJob;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Detecta N+1: o número de instruções SQL de cada leitura não pode crescer
 * com a quantidade de registros do usuário. Conta só o que passa pelo Hibernate;
 * a exportação de dados usa JDBC, com uma consulta fixa por seção.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueryCountIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final String MONTH = YearMonth.from(TODAY).toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationJob notificationJob;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Statistics statistics;
    private UUID ana;
    private UUID firstCard;
    private int rounds;
    private String categoryOfFirstRound;

    @BeforeEach
    void setUp() {
        clean();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void readsDoNotGrowWithTheNumberOfRecords() throws Exception {
        seedRound();
        Map<String, Long> few = measureReads();

        for (int i = 0; i < 4; i++) {
            seedRound();
        }
        Map<String, Long> many = measureReads();

        assertThat(many).isEqualTo(few);
    }

    @Test
    void installmentPurchaseDoesNotGrowWithTheNumberOfInstallments() throws Exception {
        seedRound();
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int installments : List.of(1, 12, 48)) {
            statistics.clear();
            send(post("/api/v1/cards/" + firstCard + "/purchases"), """
                    {"description":"Parcelada","totalAmount":480.00,"installments":%d,"categoryId":"%s","purchaseDate":"%s"}
                    """.formatted(installments, categoryOfFirstRound, TODAY)).andExpect(status().isCreated());
            counts.put(installments, statistics.getPrepareStatementCount());
        }

        // Os INSERTs de cada tabela vão em lote; o número de instruções preparadas não muda.
        assertThat(counts.get(48)).isEqualTo(counts.get(12));
        assertThat(counts.get(48)).isLessThanOrEqualTo(counts.get(1) + 2);
    }

    private Map<String, Long> measureReads() throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map.Entry<String, MockHttpServletRequestBuilder> read : reads().entrySet()) {
            statistics.clear();
            send(read.getValue(), null).andExpect(status().isOk());
            counts.put(read.getKey(), statistics.getPrepareStatementCount());
        }
        return counts;
    }

    private Map<String, MockHttpServletRequestBuilder> reads() {
        Map<String, MockHttpServletRequestBuilder> reads = new LinkedHashMap<>();
        reads.put("contas", get("/api/v1/accounts"));
        reads.put("categorias", get("/api/v1/categories"));
        reads.put("transações", get("/api/v1/transactions"));
        reads.put("orçamentos", get("/api/v1/budgets").param("month", MONTH));
        reads.put("metas", get("/api/v1/goals"));
        reads.put("cartões", get("/api/v1/cards"));
        reads.put("compras", get("/api/v1/cards/" + firstCard + "/purchases"));
        reads.put("faturas", get("/api/v1/cards/" + firstCard + "/invoices"));
        reads.put("recorrências", get("/api/v1/recurring"));
        reads.put("notificações", get("/api/v1/notifications"));
        reads.put("dashboard: resumo", get("/api/v1/dashboard/summary"));
        reads.put("dashboard: vencimentos", get("/api/v1/dashboard/upcoming"));
        reads.put("dashboard: categorias", get("/api/v1/dashboard/expenses-by-category"));
        reads.put("dashboard: receitas x despesas", get("/api/v1/dashboard/income-vs-expense"));
        for (String report : List.of("monthly", "by-category", "by-period", "evolution", "by-card")) {
            reads.put("relatório: " + report, get("/api/v1/reports/" + report).param("month", MONTH));
        }
        reads.put("pesquisa", get("/api/v1/search").param("q", "Item"));
        return reads;
    }

    /** Cada rodada cria um conjunto completo de registros relacionados entre si. */
    private void seedRound() throws Exception {
        int n = ++rounds;
        String account = id(post("/api/v1/accounts"),
                "{\"name\":\"Conta %d\",\"type\":\"CHECKING\",\"initialBalance\":1000}".formatted(n));
        String category = id(post("/api/v1/categories"),
                "{\"name\":\"Item categoria %d\",\"type\":\"EXPENSE\",\"icon\":\"Home\",\"color\":\"#7C4DFF\"}".formatted(n));

        for (String status : List.of("COMPLETED", "PENDING")) {
            send(post("/api/v1/transactions"), """
                    {"description":"Item %d","amount":10.00,"type":"EXPENSE","status":"%s","categoryId":"%s",
                     "accountId":"%s","transactionDate":"%s","dueDate":"%s"}
                    """.formatted(n, status, category, account, TODAY, TODAY.plusDays(2)))
                    .andExpect(status().isCreated());
        }
        send(post("/api/v1/budgets"), "{\"categoryId\":\"%s\",\"month\":\"%s\",\"limitAmount\":15}"
                .formatted(category, MONTH)).andExpect(status().isCreated());
        send(post("/api/v1/goals"), "{\"name\":\"Item meta %d\",\"targetAmount\":100,\"initialAmount\":10,\"deadline\":\"%s\"}"
                .formatted(n, TODAY.plusYears(1))).andExpect(status().isCreated());

        String card = id(post("/api/v1/cards"),
                "{\"nickname\":\"Item cartão %d\",\"brand\":\"VISA\",\"lastFour\":\"1234\",\"creditLimit\":5000,\"closingDay\":5,\"dueDay\":15}"
                        .formatted(n));
        if (firstCard == null) {
            firstCard = UUID.fromString(card);
            categoryOfFirstRound = category;
        }
        for (String target : List.of(card, firstCard.toString())) {
            send(post("/api/v1/cards/" + target + "/purchases"), """
                    {"description":"Item compra %d","totalAmount":30.00,"installments":3,"categoryId":"%s","purchaseDate":"%s"}
                    """.formatted(n, category, TODAY)).andExpect(status().isCreated());
        }

        send(post("/api/v1/recurring"), """
                {"description":"Item recorrente %d","amount":50.00,"type":"EXPENSE","categoryId":"%s","accountId":"%s",
                 "frequency":"MONTHLY","startDate":"%s"}
                """.formatted(n, category, account, TODAY.plusDays(1))).andExpect(status().isCreated());

        notificationJob.run(TODAY);
    }

    private String id(MockHttpServletRequestBuilder request, String json) throws Exception {
        String body = send(request, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder request, String json)
            throws Exception {
        request.with(jwt().jwt(token -> token.subject(ana.toString())));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }
}
