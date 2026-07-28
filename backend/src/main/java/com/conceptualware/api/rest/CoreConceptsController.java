package com.conceptualware.api.rest;

import com.conceptualware.core.algorithms.graph.GraphAlgorithms;
import com.conceptualware.core.algorithms.string.StringAlgorithms;
import com.conceptualware.core.concurrency.ConcurrencyUtils;
import com.conceptualware.core.datastructures.BloomFilter;
import com.conceptualware.core.datastructures.LFUCache;
import com.conceptualware.core.datastructures.LRUCache;
import com.conceptualware.core.datastructures.graph.Graph;
import com.conceptualware.core.datastructures.tree.FenwickTree;
import com.conceptualware.core.patterns.ArchitecturalPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Concept #25 — REST API sandbox para demos stateless de conceitos "core":
 *   algoritmos de grafo, estruturas de dados avançadas, algoritmos de string,
 *   concorrência estruturada (Java 21) e padrões arquiteturais in-process
 *   (CQRS/Saga/Outbox). Sem persistência — cada chamada cria seu próprio estado.
 */
@RestController
@RequestMapping("/api/v1/core-concepts")
@RequiredArgsConstructor
@Slf4j
public class CoreConceptsController {

    // ── Grafos: SCC (Tarjan / Kosaraju) ──────────────────────────────────────

    public record EdgeDto(@Min(0) int from, @Min(0) int to, double weight) {}
    public record GraphRequest(@Min(1) @Max(1000) int vertices, boolean directed, @NotNull List<EdgeDto> edges) {}

    @PostMapping("/graph/scc/tarjan")
    public ResponseEntity<List<List<Integer>>> tarjanScc(@Valid @RequestBody GraphRequest req) {
        Graph graph = buildGraph(req);
        return ResponseEntity.ok(new GraphAlgorithms().tarjanSCC(graph));
    }

    @PostMapping("/graph/scc/kosaraju")
    public ResponseEntity<List<List<Integer>>> kosarajuScc(@Valid @RequestBody GraphRequest req) {
        Graph graph = buildGraph(req);
        return ResponseEntity.ok(GraphAlgorithms.kosarajuSCC(graph));
    }

    private Graph buildGraph(GraphRequest req) {
        Graph graph = new Graph(req.vertices(), req.directed());
        for (EdgeDto e : req.edges()) graph.addEdge(e.from(), e.to(), e.weight());
        return graph;
    }

    // ── Estruturas Avançadas: Fenwick, LRU, LFU, Bloom Filter ────────────────

    @PostMapping("/datastructures/fenwick/range-sum")
    public ResponseEntity<List<Long>> fenwickRangeSum(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> valuesRaw = (List<Number>) body.get("values");
        @SuppressWarnings("unchecked")
        List<List<Integer>> ranges = (List<List<Integer>>) body.get("ranges"); // cada item [l, r]

        long[] values = valuesRaw.stream().mapToLong(Number::longValue).toArray();
        FenwickTree bit = FenwickTree.fromArray(values);
        List<Long> results = new ArrayList<>();
        for (List<Integer> range : ranges) results.add(bit.rangeSum(range.get(0), range.get(1)));
        return ResponseEntity.ok(results);
    }

    public record CacheOpDto(@NotBlank String op, String key, String value) {}
    public record CacheDemoRequest(@Min(1) @Max(1000) int capacity, @NotNull List<CacheOpDto> ops) {}
    public record CacheOpResult(String op, String key, String result) {}

    @PostMapping("/datastructures/lru")
    public ResponseEntity<List<CacheOpResult>> lruDemo(@Valid @RequestBody CacheDemoRequest req) {
        LRUCache<String, String> cache = new LRUCache<>(req.capacity());
        return ResponseEntity.ok(runCacheOps(req.ops(), cache::put, cache::get));
    }

    @PostMapping("/datastructures/lfu")
    public ResponseEntity<List<CacheOpResult>> lfuDemo(@Valid @RequestBody CacheDemoRequest req) {
        LFUCache<String, String> cache = new LFUCache<>(req.capacity());
        return ResponseEntity.ok(runCacheOps(req.ops(), cache::put, cache::get));
    }

    private List<CacheOpResult> runCacheOps(List<CacheOpDto> ops,
                                             java.util.function.BiConsumer<String, String> put,
                                             java.util.function.Function<String, String> get) {
        List<CacheOpResult> results = new ArrayList<>();
        for (CacheOpDto op : ops) {
            if ("put".equalsIgnoreCase(op.op())) {
                put.accept(op.key(), op.value());
                results.add(new CacheOpResult("put", op.key(), "ok"));
            } else if ("get".equalsIgnoreCase(op.op())) {
                String value = get.apply(op.key());
                results.add(new CacheOpResult("get", op.key(), value == null ? "MISS" : value));
            }
        }
        return results;
    }

