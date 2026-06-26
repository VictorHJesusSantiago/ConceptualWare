package com.conceptualware.core.concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

/**
 * Concept #17 — Sistemas Operacionais e Concorrência:
 *   Thread, Mutex, Semáforo, Monitor, Spinlock, Variável de condição,
 *   Deadlock, Race condition, Barreira de memória, Thread-local storage
 *   Lock otimista/pessimista, Lock-free, Wait-free
 *
 * Concept #18 — Programação Assíncrona e Concorrente:
 *   CompletableFuture, Thread Pool, Work-stealing scheduler,
 *   Semáforo assíncrono, Rate limiting assíncrono, Debounce, Throttle,
 *   Structured Concurrency, Virtual Threads (Project Loom)
 */
public class ConcurrencyUtils {

    // ── Thread-local storage (Concept #17) ────────────────────────────────────

    private static final ThreadLocal<String> EXECUTION_CONTEXT = ThreadLocal.withInitial(() -> "default");

    public static void setContext(String ctx) { EXECUTION_CONTEXT.set(ctx); }
    public static String getContext()         { return EXECUTION_CONTEXT.get(); }
    public static void clearContext()         { EXECUTION_CONTEXT.remove(); }

    // ── Mutex / ReentrantLock (Concept #17) ───────────────────────────────────

    public static class BoundedBuffer<T> {
        private final Queue<T> buffer;
        private final int capacity;
        private final Lock lock = new ReentrantLock(true); // fair=true (prevents starvation)
        private final Condition notFull  = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        public BoundedBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new ArrayDeque<>(capacity);
        }

