package com.finantrack.common.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Cria respostas de erro com título em português, iguais em toda a API. */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title(status));
        return problem;
    }

    public static String title(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida";
            case 401 -> "Não autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Não encontrado";
            case 405 -> "Método não permitido";
            case 406 -> "Formato não aceito";
            case 409 -> "Conflito";
            case 415 -> "Formato não suportado";
            case 422 -> "Operação não permitida";
            case 429 -> "Muitas tentativas";
            default -> status.is5xxServerError() ? "Erro interno" : "Erro na requisição";
        };
    }
}
