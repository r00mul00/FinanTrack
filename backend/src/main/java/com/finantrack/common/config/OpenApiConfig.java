package com.finantrack.common.config;

import com.finantrack.common.exception.Problems;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String PROBLEM = "Problema";
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Respostas de erro comuns, acrescentadas pelo customizador quando a operação admite o caso. */
    private static final Map<String, String> COMMON_ERRORS = Map.of(
            "400", "Parâmetros ou corpo inválidos; os erros por campo vêm em \"errors\".",
            "401", "Sem token, ou com token expirado ou inválido.",
            "404", "O recurso não existe ou pertence a outro usuário.");

    @Bean
    public OpenAPI finanTrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinanTrack API")
                        .description("""
                                Controle suas finanças. Conquiste seus objetivos.

                                **Autenticação:** `POST /api/v1/auth/login` devolve o access token (JWT, 15 minutos) \
                                e grava o refresh token em um cookie HttpOnly. Envie o access token no botão \
                                **Authorize** (`Bearer`). Quando ele expirar, `POST /api/v1/auth/refresh` emite outro.

                                **Erros:** sempre no formato Problem Details (RFC 9457), com mensagens em português.

                                **Valores e datas:** dinheiro com duas casas decimais, datas em ISO 8601 \
                                (`2026-11-10`) e meses como `2026-11`.""")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /**
     * Completa cada operação com as respostas de erro que ela pode dar e usa o
     * esquema de erro em todas as 4xx, inclusive nas declaradas nos controllers.
     */
    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            // Registrado aqui, e não no bean acima: o springdoc refaz os componentes ao gerar o documento.
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            openApi.getPaths().forEach((path, item) ->
                    item.readOperations().forEach(operation -> addErrors(path, operation)));
        };
    }

    private static void addErrors(String path, Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (isSecured(operation)) {
            addIfAbsent(responses, "401");
        }
        if (operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty())) {
            addIfAbsent(responses, "400");
        }
        if (path.contains("{")) {
            addIfAbsent(responses, "404");
        }
        // Sem isso, um @ApiResponse de erro declarado no controller herdaria o esquema de sucesso.
        responses.forEach((code, response) -> {
            if (code.startsWith("4")) {
                response.setContent(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))
                        .example(example(Integer.parseInt(code), response.getDescription()))));
            }
        });
    }

    /** Exemplo próprio de cada resposta: o mesmo status, título e mensagem que a API devolveria. */
    private static Map<String, Object> example(int status, String description) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("title", Problems.title(HttpStatusCode.valueOf(status)));
        example.put("status", status);
        if (status == 400) {
            example.put("detail", "Existem campos inválidos.");
            example.put("errors", Map.of("amount", "O valor deve ser maior que zero."));
        } else {
            example.put("detail", description);
        }
        if (description != null && description.contains("EMAIL_NOT_VERIFIED")) {
            example.put("detail", "Confirme seu e-mail para entrar.");
            example.put("code", "EMAIL_NOT_VERIFIED");
        }
        return example;
    }

    /** Rotas marcadas com @SecurityRequirements() vazio, como as de /auth, são públicas. */
    private static boolean isSecured(Operation operation) {
        return operation.getSecurity() == null || !operation.getSecurity().isEmpty();
    }

    private static void addIfAbsent(ApiResponses responses, String code) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse().description(COMMON_ERRORS.get(code)));
        }
    }

    @SuppressWarnings("rawtypes")
    private static Schema problemSchema() {
        return new ObjectSchema()
                .description("Erro no formato Problem Details (RFC 9457).")
                .addProperty("title", new StringSchema().description("Título do status, em português."))
                .addProperty("status", new IntegerSchema().description("Código HTTP."))
                .addProperty("detail", new StringSchema().description("Mensagem para exibir ao usuário."))
                .addProperty("instance", new StringSchema().description("Caminho da requisição."))
                .addProperty("code", new StringSchema()
                        .description("Código para o cliente tratar o caso, quando houver (ex.: EMAIL_NOT_VERIFIED)."))
                .addProperty("errors", new ObjectSchema()
                        .description("Erros de validação, por campo.")
                        .additionalProperties(new StringSchema()));
    }
}
