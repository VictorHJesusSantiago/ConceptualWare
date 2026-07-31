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
import com.conceptualware.infrastructure.web.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/api/v1/core-concepts")
@RequiredArgsConstructor
@Slf4j
public class CoreConceptsController {

    private final IdempotencyService idempotencyService;

    private static final int MAX_COLLECTION_SIZE = 10_000;
    private static final int MAX_TEXT_LENGTH = 100_000;

    public record EdgeDto(@Min(0) @Max(999) int from, @Min(0) @Max(999) int to, double weight) {}

    public record GraphRequest(
        @Min(1) @Max(1000) int vertices,
        boolean directed,
        @NotNull @Size(max = MAX_COLLECTION_SIZE) List<@Valid EdgeDto> edges
    ) {}

    @PostMapping("/graph/scc/tarjan")
    public ResponseEntity<List<List<Integer>>> tarjanScc(@Valid @RequestBody GraphRequest req) {
        Graph graph = buildGraph(req);
        return ResponseEntity.ok(GraphAlgorithms.tarjanSCC(graph));
    }

    @PostMapping("/graph/scc/kosaraju")
    public ResponseEntity<List<List<Integer>>> kosarajuScc(@Valid @RequestBody GraphRequest req) {
        Graph graph = buildGraph(req);
        return ResponseEntity.ok(GraphAlgorithms.kosarajuSCC(graph));
    }

    private Graph buildGraph(GraphRequest req) {
        Graph graph = new Graph(req.vertices(), req.directed());
        for (EdgeDto e : req.edges()) {
            if (e.from() >= req.vertices() || e.to() >= req.vertices()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aresta (" + e.from() + "->" + e.to() + ") referencia vértice fora do intervalo [0, "
                        + (req.vertices() - 1) + "]");
            }
            graph.addEdge(e.from(), e.to(), e.weight());
        }
        return graph;
    }

    public record RangeDto(@Min(0) int left, @Min(0) int right) {}

    public record FenwickRequest(
        @NotNull @Size(min = 1, max = MAX_COLLECTION_SIZE) List<@NotNull Long> values,
        @NotNull @Size(max = 1000) List<@Valid RangeDto> ranges
    ) {}