    public record BloomFilterRequest(@Min(1) int expectedInsertions, @DecimalMin("0.0001") @DecimalMax("0.5") double falsePositiveRate,
                                      @NotNull List<String> insert, @NotNull List<String> check) {}
    public record BloomFilterResponse(int bitSize, int hashCount, Map<String, Boolean> checkResults) {}

    @PostMapping("/datastructures/bloom-filter")
    public ResponseEntity<BloomFilterResponse> bloomFilterDemo(@Valid @RequestBody BloomFilterRequest req) {
        BloomFilter<String> filter = new BloomFilter<>(req.expectedInsertions(), req.falsePositiveRate());
        req.insert().forEach(filter::add);
        Map<String, Boolean> results = new LinkedHashMap<>();
        req.check().forEach(item -> results.put(item, filter.mightContain(item)));
        return ResponseEntity.ok(new BloomFilterResponse(filter.bitSize(), filter.hashCount(), results));
    }

    // ── Strings: Z-algorithm, Aho-Corasick ────────────────────────────────────

    public record ZSearchRequest(@NotBlank String text, @NotBlank String pattern) {}

    @PostMapping("/strings/z-search")
    public ResponseEntity<List<Integer>> zSearch(@Valid @RequestBody ZSearchRequest req) {
        return ResponseEntity.ok(StringAlgorithms.zSearch(req.text(), req.pattern()));
    }

    public record AhoCorasickRequest(@NotBlank String text, @NotNull List<String> patterns) {}

    @PostMapping("/strings/aho-corasick")
    public ResponseEntity<Map<String, List<Integer>>> ahoCorasick(@Valid @RequestBody AhoCorasickRequest req) {
        var automaton = new StringAlgorithms.AhoCorasick(req.patterns());
        return ResponseEntity.ok(automaton.search(req.text()));
    }

    // ── Concorrência: benchmark Virtual Threads vs Platform Threads ──────────

    public record ThreadBenchmarkRequest(@Min(1) @Max(50_000) int taskCount, @Min(0) @Max(1000) int simulatedIoMillis,
                                          @Min(1) @Max(500) int platformPoolSize) {}

    @PostMapping("/concurrency/thread-benchmark")
    public ResponseEntity<Map<String, ConcurrencyUtils.ThreadBenchmarkResult>> threadBenchmark(
            @Valid @RequestBody ThreadBenchmarkRequest req) throws InterruptedException {
        var virtual = ConcurrencyUtils.benchmarkVirtualThreads(req.taskCount(), req.simulatedIoMillis());
        var platform = ConcurrencyUtils.benchmarkPlatformThreads(req.taskCount(), req.simulatedIoMillis(), req.platformPoolSize());
        return ResponseEntity.ok(Map.of("virtual", virtual, "platform", platform));
    }

    @PostMapping("/concurrency/structured-fan-out")
    public ResponseEntity<List<Integer>> structuredFanOut(@RequestBody List<@Min(0) Integer> delaysMillis) {
        List<Callable<Integer>> tasks = delaysMillis.stream()
            .<Callable<Integer>>map(delay -> () -> {
                Thread.sleep(delay);
                return delay;
            }).toList();
        try {
            return ResponseEntity.ok(ConcurrencyUtils.structuredFanOut(tasks));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
    }

    // ── Padrões Arquiteturais: Saga com compensação, Outbox ──────────────────

    public record SagaStepInput(@NotBlank String name, boolean shouldSucceed) {}
    public record SagaDemoRequest(@NotNull List<SagaStepInput> steps) {}

    @PostMapping("/patterns/saga")
    public ResponseEntity<ArchitecturalPatterns.SagaResult> sagaDemo(@Valid @RequestBody SagaDemoRequest req) {
        var orchestrator = new ArchitecturalPatterns.SagaOrchestrator<Void>();
        for (SagaStepInput input : req.steps()) {
            orchestrator.addStep(new ArchitecturalPatterns.SagaStep<>(
                input.name(),
                ctx -> input.shouldSucceed(),
                ctx -> null
            ));
        }
        return ResponseEntity.ok(orchestrator.execute(null));
    }

    private final ArchitecturalPatterns.OutboxTable outboxTable = new ArchitecturalPatterns.OutboxTable();

    public record OutboxAppendRequest(@NotBlank String aggregateType, @NotBlank String aggregateId,
                                       @NotBlank String eventType, @NotBlank String payload) {}

    @PostMapping("/patterns/outbox")
    public ResponseEntity<UUID> outboxAppend(@Valid @RequestBody OutboxAppendRequest req) {
        return ResponseEntity.ok(outboxTable.append(req.aggregateType(), req.aggregateId(), req.eventType(), req.payload()));
    }

    @GetMapping("/patterns/outbox/pending")
    public ResponseEntity<List<ArchitecturalPatterns.OutboxMessage>> outboxPending() {
        return ResponseEntity.ok(outboxTable.pendingMessages());
    }

    @PostMapping("/patterns/outbox/{id}/publish")
    public ResponseEntity<Void> outboxPublish(@PathVariable UUID id) {
        outboxTable.markPublished(id);
        return ResponseEntity.noContent().build();
    }
}
