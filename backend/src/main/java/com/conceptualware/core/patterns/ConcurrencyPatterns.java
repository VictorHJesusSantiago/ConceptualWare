package com.conceptualware.core.patterns;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/**
 * Concept #13 — Padrões de Concorrência e Arquitetura (complete coverage):
 *   Active Object   — decouples method execution from invocation (async method calls)
 *   Monitor Object  — synchronises concurrent access (every method is synchronized)
 *   Half-Sync/Half-Async — separates sync & async processing tiers
 *   Microkernel     — extensible core + plug-in architecture
 *
 * Concept #17 — Concorrência: Virtual Threads, thread pools, condition variables
 * Concept #18 — Async: CompletableFuture, ExecutorService, BlockingQueue
 */
public class ConcurrencyPatterns {

    // ══════════════════════════════════════════════════════════════════════════
    // PATTERN 1 — Active Object
    //   Intent: decouples method invocation from execution so that the caller
    //   is never blocked. Requests are placed in a queue and run on a separate
    //   servant thread.  "Future"-based result retrieval.
    // ══════════════════════════════════════════════════════════════════════════

    /** A "method request" in the Active Object pattern. */
    @FunctionalInterface
    public interface MethodRequest<T> {
        T execute() throws Exception;
    }

    /** The servant executes requests on its own thread (the Scheduler). */
    public static class ActiveAlgorithmService {

        private final BlockingQueue<Runnable> activationQueue = new LinkedBlockingQueue<>();
        private final Thread schedulerThread;

        public ActiveAlgorithmService() {
            schedulerThread = Thread.ofVirtual().name("active-object-scheduler").start(this::schedulerLoop);
        }

