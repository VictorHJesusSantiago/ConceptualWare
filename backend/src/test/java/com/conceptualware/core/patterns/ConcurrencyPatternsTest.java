package com.conceptualware.core.patterns;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — TDD: unit tests for concurrency patterns
 * Concept #13 — Design Patterns: Active Object, Monitor Object,
 *               Half-Sync/Half-Async, Microkernel
 * Concept #17 — Concorrência: thread safety, blocking/non-blocking
 */
@DisplayName("Concurrency Patterns — Unit Tests")
class ConcurrencyPatternsTest {

    // ── Active Object ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Active Object Pattern")
    class ActiveObjectTests {

        @Test
        @DisplayName("Submit returns Future that resolves to correct result")
        @Timeout(5)
        void activeObjectReturnsResult() throws Exception {
            var service = new ConcurrencyPatterns.ActiveAlgorithmService();
            try {
                Future<Integer> future = service.submit(() -> 42);
                assertThat(future.get()).isEqualTo(42);
            } finally {
                service.shutdown();
            }
        }

        @Test
        @DisplayName("Multiple concurrent submissions all complete")
        @Timeout(10)
        void activeObjectHandlesConcurrentSubmissions() throws Exception {
            var service = new ConcurrencyPatterns.ActiveAlgorithmService();
            try {
                List<Future<Integer>> futures = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    final int val = i;
                    futures.add(service.submit(() -> val * val));
                }
                for (int i = 0; i < 10; i++) {
                    assertThat(futures.get(i).get()).isEqualTo(i * i);
                }
            } finally {
                service.shutdown();
            }
        }
    }

    // ── Monitor Object ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Monitor Object Pattern")
    class MonitorObjectTests {

        @Test
        @DisplayName("tryGet on empty cache returns null")
        void emptyMonitorCacheReturnsNull() {
            var cache = new ConcurrencyPatterns.MonitorCache<String, Integer>(10);
            assertThat(cache.tryGet("missing")).isNull();
        }

        @Test
        @DisplayName("put then tryGet returns stored value")
        void monitorCachePutAndGet() throws InterruptedException {
            var cache = new ConcurrencyPatterns.MonitorCache<String, Integer>(10);
            cache.put("key", 42);
            assertThat(cache.tryGet("key")).isEqualTo(42);
        }

        @Test
        @DisplayName("size reflects stored entries")
        void monitorCacheSize() throws InterruptedException {
            var cache = new ConcurrencyPatterns.MonitorCache<String, Integer>(10);
            cache.put("a", 1);
            cache.put("b", 2);
            assertThat(cache.size()).isEqualTo(2);
        }
    }

    // ── Half-Sync / Half-Async ────────────────────────────────────────────────

    @Nested
    @DisplayName("Half-Sync/Half-Async Pattern")
    class HalfSyncHalfAsyncTests {

        @Test
        @DisplayName("Submitted request completes asynchronously")
        @Timeout(5)
        void requestCompletesAsync() throws Exception {
            var service = new ConcurrencyPatterns.HalfSyncHalfAsync<Integer, Integer>(
                10, 2, n -> n * n
            );
            try {
                CompletableFuture<Integer> future = service.submit(7);
                assertThat(future.get()).isEqualTo(49);
            } finally {
                service.shutdown();
            }
        }
    }

    // ── Microkernel ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Microkernel Pattern")
    class MicrokernelTests {

        @Test
        @DisplayName("Core starts with no plugins registered")
        void coreStartsEmpty() {
            var core = new ConcurrencyPatterns.MicrokernelCore();
            assertThat(core.registeredPlugins()).isEmpty();
        }

        @Test
        @DisplayName("Plugin registers and executes via core")
        void pluginRegistersAndExecutes() {
            var core = new ConcurrencyPatterns.MicrokernelCore();
            core.registerPlugin(new ConcurrencyPatterns.SortPlugin());

            assertThat(core.registeredPlugins()).contains("sort");

            var result = core.executePlugin("sort", Map.of("data", List.of(3, 1, 2)));
            @SuppressWarnings("unchecked")
            var sorted = (List<Integer>) result.get("sorted");
            assertThat(sorted).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("Metrics plugin counts sort events via event bus")
        void metricsPluginTracksEvents() {
            var core = new ConcurrencyPatterns.MicrokernelCore();
            core.registerPlugin(new ConcurrencyPatterns.SortPlugin());
            core.registerPlugin(new ConcurrencyPatterns.MetricsPlugin());

            core.executePlugin("sort", Map.of("data", List.of(2, 1)));
            core.executePlugin("sort", Map.of("data", List.of(5, 3, 4)));

            var metrics = core.executePlugin("metrics", Map.of());
            assertThat(metrics.get("sorts")).isEqualTo(2L);
        }

        @Test
        @DisplayName("Unregistering plugin removes it from core")
        void unregisterPlugin() {
            var core = new ConcurrencyPatterns.MicrokernelCore();
            core.registerPlugin(new ConcurrencyPatterns.SortPlugin());
            core.unregisterPlugin("sort");

            assertThat(core.registeredPlugins()).doesNotContain("sort");
            assertThatThrownBy(() -> core.executePlugin("sort", Map.of()))
                .isInstanceOf(NoSuchElementException.class);
        }
    }
}
