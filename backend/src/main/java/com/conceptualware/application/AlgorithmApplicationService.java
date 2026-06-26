package com.conceptualware.application;

import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import com.conceptualware.core.algorithms.graph.GraphAlgorithms;
import com.conceptualware.core.algorithms.dp.DynamicProgramming;
import com.conceptualware.core.algorithms.string.StringAlgorithms;
import com.conceptualware.core.patterns.DesignPatterns;
import com.conceptualware.domain.algorithm.Algorithm;
import com.conceptualware.infrastructure.observability.MetricsService;
import com.conceptualware.infrastructure.persistence.AlgorithmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Concept #12 — Arquitetura: Application Service (orquestra domain + infrastructure)
 * Concept #14 — SOLID: SRP, OCP, DIP (depends on abstractions)
 * Concept #18 — Async: CompletableFuture, @Async
 * Concept #26 — Performance: Caching (@Cacheable), Connection pooling
 * Concept #27 — Observabilidade: Métricas, Logs estruturados
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlgorithmApplicationService {

    private final AlgorithmRepository algorithmRepository;
    private final MetricsService metricsService;

    // ── Execute Algorithm — returns step-by-step trace ────────────────────────

    @Async("algorithmExecutor") // CPU-bound — platform thread pool (Concept #18)
    public CompletableFuture<ExecutionResult> executeAlgorithm(
            String algorithmName, int[] input) {

        long startNs = System.nanoTime();
        metricsService.recordAlgorithmExecution(algorithmName, input.length);

        try {
            int[] result = switch (algorithmName.toLowerCase()) {
                case "bubble"    -> SortingAlgorithms.bubbleSort(input);
                case "selection" -> SortingAlgorithms.selectionSort(input);
                case "insertion" -> SortingAlgorithms.insertionSort(input);
                case "merge"     -> SortingAlgorithms.mergeSort(input);
                case "quick"     -> SortingAlgorithms.quickSort(input);
                case "heap"      -> SortingAlgorithms.heapSort(input);
                case "shell"     -> SortingAlgorithms.shellSort(input);
                case "tim"       -> SortingAlgorithms.timSort(input);
                default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
            };

            long durationNs = System.nanoTime() - startNs;
            metricsService.recordAlgorithmExecutionTime(algorithmName, durationNs);

            Map<String, Object> complexity = new HashMap<>();
            SortingAlgorithms.complexityTable().get(toPascalCase(algorithmName)).ifPresent_or_else(
                c -> {}, // handled below
                () -> {}
            );
            var info = SortingAlgorithms.complexityTable().get(toPascalCase(algorithmName));

            return CompletableFuture.completedFuture(new ExecutionResult(
                algorithmName, input, result, durationNs,
                info != null ? info.timeAvg() : "Unknown",
                info != null ? info.space() : "Unknown"
            ));

        } catch (Exception e) {
            log.error("Algorithm execution failed: name={} error={}", algorithmName, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private String toPascalCase(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    // ── DP Problems ───────────────────────────────────────────────────────────

    public DpResult solveDpProblem(String problem, Object... args) {
        return switch (problem.toLowerCase()) {
            case "fibonacci" -> new DpResult(problem,
                DynamicProgramming.fibTabulation((Integer) args[0]),
                "O(n)", "O(1)");
            case "knapsack01" -> new DpResult(problem,
                DynamicProgramming.knapsack01((int[]) args[0], (int[]) args[1], (Integer) args[2]),
                "O(n*W)", "O(n*W)");
            case "lcs" -> new DpResult(problem,
                DynamicProgramming.lcs((String) args[0], (String) args[1]),
                "O(m*n)", "O(m*n)");
            case "editdistance" -> new DpResult(problem,
                DynamicProgramming.editDistance((String) args[0], (String) args[1]),
                "O(m*n)", "O(m*n)");
            case "coinchange" -> new DpResult(problem,
                DynamicProgramming.coinChange((int[]) args[0], (Integer) args[1]),
                "O(amount*coins)", "O(amount)");
            default -> throw new IllegalArgumentException("Unknown DP problem: " + problem);
        };
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Cacheable(value = "algorithms", key = "#slug")
    public Optional<Algorithm> findBySlug(String slug) {
        log.debug("Cache miss for algorithm slug={}", slug);
        return algorithmRepository.findBySlug(slug);
    }

    @Cacheable(value = "algorithms", key = "'category:' + #category + ':page:' + #pageable.pageNumber")
    public Page<Algorithm> findByCategory(Algorithm.Category category, Pageable pageable) {
        return algorithmRepository.findByCategory(category, pageable);
    }

    public Page<Algorithm> search(String query, Pageable pageable) {
        return algorithmRepository.fullTextSearch(query, pageable);
    }

    @CacheEvict(value = "algorithms", allEntries = true)
    public Algorithm save(Algorithm algorithm) {
        return algorithmRepository.save(algorithm);
    }

    // ── Records (return types) ────────────────────────────────────────────────

    public record ExecutionResult(
        String algorithmName,
        int[] input,
        int[] output,
        long durationNs,
        String timeComplexity,
        String spaceComplexity
    ) {}

    public record DpResult(String problem, Object result, String time, String space) {}
}
