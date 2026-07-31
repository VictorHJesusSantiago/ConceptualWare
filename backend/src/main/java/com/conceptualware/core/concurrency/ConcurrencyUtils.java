package com.conceptualware.core.concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

public class ConcurrencyUtils {

    public static <T> List<T> structuredFanOut(List<Callable<T>> tasks) throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new ArrayList<>();
            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(task::call));
            }
            scope.join();
            scope.throwIfFailed();
            List<T> results = new ArrayList<>();
            for (var subtask : subtasks) results.add(subtask.get());
            return results;
        }
    }

    public static <T> T structuredRace(List<Callable<T>> tasks) throws InterruptedException, ExecutionException {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<T>()) {
            for (Callable<T> task : tasks) scope.fork(task::call);
            scope.join();
            return scope.result();
        }
    }

    public record ThreadBenchmarkResult(String mode, int taskCount, long elapsedMillis, long peakThreadsEstimate) {}

    public static ThreadBenchmarkResult benchmarkVirtualThreads(int taskCount, int simulatedIoMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch latch = new CountDownLatch(taskCount);
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try { Thread.sleep(simulatedIoMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    latch.countDown();
                });
            }
            latch.await();
        }
        return new ThreadBenchmarkResult("virtual", taskCount, System.currentTimeMillis() - start, taskCount);
    }

    public static ThreadBenchmarkResult benchmarkPlatformThreads(int taskCount, int simulatedIoMillis, int poolSize) throws InterruptedException {
        long start = System.currentTimeMillis();
        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            CountDownLatch latch = new CountDownLatch(taskCount);
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    try { Thread.sleep(simulatedIoMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    latch.countDown();
                });
            }
            latch.await();
        }
        return new ThreadBenchmarkResult("platform", taskCount, System.currentTimeMillis() - start, poolSize);
    }

    private static final ThreadLocal<String> EXECUTION_CONTEXT = ThreadLocal.withInitial(() -> "default");

    public static void setContext(String ctx) { EXECUTION_CONTEXT.set(ctx); }
    public static String getContext()         { return EXECUTION_CONTEXT.get(); }
    public static void clearContext()         { EXECUTION_CONTEXT.remove(); }

    public static class BoundedBuffer<T> {
        private final Queue<T> buffer;
        private final int capacity;
        private final Lock lock = new ReentrantLock(true);
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

    public static class RateLimiter {
        private final Semaphore semaphore;
        private final int permitsPerWindow;
        private final long windowMs;

        public RateLimiter(int permitsPerWindow, long windowMs) {
            this.permitsPerWindow = permitsPerWindow;
            this.windowMs = windowMs;
            this.semaphore = new Semaphore(permitsPerWindow, true);

            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().factory());
            scheduler.scheduleAtFixedRate(() -> {
                int deficit = permitsPerWindow - semaphore.availablePermits();
                if (deficit > 0) semaphore.release(deficit);
            }, windowMs, windowMs, TimeUnit.MILLISECONDS);
        }

        public boolean tryAcquire() { return semaphore.tryAcquire(); }
        public void acquire() throws InterruptedException { semaphore.acquire(); }
    }

    public static class ReadWriteCache<K, V> {
        private final Map<K, V> cache = new HashMap<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        public V get(K key) {
            rwLock.readLock().lock();
            try {
                return cache.get(key);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        public void put(K key, V value) {
            rwLock.writeLock().lock();
            try {
                cache.put(key, value);
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    public static class LockFreeCounter {
        private final AtomicLong count = new AtomicLong(0);

        public long increment()            { return count.incrementAndGet(); }
        public long decrement()            { return count.decrementAndGet(); }
        public long addAndGet(long delta)  { return count.addAndGet(delta); }
        public long get()                  { return count.get(); }

        public boolean compareAndSet(long expected, long update) {
            return count.compareAndSet(expected, update);
        }
    }

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

    public static <T> CompletableFuture<T> withTimeout(
            CompletableFuture<T> future, long timeoutMs, T fallback) {
        CompletableFuture<T> timeout = new CompletableFuture<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> timeout.complete(fallback), timeoutMs, TimeUnit.MILLISECONDS);
        return future.applyToEither(timeout, Function.identity());
    }

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

    public static class VisibilityExample {
        private volatile boolean ready = false;
        private int value = 0;

        public void writer() {
            value = 42;
            ready = true;
        }

        public int reader() {
            while (!ready) Thread.onSpinWait();
            return value;
        }
    }

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
            left.fork();
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

    public static int[] forkJoinSort(int[] arr) {
        return ForkJoinPool.commonPool().invoke(new ParallelMergeSort(arr));
    }
}