        private void schedulerLoop() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Runnable request = activationQueue.take();
                    request.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /** Enqueue an async sort request — returns a Future immediately. */
        public <T> Future<T> submit(MethodRequest<T> request) {
            CompletableFuture<T> future = new CompletableFuture<>();
            activationQueue.offer(() -> {
                try {
                    future.complete(request.execute());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }

        public void shutdown() { schedulerThread.interrupt(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PATTERN 2 — Monitor Object
    //   Intent: synchronizes every method of an object so that only one
    //   method executes within the object at a time.
    //   Differs from simple `synchronized`: uses explicit Conditions for
    //   fine-grained waiting (like "wait until not empty" vs "wait until not full").
    // ══════════════════════════════════════════════════════════════════════════

    public static class MonitorCache<K, V> {

        private final int maxSize;
        private final LinkedHashMap<K, V> store;
        private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        private final java.util.concurrent.locks.Condition notFull  = lock.newCondition();
        private final java.util.concurrent.locks.Condition notEmpty = lock.newCondition();

        public MonitorCache(int maxSize) {
            this.maxSize = maxSize;
            this.store   = new LinkedHashMap<>(maxSize, 0.75f, true);
        }

        /** Monitor method: blocks if cache is full until space becomes available. */
        public void put(K key, V value) throws InterruptedException {
            lock.lock();
            try {
                while (store.size() >= maxSize) notFull.await(); // wait condition
                store.put(key, value);
                notEmpty.signalAll();                             // signal condition
            } finally {
                lock.unlock();                                    // always release
            }
        }

        /** Monitor method: blocks if cache is empty until an entry is available. */
        public V get(K key) throws InterruptedException {
            lock.lock();
            try {
                while (store.isEmpty()) notEmpty.await();
                V value = store.get(key);
                if (store.size() < maxSize) notFull.signalAll();
                return value;
            } finally {
                lock.unlock();
            }
        }

        /** Non-blocking read — returns null if absent (no waiting). */
        public V tryGet(K key) {
            lock.lock();
            try {
                return store.get(key);
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            lock.lock();
            try { return store.size(); } finally { lock.unlock(); }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PATTERN 3 — Half-Sync / Half-Async
    //   Intent: separate synchronous and asynchronous processing via a queue.
    //   Async tier  — accepts requests, never blocks (Concept #18)
    //   Queue tier  — decouples the two tiers (Concept #4)
    //   Sync tier   — processes requests synchronously in a thread pool
    // ══════════════════════════════════════════════════════════════════════════

    public static class HalfSyncHalfAsync<Request, Response> {

        // ── Async Tier: accepts without blocking ───────────────────────────
        private final BlockingQueue<Request> queue;

        // ── Sync Tier: processes in thread pool ────────────────────────────
        private final ExecutorService syncPool;
        private final java.util.function.Function<Request, Response> handler;
        private final List<CompletableFuture<Response>> pending = Collections.synchronizedList(new ArrayList<>());

        public HalfSyncHalfAsync(int queueCapacity, int threads,
                                  java.util.function.Function<Request, Response> handler) {
            this.queue    = new LinkedBlockingQueue<>(queueCapacity);
            this.syncPool = Executors.newFixedThreadPool(threads);
            this.handler  = handler;
        }

        /** Async tier: enqueue request immediately, return Future. */
        public CompletableFuture<Response> submit(Request request) {
            CompletableFuture<Response> future = new CompletableFuture<>();
            pending.add(future);

            if (!queue.offer(request)) {               // non-blocking: offer vs put
                future.completeExceptionally(new RejectedExecutionException("Queue full"));
                return future;
            }

            // Hand off to sync tier
            syncPool.submit(() -> {
                try {
                    Response result = handler.apply(queue.take()); // sync processing
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            return future;
        }

        public void shutdown() {
            syncPool.shutdown();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PATTERN 4 — Microkernel
    //   Intent: separate minimal core functionality from extended features.
    //   Core (kernel) provides: basic services + plugin registration.
    //   Plug-ins are loaded dynamically at runtime and registered by capability.
    // ══════════════════════════════════════════════════════════════════════════

    /** Core service contract for every plug-in. */
    public interface Plugin {
        String name();
        String version();
        void initialize(MicrokernelCore core);
        Map<String, Object> execute(Map<String, Object> params);
        void shutdown();
    }

    /** Minimal core: plugin registry + inter-plugin communication bus. */
    public static class MicrokernelCore {

        private final Map<String, Plugin> registry = new ConcurrentHashMap<>();
        private final Map<String, List<java.util.function.Consumer<Map<String,Object>>>> eventBus = new ConcurrentHashMap<>();

        // ── Plugin lifecycle management ────────────────────────────────────

        public void registerPlugin(Plugin plugin) {
            plugin.initialize(this);
            registry.put(plugin.name(), plugin);
        }

        public void unregisterPlugin(String name) {
            Plugin plugin = registry.remove(name);
            if (plugin != null) plugin.shutdown();
        }

        public Optional<Plugin> getPlugin(String name) {
            return Optional.ofNullable(registry.get(name));
        }

        public Map<String, Object> executePlugin(String name, Map<String, Object> params) {
            return getPlugin(name)
                .orElseThrow(() -> new NoSuchElementException("Plugin not found: " + name))
                .execute(params);
        }

        // ── Internal event bus (inter-plugin communication) ────────────────

        public void subscribe(String event, java.util.function.Consumer<Map<String,Object>> listener) {
            eventBus.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
        }

        public void publish(String event, Map<String, Object> data) {
            List<java.util.function.Consumer<Map<String,Object>>> listeners = eventBus.get(event);
            if (listeners != null) listeners.forEach(l -> l.accept(data));
        }

        public Set<String> registeredPlugins() { return Collections.unmodifiableSet(registry.keySet()); }
    }

    // ── Concrete plug-ins (Microkernel extension points) ─────────────────────

    /** Sort plug-in — extends core with sorting capability. */
    public static class SortPlugin implements Plugin {
        private MicrokernelCore core;

        @Override public String name()    { return "sort"; }
        @Override public String version() { return "1.0.0"; }

        @Override
        public void initialize(MicrokernelCore core) {
            this.core = core;
            core.subscribe("data.ready", params ->
                System.out.println("[SortPlugin] data.ready received"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> execute(Map<String, Object> params) {
            List<Integer> data = (List<Integer>) params.get("data");
            List<Integer> sorted = new ArrayList<>(data);
            Collections.sort(sorted);
            core.publish("sort.complete", Map.of("result", sorted));
            return Map.of("sorted", sorted, "algorithm", "Collections.sort");
        }

        @Override public void shutdown() {}
    }

    /** Metrics plug-in — extends core with telemetry capability. */
    public static class MetricsPlugin implements Plugin {
        private final Map<String, Long> counters = new ConcurrentHashMap<>();

        @Override public String name()    { return "metrics"; }
        @Override public String version() { return "1.0.0"; }

        @Override
        public void initialize(MicrokernelCore core) {
            core.subscribe("sort.complete", params ->
                counters.merge("sorts", 1L, Long::sum));
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> params) {
            return Collections.unmodifiableMap(counters);
        }

        @Override public void shutdown() { counters.clear(); }
    }
}
