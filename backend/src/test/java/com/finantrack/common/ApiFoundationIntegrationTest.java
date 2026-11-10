package com.finantrack.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finantrack.common.exception.BusinessRuleException;
import com.finantrack.common.exception.ConflictException;
import com.finantrack.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiFoundationIntegrationTest.ProbeController.class)
class ApiFoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anyOtherRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/probe/not-found")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void swaggerIsClosedOutsideDev() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/probe/not-found")
                        .header("Origin", "http://localhost:5080")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5080"));
    }

    @Test
    void corsRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/probe/not-found")
                        .header("Origin", "https://site-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void notFoundUsesProblemDetail() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Registro não encontrado."));
    }

    @Test
    @WithMockUser
    void conflictReturns409() throws Exception {
        mockMvc.perform(get("/probe/conflict")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void businessRuleReturns422() throws Exception {
        mockMvc.perform(get("/probe/business-rule")).andExpect(status().isUnprocessableContent());
    }

    @Test
    @WithMockUser
    void unexpectedErrorDoesNotLeakDetails() throws Exception {
        mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("segredo-interno"))));
    }

    @Test
    @WithMockUser
    void validationErrorListsFields() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    record ProbeRequest(@NotBlank String name) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Registro não encontrado.");
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new ConflictException("Conflito.");
        }

        @GetMapping("/probe/business-rule")
        void businessRule() {
            throw new BusinessRuleException("Regra violada.");
        }

        @GetMapping("/probe/unexpected")
        void unexpected() {
            throw new IllegalStateException("segredo-interno");
        }

        @PostMapping("/probe/validation")
        void validation(@Valid @RequestBody ProbeRequest request) {
        }
    }
}
