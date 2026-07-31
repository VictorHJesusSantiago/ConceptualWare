package com.conceptualware.core.patterns;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

public class ConcurrencyPatterns {

    @FunctionalInterface
    public interface MethodRequest<T> {
        T execute() throws Exception;
    }

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

        public void put(K key, V value) throws InterruptedException {
            lock.lock();
            try {
                while (store.size() >= maxSize) notFull.await();
                store.put(key, value);
                notEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }

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

    public static class HalfSyncHalfAsync<Request, Response> {

        private final BlockingQueue<Request> queue;

        private final ExecutorService syncPool;
        private final java.util.function.Function<Request, Response> handler;
        private final List<CompletableFuture<Response>> pending = Collections.synchronizedList(new ArrayList<>());

        public HalfSyncHalfAsync(int queueCapacity, int threads,
                                  java.util.function.Function<Request, Response> handler) {
            this.queue    = new LinkedBlockingQueue<>(queueCapacity);
            this.syncPool = Executors.newFixedThreadPool(threads);
            this.handler  = handler;
        }

        public CompletableFuture<Response> submit(Request request) {
            CompletableFuture<Response> future = new CompletableFuture<>();
            pending.add(future);

            if (!queue.offer(request)) {
                future.completeExceptionally(new RejectedExecutionException("Queue full"));
                return future;
            }

            syncPool.submit(() -> {
                try {
                    Response result = handler.apply(queue.take());
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

    public interface Plugin {
        String name();
        String version();
        void initialize(MicrokernelCore core);
        Map<String, Object> execute(Map<String, Object> params);
        void shutdown();
    }

    public static class MicrokernelCore {

        private final Map<String, Plugin> registry = new ConcurrentHashMap<>();
        private final Map<String, List<java.util.function.Consumer<Map<String,Object>>>> eventBus = new ConcurrentHashMap<>();

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

        public void subscribe(String event, java.util.function.Consumer<Map<String,Object>> listener) {
            eventBus.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
        }

        public void publish(String event, Map<String, Object> data) {
            List<java.util.function.Consumer<Map<String,Object>>> listeners = eventBus.get(event);
            if (listeners != null) listeners.forEach(l -> l.accept(data));
        }

        public Set<String> registeredPlugins() { return Collections.unmodifiableSet(registry.keySet()); }
    }

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
