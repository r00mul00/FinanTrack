package com.finantrack.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converte exceções em respostas RFC 9457, em português e sem detalhes internos.
 * O frontend exibe o primeiro item de "errors" ou, na falta dele, o "detail".
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String INVALID_FIELDS = "Existem campos inválidos.";
    static final String INVALID_VALUE = "Valor inválido.";
    static final String UNEXPECTED = "Ocorreu um erro inesperado. Tente novamente.";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return Problems.of(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ProblemDetail handleInvalidParameter(InvalidParameterException ex) {
        return Problems.of(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return Problems.of(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return Problems.of(HttpStatus.CONFLICT,
                "O registro foi alterado por outra operação. Recarregue e tente novamente.");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return Problems.of(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setProperty("code", "EMAIL_NOT_VERIFIED");
        return problem;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException ex) {
        return Problems.of(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return Problems.of(HttpStatus.FORBIDDEN, "Acesso negado.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED);
    }

    /** Falha de conversão (texto em campo de data, por exemplo) não expõe o tipo Java esperado. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.isBindingFailure() ? INVALID_VALUE : error.getDefaultMessage());
        }

        ProblemDetail problem = Problems.of(status, INVALID_FIELDS);
        problem.setProperty("errors", errors);
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Ponto único por onde passam as exceções tratadas pelo Spring MVC. A mensagem
     * padrão delas vem em inglês e pode citar classes internas; aqui é substituída.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Erro inesperado", ex);
        }
        return super.handleExceptionInternal(ex, Problems.of(status, frameworkDetail(ex, status)), headers, status,
                request);
    }

    private static String frameworkDetail(Exception ex, HttpStatusCode status) {
        return switch (ex) {
            case HttpMessageNotReadableException ignored -> "O corpo da requisição está ausente ou mal formatado.";
            case TypeMismatchException mismatch when mismatch.getPropertyName() != null ->
                    "O valor informado para \"%s\" é inválido.".formatted(mismatch.getPropertyName());
            case MissingServletRequestParameterException missing ->
                    "Informe o parâmetro \"%s\".".formatted(missing.getParameterName());
            case NoResourceFoundException ignored -> "Endereço não encontrado.";
            case HttpRequestMethodNotSupportedException ignored -> "Método HTTP não permitido neste endereço.";
            case HttpMediaTypeNotSupportedException ignored -> "Formato de conteúdo não suportado. Envie JSON.";
            case HttpMediaTypeNotAcceptableException ignored -> "Formato de resposta não suportado.";
            default -> status.is5xxServerError() ? UNEXPECTED : "Requisição inválida.";
        };
    }
}