    @PostMapping("/datastructures/fenwick/range-sum")
    public ResponseEntity<List<Long>> fenwickRangeSum(@Valid @RequestBody FenwickRequest req) {
        long[] values = req.values().stream().mapToLong(Long::longValue).toArray();
        FenwickTree bit = FenwickTree.fromArray(values);

        List<Long> results = new ArrayList<>(req.ranges().size());
        for (RangeDto range : req.ranges()) {
            if (range.left() > range.right() || range.right() >= values.length) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Intervalo inválido [" + range.left() + ", " + range.right()
                        + "] para array de tamanho " + values.length);
            }
            results.add(bit.rangeSum(range.left(), range.right()));
        }
        return ResponseEntity.ok(results);
    }

    public record CacheOpDto(@NotBlank @Size(max = 200) String op,
                             @Size(max = 200) String key,
                             @Size(max = 1000) String value) {}

    public record CacheDemoRequest(@Min(1) @Max(1000) int capacity,
                                   @NotNull @Size(max = MAX_COLLECTION_SIZE) List<@Valid CacheOpDto> ops) {}

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
        List<CacheOpResult> results = new ArrayList<>(ops.size());
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

    public record BloomFilterRequest(
        @Min(1) @Max(1_000_000) int expectedInsertions,
        @DecimalMin("0.0001") @DecimalMax("0.5") double falsePositiveRate,
        @NotNull @Size(max = MAX_COLLECTION_SIZE) List<@NotNull @Size(max = 500) String> insert,
        @NotNull @Size(max = MAX_COLLECTION_SIZE) List<@NotNull @Size(max = 500) String> check
    ) {}

    public record BloomFilterResponse(int bitSize, int hashCount, Map<String, Boolean> checkResults) {}

    @PostMapping("/datastructures/bloom-filter")
    public ResponseEntity<BloomFilterResponse> bloomFilterDemo(@Valid @RequestBody BloomFilterRequest req) {
        BloomFilter<String> filter = new BloomFilter<>(req.expectedInsertions(), req.falsePositiveRate());
        req.insert().forEach(filter::add);
        Map<String, Boolean> results = new LinkedHashMap<>();
        req.check().forEach(item -> results.put(item, filter.mightContain(item)));
        return ResponseEntity.ok(new BloomFilterResponse(filter.bitSize(), filter.hashCount(), results));
    }

    public record ZSearchRequest(@NotBlank @Size(max = MAX_TEXT_LENGTH) String text,
                                 @NotBlank @Size(max = 1000) String pattern) {}

    @PostMapping("/strings/z-search")
    public ResponseEntity<List<Integer>> zSearch(@Valid @RequestBody ZSearchRequest req) {
        return ResponseEntity.ok(StringAlgorithms.zSearch(req.text(), req.pattern()));
    }

    public record AhoCorasickRequest(
        @NotBlank @Size(max = MAX_TEXT_LENGTH) String text,
        @NotNull @Size(max = 1000) List<@NotBlank @Size(max = 500) String> patterns
    ) {}

    @PostMapping("/strings/aho-corasick")
    public ResponseEntity<Map<String, List<Integer>>> ahoCorasick(@Valid @RequestBody AhoCorasickRequest req) {
        var automaton = new StringAlgorithms.AhoCorasick(req.patterns());
        return ResponseEntity.ok(automaton.search(req.text()));
    }

    public record ThreadBenchmarkRequest(
        @Min(1) @Max(2_000) int taskCount,
        @Min(0) @Max(50) int simulatedIoMillis,
        @Min(1) @Max(64) int platformPoolSize
    ) {}

    @PostMapping("/concurrency/thread-benchmark")
    public ResponseEntity<Map<String, ConcurrencyUtils.ThreadBenchmarkResult>> threadBenchmark(
            @Valid @RequestBody ThreadBenchmarkRequest req) throws InterruptedException {
        var virtual  = ConcurrencyUtils.benchmarkVirtualThreads(req.taskCount(), req.simulatedIoMillis());
        var platform = ConcurrencyUtils.benchmarkPlatformThreads(req.taskCount(), req.simulatedIoMillis(), req.platformPoolSize());
        return ResponseEntity.ok(Map.of("virtual", virtual, "platform", platform));
    }

    public record FanOutRequest(
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Min(0) @Max(1000) Integer> delaysMillis
    ) {}

    @PostMapping("/concurrency/structured-fan-out")
    public ResponseEntity<List<Integer>> structuredFanOut(@Valid @RequestBody FanOutRequest req) {
        List<Callable<Integer>> tasks = req.delaysMillis().stream()
            .<Callable<Integer>>map(delay -> () -> {
                Thread.sleep(delay);
                return delay;
            }).toList();
        try {
            return ResponseEntity.ok(ConcurrencyUtils.structuredFanOut(tasks));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Execução interrompida");
        } catch (Exception ex) {
            log.warn("Falha no fan-out estruturado", ex);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Falha ao executar as tarefas");
        }
    }

    public record SagaStepInput(@NotBlank @Size(max = 200) String name, boolean shouldSucceed) {}

    public record SagaDemoRequest(@NotNull @Size(min = 1, max = 100) List<@Valid SagaStepInput> steps) {}

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

    public record OutboxAppendRequest(@NotBlank @Size(max = 100) String aggregateType,
                                       @NotBlank @Size(max = 100) String aggregateId,
                                       @NotBlank @Size(max = 100) String eventType,
                                       @NotBlank @Size(max = 2000) String payload) {}

    @PostMapping("/patterns/outbox")
    public ResponseEntity<UUID> outboxAppend(
            @Valid @RequestBody OutboxAppendRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 200) String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.ok(
                outboxTable.append(req.aggregateType(), req.aggregateId(), req.eventType(), req.payload()));
        }

        var replay = idempotencyService.beginOrReplay(idempotencyKey);
        if (replay.isPresent()) {
            return ResponseEntity.ok(UUID.fromString(replay.get()));
        }

        try {
            UUID id = outboxTable.append(req.aggregateType(), req.aggregateId(), req.eventType(), req.payload());
            idempotencyService.complete(idempotencyKey, id.toString());
            return ResponseEntity.ok(id);
        } catch (RuntimeException ex) {
            idempotencyService.release(idempotencyKey);
            throw ex;
        }
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
