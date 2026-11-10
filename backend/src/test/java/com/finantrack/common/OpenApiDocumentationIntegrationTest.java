package com.finantrack.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Garante que a documentação do Swagger acompanhe a API: nenhum endpoint sem descrição. */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "finantrack.security.swagger-public=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationIntegrationTest {

    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    private JsonNode spec;

    @BeforeEach
    void loadSpec() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        spec = JsonMapper.builder().build().readTree(json);
    }

    @Test
    void everyOperationHasSummary() {
        List<String> missing = new ArrayList<>();
        operations().forEach((name, operation) -> {
            if (operation.path("summary").asString("").isBlank()) {
                missing.add(name);
            }
        });
        assertThat(missing).isEmpty();
    }

    @Test
    void authRoutesArePublicAndOthersRequireBearer() {
        operations().forEach((name, operation) -> {
            JsonNode security = operation.get("security");
            if (name.contains("/api/v1/auth/")) {
                assertThat(security).as(name).isNotNull();
                assertThat(security.isEmpty()).as(name).isTrue();
                // Rota pública não recebe o 401 genérico; só login e refresh declaram o seu.
                boolean declaresOwn401 = name.endsWith("/login") || name.endsWith("/refresh");
                assertThat(operation.path("responses").has("401")).as(name).isEqualTo(declaresOwn401);
            } else {
                assertThat(security == null || !security.isEmpty()).as(name).isTrue();
                assertThat(operation.path("responses").has("401")).as(name).isTrue();
            }
        });
    }

    @Test
    void errorResponsesUseProblemDetails() {
        JsonNode problem = spec.path("components").path("schemas").path("Problema").path("properties");
        assertThat(problem.has("detail") && problem.has("errors") && problem.has("code")).isTrue();

        operations().forEach((name, operation) -> operation.path("responses").properties().forEach(response -> {
            if (response.getKey().startsWith("4")) {
                JsonNode media = response.getValue().path("content").path("application/problem+json");
                String label = name + " " + response.getKey();
                assertThat(media.path("schema").path("$ref").asString("")).as(label)
                        .isEqualTo("#/components/schemas/Problema");
                assertThat(media.path("example").path("status").asInt()).as(label)
                        .isEqualTo(Integer.parseInt(response.getKey()));
            }
        }));
    }

    @Test
    void businessRulesAreDocumented() {
        JsonNode purchase = spec.path("paths").path("/api/v1/cards/{id}/purchases").path("post");
        assertThat(purchase.path("responses").has("422")).isTrue();
        assertThat(purchase.path("description").asString()).contains("fechamento");
    }

    private Map<String, JsonNode> operations() {
        Map<String, JsonNode> result = new java.util.LinkedHashMap<>();
        spec.path("paths").properties().forEach(path -> METHODS.forEach(method -> {
            JsonNode operation = path.getValue().get(method);
            if (operation != null) {
                result.put(method.toUpperCase() + " " + path.getKey(), operation);
            }
        }));
        assertThat(result).hasSize(76);
        return result;
    }
}
