package com.conceptualware.infrastructure.observability;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Concept #27 — Observabilidade e Monitoramento:
 *   Métricas: Counter, Gauge, Histogram, Timer
 *   APM, SLI, SLO, Error Budget
 *   Logs estruturados, Trace ID
 *
 * Concept #26 — Performance: Profiling via métricas, benchmarking
 */
@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry registry;

    // Counters (Concept #27)
    private final Counter algorithmExecutions;
    private final Counter challengeSubmissions;
    private final Counter authAttempts;
    private final Counter authFailures;

    // Gauges (Concept #27)
    private final AtomicInteger activeUsers;

    // Timers / Histograms (Concept #27)
    private final Timer algorithmExecutionTimer;
    private final DistributionSummary inputSizeSummary;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;

        this.algorithmExecutions = Counter.builder("algorithm.executions.total")
            .description("Total algorithm executions")
            .tag("service", "backend")
            .register(registry);

        this.challengeSubmissions = Counter.builder("challenge.submissions.total")
            .description("Total challenge submissions")
            .register(registry);

        this.authAttempts = Counter.builder("auth.attempts.total")
            .description("Total authentication attempts")
            .register(registry);

        this.authFailures = Counter.builder("auth.failures.total")
            .description("Total authentication failures")
            .register(registry);

        this.activeUsers = new AtomicInteger(0);
        Gauge.builder("users.active", activeUsers, AtomicInteger::get)
            .description("Currently active users")
            .register(registry);

        this.algorithmExecutionTimer = Timer.builder("algorithm.execution.duration")
            .description("Algorithm execution duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.inputSizeSummary = DistributionSummary.builder("algorithm.input.size")
            .description("Distribution of algorithm input sizes")
            .register(registry);
    }

    // ── Recording Methods ────────────────────────────────────────────────────

    public void recordAlgorithmExecution(String algorithmName, int inputSize) {
        algorithmExecutions.increment();
        inputSizeSummary.record(inputSize);
        registry.counter("algorithm.executions.by.name", "name", algorithmName).increment();
        log.info("Algorithm executed: name={} inputSize={}", algorithmName, inputSize);
    }

    public <T> T timeAlgorithmExecution(String algorithmName, Supplier<T> supplier) {
        return algorithmExecutionTimer.record(supplier);
    }

    public void recordAlgorithmExecutionTime(String algorithmName, long durationNs) {
        algorithmExecutionTimer.record(durationNs, TimeUnit.NANOSECONDS);
        log.debug("Algorithm timing: name={} durationNs={}", algorithmName, durationNs);
    }

    public void recordChallengeSubmission(String challengeId, boolean passed) {
        challengeSubmissions.increment();
        registry.counter("challenge.submissions.result",
            "challengeId", challengeId,
            "result", passed ? "pass" : "fail").increment();
    }

    public void recordAuthAttempt(boolean success) {
        authAttempts.increment();
        if (!success) authFailures.increment();
    }

    public void incrementActiveUsers()  { activeUsers.incrementAndGet(); }
    public void decrementActiveUsers()  { activeUsers.decrementAndGet(); }

    // ── Health Check support (Concept #27) ───────────────────────────────────

    public void recordHealthCheck(String component, boolean healthy) {
        registry.gauge("health.status", Tags.of("component", component), healthy ? 1 : 0);
    }

    // ── SLI tracking (Concept #27) ────────────────────────────────────────────

    public void recordRequestOutcome(String endpoint, int statusCode, long durationMs) {
        Tags tags = Tags.of(
            "endpoint", endpoint,
            "status_class", statusCode < 400 ? "2xx" :
                            statusCode < 500 ? "4xx" : "5xx"
        );
        registry.counter("http.requests.total", tags).increment();
        registry.timer("http.request.duration", tags).record(durationMs, TimeUnit.MILLISECONDS);
    }
}
