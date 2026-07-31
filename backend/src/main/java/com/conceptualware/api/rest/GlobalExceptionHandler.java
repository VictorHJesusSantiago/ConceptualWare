package com.conceptualware.api.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private static final URI TYPE_VALIDATION  = URI.create("https://conceptualware.dev/problems/validation-error");
    private static final URI TYPE_MALFORMED   = URI.create("https://conceptualware.dev/problems/malformed-request");
    private static final URI TYPE_CONSTRAINT  = URI.create("https://conceptualware.dev/problems/constraint-violation");
    private static final URI TYPE_INTERNAL    = URI.create("https://conceptualware.dev/problems/internal-error");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(TYPE_VALIDATION);
        problem.setTitle("Falha de validação");
        problem.setDetail("Um ou mais campos da requisição são inválidos.");

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(),
                fieldError.getDefaultMessage() == null ? "inválido" : fieldError.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(err ->
            fieldErrors.put(err.getObjectName(),
                err.getDefaultMessage() == null ? "inválido" : err.getDefaultMessage()));

        problem.setProperty("errors", fieldErrors);
        return decorate(problem, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(TYPE_MALFORMED);
        problem.setTitle("Requisição malformada");
        problem.setDetail("O corpo da requisição não pôde ser lido. Verifique se é um JSON válido "
            + "e se os tipos dos campos estão corretos.");
        log.debug("Corpo de requisição ilegível em {}", request.getRequestURI(), ex);
        return decorate(problem, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(TYPE_CONSTRAINT);
        problem.setTitle("Parâmetro inválido");
        problem.setDetail("O parâmetro '" + ex.getName() + "' tem formato inválido.");
        return decorate(problem, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatusCode());
        problem.setTitle(HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase());
        if (ex.getReason() != null) problem.setDetail(ex.getReason());
        return decorate(problem, request);
    }

    @ExceptionHandler(com.conceptualware.infrastructure.web.IdempotencyService.IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(
            com.conceptualware.infrastructure.web.IdempotencyService.IdempotencyConflictException ex,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create("https://conceptualware.dev/problems/idempotency-conflict"));
        problem.setTitle("Requisição duplicada em andamento");
        problem.setDetail(ex.getMessage());
        return decorate(problem, request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleDomainRule(RuntimeException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(TYPE_CONSTRAINT);
        problem.setTitle("Regra de domínio violada");
        problem.setDetail(ex.getMessage());
        return decorate(problem, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = correlationIdOf(request);
        log.error("Erro não tratado em {} [correlationId={}]", request.getRequestURI(), correlationId, ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(TYPE_INTERNAL);
        problem.setTitle("Erro interno");
        problem.setDetail("Ocorreu um erro inesperado. Informe o correlationId ao suporte.");
        return decorate(problem, request);
    }

    private ProblemDetail decorate(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("correlationId", correlationIdOf(request));
        return problem;
    }

    private String correlationIdOf(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        return correlationId == null || correlationId.isBlank() ? "none" : correlationId;
    }
}
