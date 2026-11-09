package com.finantrack.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.auth.service.EmailSender;
import com.finantrack.category.entity.Category;
import com.finantrack.category.entity.CategoryType;
import com.finantrack.category.repository.CategoryRepository;
import com.finantrack.category.service.CategoryReferenceHandler;
import com.finantrack.user.entity.User;
import com.finantrack.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CategoryIntegrationTest.Config.class)
class CategoryIntegrationTest {

    private static final String URL = "/api/v1/categories";

    /** Simula um módulo que referencia categorias, como as transações farão. */
    static class FakeReferences implements CategoryReferenceHandler {
        final Map<UUID, Long> counts = new HashMap<>();

        @Override
        public long countReferences(UUID categoryId) {
            return counts.getOrDefault(categoryId, 0L);
        }

        @Override
        public void reassign(UUID from, UUID to) {
            counts.merge(to, counts.getOrDefault(from, 0L), Long::sum);
            counts.remove(from);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        FakeReferences fakeReferences() {
            return new FakeReferences();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeReferences references;

    @MockitoBean
    private EmailSender emailSender;

    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void setUp() {
        clean();
        references.counts.clear();
        ana = userRepository.save(new User("Ana", "ana@exemplo.com", "hash")).getId();
        bruno = userRepository.save(new User("Bruno", "bruno@exemplo.com", "hash")).getId();
    }

    @AfterEach
    void clean() {
        jdbcTemplate.execute("truncate table users cascade");
    }

    @Test
    void registrationCreatesDefaultCategories() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nova\",\"email\":\"nova@exemplo.com\",\"password\":\"senha-bem-segura\"}"))
                .andExpect(status().isAccepted());

        UUID user = userRepository.findByEmail("nova@exemplo.com").orElseThrow().getId();
        assertThat(categoryRepository.findAllByUserIdAndTypeOrderByNameAsc(user, CategoryType.EXPENSE)).hasSize(9);
        assertThat(categoryRepository.findAllByUserIdAndTypeOrderByNameAsc(user, CategoryType.INCOME)).hasSize(4);
    }

