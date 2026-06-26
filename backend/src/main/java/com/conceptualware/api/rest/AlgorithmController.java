package com.conceptualware.api.rest;

import com.conceptualware.application.AlgorithmApplicationService;
import com.conceptualware.domain.algorithm.Algorithm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Concept #25 — REST API: Resource, Endpoints, HTTP Methods, Status Codes,
 *   Headers, Request/Response Body, Idempotência, Paginação
 * Concept #16 — HTTP: GET/POST/PUT/PATCH/DELETE, status codes 2xx/4xx/5xx
 * Concept #14 — Clean Code: Guard clauses, early return, naming
 * Concept #29 — Clean Code: Funções pequenas, responsabilidade única
 */
@RestController
@RequestMapping("/api/v1/algorithms")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmController {

    private final AlgorithmApplicationService algorithmService;

    // ── GET /api/v1/algorithms ─────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<Algorithm>> listAlgorithms(
        @RequestParam(required = false) Algorithm.Category category,
        @RequestParam(required = false) Algorithm.Difficulty difficulty,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "name") String sortBy,
        @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<Algorithm> result = category != null
            ? algorithmService.findByCategory(category, pageable)
            : algorithmService.search("", pageable);

        return ResponseEntity.ok(result);
    }

    // ── GET /api/v1/algorithms/search ─────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<Page<Algorithm>> searchAlgorithms(
        @RequestParam @NotBlank @Size(min = 2, max = 100) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(algorithmService.search(q, PageRequest.of(page, size)));
    }

    // ── GET /api/v1/algorithms/{slug} ────────────────────────────────────────

    @GetMapping("/{slug}")
    public ResponseEntity<Algorithm> getAlgorithm(@PathVariable @NotBlank String slug) {
        return algorithmService.findBySlug(slug)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ── POST /api/v1/algorithms/{slug}/execute ────────────────────────────────

    @PostMapping("/{slug}/execute")
    public CompletableFuture<ResponseEntity<AlgorithmApplicationService.ExecutionResult>>
    executeAlgorithm(
        @PathVariable String slug,
        @Valid @RequestBody ExecutionRequest request
    ) {
        if (request.input().length > 10_000) {
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build());
        }
        return algorithmService.executeAlgorithm(slug, request.input())
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build());
    }

    // ── POST /api/v1/algorithms/{slug}/dp ────────────────────────────────────

    @PostMapping("/{slug}/dp")
    public ResponseEntity<AlgorithmApplicationService.DpResult> solveDp(
        @PathVariable String slug,
        @Valid @RequestBody DpRequest request
    ) {
        var result = algorithmService.solveDpProblem(slug, (Object[]) request.args());
        return ResponseEntity.ok(result);
    }

    // ── Admin only: POST /api/v1/algorithms ──────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Algorithm createAlgorithm(@Valid @RequestBody CreateAlgorithmRequest request) {
        Algorithm algorithm = Algorithm.create(
            request.slug(), request.name(), request.description(),
            request.category(), request.difficulty(),
            new Algorithm.Complexity(request.timeComplexity(), "O(1)", ""),
            new Algorithm.Complexity(request.spaceComplexity(), "O(1)", "")
        );
        return algorithmService.save(algorithm);
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    public record ExecutionRequest(
        @NotNull @Size(min = 1, max = 10000) int[] input
    ) {}

    public record DpRequest(String[] args) {}

    public record CreateAlgorithmRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String slug,
        @NotBlank @Size(min = 3, max = 200) String name,
        @NotBlank String description,
        @NotNull Algorithm.Category category,
        @NotNull Algorithm.Difficulty difficulty,
        @NotBlank String timeComplexity,
        @NotBlank String spaceComplexity
    ) {}
}