        public void put(T item) throws InterruptedException {
            lock.lock();
            try {
                while (buffer.size() == capacity) notFull.await();
                buffer.offer(item);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        public T take() throws InterruptedException {
            lock.lock();
            try {
                while (buffer.isEmpty()) notEmpty.await();
                T item = buffer.poll();
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }

        public int size() { return buffer.size(); }
    }

    // ── Semaphore (Concept #17) ───────────────────────────────────────────────

    public static class RateLimiter {
        private final Semaphore semaphore;
        private final int permitsPerWindow;
        private final long windowMs;

        public RateLimiter(int permitsPerWindow, long windowMs) {
            this.permitsPerWindow = permitsPerWindow;
            this.windowMs = windowMs;
            this.semaphore = new Semaphore(permitsPerWindow, true);

            // Refill permits on schedule (sliding window)
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().factory()); // Virtual Thread (Concept #17)
            scheduler.scheduleAtFixedRate(() -> {
                int deficit = permitsPerWindow - semaphore.availablePermits();
                if (deficit > 0) semaphore.release(deficit);
            }, windowMs, windowMs, TimeUnit.MILLISECONDS);
        }

        public boolean tryAcquire() { return semaphore.tryAcquire(); }
        public void acquire() throws InterruptedException { semaphore.acquire(); }
    }

    // ── Read-Write Lock — optimistic/pessimistic (Concept #17) ────────────────

    public static class ReadWriteCache<K, V> {
        private final Map<K, V> cache = new HashMap<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        public V get(K key) {
            rwLock.readLock().lock(); // Multiple readers allowed
            try {
                return cache.get(key);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public void put(K key, V value) {
            rwLock.writeLock().lock(); // Exclusive write access
            try {
                cache.put(key, value);
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    // ── Atomic Operations — Lock-free (Concept #17) ───────────────────────────

    public static class LockFreeCounter {
        private final AtomicLong count = new AtomicLong(0);

        public long increment()            { return count.incrementAndGet(); }
        public long decrement()            { return count.decrementAndGet(); }
        public long addAndGet(long delta)  { return count.addAndGet(delta); }
        public long get()                  { return count.get(); }

        /** CAS — Compare and Set (wait-free operation). */
        public boolean compareAndSet(long expected, long update) {
            return count.compareAndSet(expected, update);
        }
    }

    // ── CompletableFuture — Async composition (Concept #18) ──────────────────

    /** Execute multiple algorithms in parallel and combine results. */
    public static CompletableFuture<List<Integer>> parallelSort(
            int[] arr, Executor executor) {

        int mid = arr.length / 2;
        int[] left  = Arrays.copyOfRange(arr, 0, mid);
        int[] right = Arrays.copyOfRange(arr, mid, arr.length);

        CompletableFuture<int[]> leftFuture  = CompletableFuture.supplyAsync(
            () -> com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(left), executor);
        CompletableFuture<int[]> rightFuture = CompletableFuture.supplyAsync(
            () -> com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(right), executor);

        return leftFuture.thenCombine(rightFuture, (l, r) -> {
            List<Integer> merged = new ArrayList<>();
            int i = 0, j = 0;
            while (i < l.length && j < r.length) {
                if (l[i] <= r[j]) merged.add(l[i++]);
                else               merged.add(r[j++]);
            }
            while (i < l.length) merged.add(l[i++]);
            while (j < r.length) merged.add(r[j++]);
            return merged;
        });
    }

    /** Timeout with fallback — Circuit Breaker pattern (Concept #12). */
    public static <T> CompletableFuture<T> withTimeout(
            CompletableFuture<T> future, long timeoutMs, T fallback) {
        CompletableFuture<T> timeout = new CompletableFuture<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> timeout.complete(fallback), timeoutMs, TimeUnit.MILLISECONDS);
        return future.applyToEither(timeout, Function.identity());
    }

    // ── Debounce — Concept #18 / #26 ─────────────────────────────────────────

    public static class Debouncer<T> {
        private final long delayMs;
        private final ScheduledExecutorService scheduler;
        private volatile ScheduledFuture<?> future;

        public Debouncer(long delayMs) {
            this.delayMs = delayMs;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().factory());
        }

        public void debounce(Runnable action) {
            if (future != null && !future.isDone()) future.cancel(false);
            future = scheduler.schedule(action, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    // ── Throttle — Concept #18 / #26 ─────────────────────────────────────────

    public static class Throttler {
        private final long periodMs;
        private volatile long lastCallMs = 0;

        public Throttler(long periodMs) { this.periodMs = periodMs; }

        public synchronized boolean tryRun(Runnable action) {
            long now = System.currentTimeMillis();
            if (now - lastCallMs >= periodMs) {
                lastCallMs = now;
                action.run();
                return true;
            }
            return false;
        }
    }

    // ── Memory Barrier / Volatile semantics ───────────────────────────────────

    public static class VisibilityExample {
        // volatile ensures write is visible to other threads (memory barrier)
        private volatile boolean ready = false;
        private int value = 0;

        public void writer() {
            value = 42;
            ready = true; // memory fence — all prior writes visible after this
        }

        public int reader() {
            while (!ready) Thread.onSpinWait(); // spin (spinlock) — Concept #17
            return value;
        }
    }

    // ── Fork/Join Work-Stealing Parallel Algorithm ────────────────────────────

    public static class ParallelMergeSort extends RecursiveTask<int[]> {
        private static final int THRESHOLD = 1000;
        private final int[] arr;

        public ParallelMergeSort(int[] arr) { this.arr = arr; }

        @Override
        protected int[] compute() {
            if (arr.length <= THRESHOLD) {
                return com.conceptualware.core.algorithms.sorting.SortingAlgorithms.mergeSort(arr);
            }
            int mid = arr.length / 2;
            ParallelMergeSort left  = new ParallelMergeSort(Arrays.copyOfRange(arr, 0, mid));
            ParallelMergeSort right = new ParallelMergeSort(Arrays.copyOfRange(arr, mid, arr.length));
            left.fork();  // submit to work-stealing thread pool
            int[] r = right.compute();
            int[] l = left.join();
            return mergeArrays(l, r);
        }

        private int[] mergeArrays(int[] l, int[] r) {
            int[] result = new int[l.length + r.length];
            int i = 0, j = 0, k = 0;
            while (i < l.length && j < r.length) result[k++] = l[i] <= r[j] ? l[i++] : r[j++];
            while (i < l.length) result[k++] = l[i++];
            while (j < r.length) result[k++] = r[j++];
            return result;
        }
    }

    /** Run parallel merge sort using ForkJoinPool (work-stealing). */
    public static int[] forkJoinSort(int[] arr) {
        return ForkJoinPool.commonPool().invoke(new ParallelMergeSort(arr));
    }
}
