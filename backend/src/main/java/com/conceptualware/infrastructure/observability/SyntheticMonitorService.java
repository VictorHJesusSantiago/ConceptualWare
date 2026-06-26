package com.conceptualware.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.*;

/**
 * Concept #27 — Observabilidade: Synthetic Monitoring
 *   Synthetic monitoring: automated, scripted checks that simulate user actions
 *   to proactively detect availability and latency issues — even with zero traffic.
 *
 *   This service:
 *   - Runs scheduled health probes (synthetic transactions)
 *   - Records SLI metrics (availability, latency p50/p99)
 *   - Tracks error budget consumption
 *   - Exposes Uptime, MTTD (Mean Time to Detect), MTTR via Micrometer
 *
 * Concept #22 — Cloud: SLA, SLO, SLI (Service Level indicators)
 * Concept #20 — DevOps: monitoring pipeline, automated alerting
 */
@Service
public class SyntheticMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SyntheticMonitorService.class);

    // SLI counters — raw signal for SLO computation
    private final AtomicLong totalChecks   = new AtomicLong(0);
    private final AtomicLong successChecks = new AtomicLong(0);
    private final AtomicLong failureChecks = new AtomicLong(0);

    // SLO targets (Concept #27)
    private static final double AVAILABILITY_SLO = 0.999;   // 99.9% uptime
    private static final long   LATENCY_SLO_MS   = 200;     // p99 < 200ms

    // MTTR tracking
    private volatile Instant lastFailureTime = null;
    private final AtomicLong totalDowntimeMs = new AtomicLong(0);

    // Micrometer instruments (Concept #27 — Counter, Gauge, Timer, Summary)
    private final Counter syntheticSuccessCounter;
    private final Counter syntheticFailureCounter;
    private final Timer   syntheticLatencyTimer;

    public SyntheticMonitorService(MeterRegistry registry) {
        this.syntheticSuccessCounter = Counter.builder("synthetic.monitor.checks")
            .tag("result", "success")
            .description("Successful synthetic monitoring checks")
            .register(registry);

        this.syntheticFailureCounter = Counter.builder("synthetic.monitor.checks")
            .tag("result", "failure")
            .description("Failed synthetic monitoring checks")
            .register(registry);

        this.syntheticLatencyTimer = Timer.builder("synthetic.monitor.latency")
            .description("Synthetic check latency distribution")
            .publishPercentiles(0.50, 0.95, 0.99)
            .register(registry);

        // SLI Gauge — current availability ratio (Concept #27 — SLI)
        Gauge.builder("synthetic.sli.availability", this, SyntheticMonitorService::currentAvailability)
            .description("Current availability SLI (0.0 - 1.0)")
            .register(registry);

        // Error budget remaining gauge (Concept #27 — Error Budget)
        Gauge.builder("synthetic.error.budget.remaining", this, SyntheticMonitorService::errorBudgetRemaining)
            .description("Error budget remaining (1.0 = full, 0.0 = exhausted)")
            .register(registry);

        // MTTD/MTTR gauges (Concept #27)
        Gauge.builder("synthetic.mttr.ms", this, s -> s.totalDowntimeMs.get())
            .description("Cumulative downtime milliseconds (MTTR signal)")
            .register(registry);
    }

    // ── Scheduled synthetic probes ────────────────────────────────────────────

    /** Synthetic health probe — runs every 30 seconds. */
    @Scheduled(fixedDelay = 30_000)
    @Observed(name = "synthetic.health.probe")
    public void runHealthProbe() {
        syntheticLatencyTimer.record(() -> {
            Instant start = Instant.now();
            totalChecks.incrementAndGet();
            try {
                executeHealthCheck();
                successChecks.incrementAndGet();
                syntheticSuccessCounter.increment();

                if (lastFailureTime != null) {
                    // Recovery detected — compute downtime for MTTR (Concept #27)
                    long downtime = Duration.between(lastFailureTime, Instant.now()).toMillis();
                    totalDowntimeMs.addAndGet(downtime);
                    log.info("Service recovered after {}ms downtime [trace_id=synthetic]", downtime);
                    lastFailureTime = null;
                }
            } catch (Exception e) {
                failureChecks.incrementAndGet();
                syntheticFailureCounter.increment();
                if (lastFailureTime == null) lastFailureTime = Instant.now(); // MTTD start
                log.error("Synthetic probe FAILED: {} [sli=availability]", e.getMessage());
            }
        });
    }

    /** Synthetic algorithm execution probe — validates core business logic is reachable. */
    @Scheduled(fixedDelay = 60_000)
    @Observed(name = "synthetic.algorithm.probe")
    public void runAlgorithmProbe() {
        Instant start = Instant.now();
        try {
            // Execute a known-good sort to verify the algorithm engine is working
            int[] testInput = {5, 3, 1, 4, 2};
            // Algorithm engines are static — verify output is sorted
            int[] sorted = com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(testInput);
            for (int i = 1; i < sorted.length; i++) {
                if (sorted[i] < sorted[i - 1]) throw new IllegalStateException("Algorithm engine degraded!");
            }
            long latency = Duration.between(start, Instant.now()).toMillis();
            if (latency > LATENCY_SLO_MS) {
                log.warn("Algorithm probe latency {}ms exceeds SLO {}ms [slo.breach=latency]",
                    latency, LATENCY_SLO_MS);
            }
        } catch (Exception e) {
            log.error("Algorithm synthetic probe FAILED: {}", e.getMessage());
        }
    }

    // ── SLI / SLO / Error Budget calculations ─────────────────────────────────

    /** SLI: availability = successful checks / total checks (Concept #27). */
    public double currentAvailability() {
        long total = totalChecks.get();
        return total == 0 ? 1.0 : (double) successChecks.get() / total;
    }

    /**
     * Error budget remaining = 1 - (error_rate / allowed_error_rate).
     * When this reaches 0, all risky changes should be frozen. (Concept #27)
     */
    public double errorBudgetRemaining() {
        double errorRate    = 1.0 - currentAvailability();
        double allowedError = 1.0 - AVAILABILITY_SLO;
        if (allowedError <= 0) return 0;
        return Math.max(0.0, 1.0 - (errorRate / allowedError));
    }

    /** SLO compliance report for dashboards (Concept #27). */
    public SloReport generateSloReport() {
        return new SloReport(
            totalChecks.get(),
            successChecks.get(),
            failureChecks.get(),
            currentAvailability(),
            errorBudgetRemaining(),
            totalDowntimeMs.get()
        );
    }

    public record SloReport(
        long totalChecks,
        long successChecks,
        long failureChecks,
        double availability,
        double errorBudgetRemaining,
        long cumulativeDowntimeMs
    ) {
        public boolean isSloCompliant() { return availability >= AVAILABILITY_SLO; }
    }

    private void executeHealthCheck() {
        // In production this would hit a real health endpoint;
        // here it validates application context is healthy
        long free = Runtime.getRuntime().freeMemory();
        if (free < 10_000_000L) throw new IllegalStateException("Low memory: " + free + " bytes free");
    }
}