    @Test
    void createsAndFiltersByTypeReturningOnlyOwnCategories() throws Exception {
        create(ana, "Mercado", "EXPENSE");
        create(ana, "Bônus", "INCOME");
        create(bruno, "Do Bruno", "EXPENSE");

        send(get(URL).param("type", "EXPENSE"), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Mercado"))
                .andExpect(jsonPath("$[*].type", everyItem(is("EXPENSE"))));
    }

    @Test
    void sameNameIsAllowedInDifferentTypesButNotInTheSameType() throws Exception {
        create(ana, "Outros", "EXPENSE");
        create(ana, "Outros", "INCOME");

        send(post(URL), ana, body(" OUTROS ", "EXPENSE")).andExpect(status().isConflict());
    }

    @Test
    void updatesNameIconColorAndArchive() throws Exception {
        UUID id = create(ana, "Antiga", "EXPENSE");

        send(put(URL + "/" + id), ana, """
                {"name":"Nova","type":"EXPENSE","icon":"Pets","color":"#112233","archived":true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nova"))
                .andExpect(jsonPath("$.icon").value("Pets"))
                .andExpect(jsonPath("$.color").value("#112233"))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void typeCannotBeChanged() throws Exception {
        UUID id = create(ana, "Fixa", "EXPENSE");

        send(put(URL + "/" + id), ana, body("Fixa", "INCOME")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void anotherUserCannotReadUpdateOrDelete() throws Exception {
        UUID id = create(ana, "Da Ana", "EXPENSE");

        send(get(URL + "/" + id), bruno, null).andExpect(status().isNotFound());
        send(get(URL + "/" + id + "/usage"), bruno, null).andExpect(status().isNotFound());
        send(put(URL + "/" + id), bruno, body("Invadida", "EXPENSE")).andExpect(status().isNotFound());
        send(delete(URL + "/" + id), bruno, null).andExpect(status().isNotFound());

        assertThat(categoryRepository.findById(id)).isPresent();
    }

    @Test
    void rejectsInvalidColorAndIcon() throws Exception {
        send(post(URL), ana, """
                {"name":"Ruim","type":"EXPENSE","icon":"<script>","color":"vermelho"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.icon").exists())
                .andExpect(jsonPath("$.errors.color").exists());
    }

    @Test
    void unusedCategoryIsDeletedDirectly() throws Exception {
        UUID id = create(ana, "Livre", "EXPENSE");

        send(get(URL + "/" + id + "/usage"), ana, null).andExpect(jsonPath("$.references").value(0));
        send(delete(URL + "/" + id), ana, null).andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(id)).isEmpty();
    }

    @Test
    void categoryInUseCannotBeDeletedWithoutDestination() throws Exception {
        UUID id = create(ana, "Em uso", "EXPENSE");
        references.counts.put(id, 3L);

        send(get(URL + "/" + id + "/usage"), ana, null).andExpect(jsonPath("$.references").value(3));
        send(delete(URL + "/" + id), ana, null).andExpect(status().isConflict());

        assertThat(categoryRepository.findById(id)).isPresent();
    }

    @Test
    void categoryInUseMovesReferencesToDestinationAndIsDeleted() throws Exception {
        UUID id = create(ana, "Em uso", "EXPENSE");
        UUID target = create(ana, "Destino", "EXPENSE");
        references.counts.put(id, 3L);

        send(delete(URL + "/" + id).param("reassignTo", target.toString()), ana, null)
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(id)).isEmpty();
        assertThat(references.countReferences(target)).isEqualTo(3L);
    }

    @Test
    void destinationMustBeValid() throws Exception {
        UUID id = create(ana, "Em uso", "EXPENSE");
        UUID income = create(ana, "Receita", "INCOME");
        UUID brunos = create(bruno, "Do Bruno", "EXPENSE");
        Category archived = new Category(ana, "Arquivada", CategoryType.EXPENSE, "Home", "#000000");
        archived.update("Arquivada", "Home", "#000000", true);
        UUID archivedId = categoryRepository.save(archived).getId();
        references.counts.put(id, 1L);

        deleteTo(id, id).andExpect(status().isUnprocessableContent());
        deleteTo(id, income).andExpect(status().isUnprocessableContent());
        deleteTo(id, archivedId).andExpect(status().isUnprocessableContent());
        deleteTo(id, brunos).andExpect(status().isNotFound());

        assertThat(categoryRepository.findById(id)).isPresent();
        assertThat(references.countReferences(id)).isEqualTo(1L);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void getsOneAndListsBothTypesWithoutFilter() throws Exception {
        UUID id = create(ana, "Mercado", "EXPENSE");
        create(ana, "Bônus", "INCOME");
        create(bruno, "Do Bruno", "EXPENSE");

        send(get(URL + "/" + id), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mercado"))
                .andExpect(jsonPath("$.type").value("EXPENSE"));
        send(get(URL), ana, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Mercado", "Bônus")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Do Bruno"))));
    }

    @Test
    void renamingOntoAnotherCategoryOfSameTypeIsRejected() throws Exception {
        create(ana, "Primeira", "EXPENSE");
        UUID second = create(ana, "Segunda", "EXPENSE");

        send(put(URL + "/" + second), ana, body(" primeira ", "EXPENSE")).andExpect(status().isConflict());
        send(put(URL + "/" + second), ana, body("SEGUNDA", "EXPENSE")).andExpect(status().isOk());
    }

    private ResultActions deleteTo(UUID id, UUID target) throws Exception {
        return send(delete(URL + "/" + id).param("reassignTo", target.toString()), ana, null);
    }

    private UUID create(UUID user, String name, String type) throws Exception {
        String response = send(post(URL), user, body(name, type))
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

    private String body(String name, String type) {
        return """
                {"name":"%s","type":"%s","icon":"Home","color":"#7C4DFF"}
                """.formatted(name, type);
    }
}
