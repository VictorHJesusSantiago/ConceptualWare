package com.conceptualware.core.patterns;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * Concept #13 — Advanced Concurrency Patterns (POSA vol. 2)
 *
 * Four patterns missing from the original implementation:
 *
 * 1. Active Object    — decouples method invocation from execution;
 *                       each object has its own thread + message queue
 * 2. Monitor Object   — synchronizes concurrent method execution;
 *                       only one method runs at a time inside the object
 * 3. Half-Sync/Half-Async — two-layer architecture: async I/O layer + sync processing layer,
 *                           bridged by a queue
 * 4. Microkernel      — minimal core + plug-in extensions;
 *                       see MicrokernelPattern.java
 *
 * Reference: "Pattern-Oriented Software Architecture, Volume 2:
 *             Patterns for Concurrent and Networked Objects" (Schmidt et al., 2000)
 */
public class AdvancedConcurrencyPatterns {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Active Object
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Active Object (POSA2 — "Active Object"):
     *
     * Decouples METHOD INVOCATION (in the client's thread) from
     * METHOD EXECUTION (in the object's private thread).
     *
     * Components:
     *   Proxy:         Client-facing interface; converts calls to MethodRequests
     *   MethodRequest: Callable stored in the activation queue
     *   Activation Queue: Bounded queue of pending requests
     *   Scheduler:     The Active Object's private thread; dequeues and executes
     *   Future:        Returned to caller; holds the eventual result
     *
     * Sequence:
     *   Client → Proxy.method()   (client thread)
     *         → enqueue MethodRequest
     *         → return CompletableFuture to caller immediately
     *   Scheduler (own thread): dequeue → execute → complete future
     *
     * Benefits:
     *   - Client never blocks on the active object's method
     *   - All access to shared state serialized by the scheduler's single thread
     *   - Backpressure via bounded queue
     *
     * Used in: event loops, UI toolkits, game entities, I/O actors
     */
    public static class ActiveObject<T> {
        private final BlockingQueue<Callable<Void>> queue;
        private final Thread scheduler;
        private volatile boolean running = true;

        /** Create an Active Object with a bounded command queue. */
        public ActiveObject(int queueCapacity) {
            this.queue     = new LinkedBlockingQueue<>(queueCapacity);
            this.scheduler = Thread.ofVirtual().start(this::runScheduler);
        }

        /** Submit a void command (fire-and-forget). */
        public void submit(Runnable command) {
            try {
                queue.put(() -> { command.run(); return null; });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** Submit a command and get a future for the result. */
        public <R> CompletableFuture<R> call(Supplier<R> command) {
            CompletableFuture<R> future = new CompletableFuture<>();
            try {
                queue.put(() -> {
                    try   { future.complete(command.get()); }
                    catch (Throwable t) { future.completeExceptionally(t); }
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
            }
            return future;
        }

        /** Scheduler loop — single thread that processes all commands sequentially. */
        private void runScheduler() {
            while (running || !queue.isEmpty()) {
                try {
                    Callable<Void> request = queue.poll(10, TimeUnit.MILLISECONDS);
                    if (request != null) request.call();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // log and continue — scheduler must not die on command errors
                }
            }
        }

        /** Graceful shutdown — processes remaining commands then stops. */
        public void shutdown() throws InterruptedException {
            running = false;
            scheduler.join(5000);
        }
    }

    /**
     * Example: Bank account as Active Object.
     * Concurrent deposits/withdrawals are serialized by the internal scheduler.
     * No explicit locking needed in the business logic.
     */
    public static class ActiveBankAccount {
        private final ActiveObject<Long> activeObject = new ActiveObject<>(1000);
        private long balance;

        public ActiveBankAccount(long initialBalance) {
            this.balance = initialBalance;
        }

        /** Non-blocking deposit — returns future of new balance. */
        public CompletableFuture<Long> deposit(long amount) {
            return activeObject.call(() -> {
                balance += amount;
                return balance;
            });
        }

        /** Non-blocking withdraw — returns future of success. */
        public CompletableFuture<Boolean> withdraw(long amount) {
            return activeObject.call(() -> {
                if (balance >= amount) { balance -= amount; return true; }
                return false;
            });
        }

        public CompletableFuture<Long> getBalance() {
            return activeObject.call(() -> balance);
        }

        public void shutdown() throws InterruptedException { activeObject.shutdown(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Monitor Object
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Monitor Object (POSA2 — "Monitor Object"):
     *
     * Ensures that ONLY ONE method executes at a time within an object,
     * regardless of how many threads call concurrently.
     * Waiting threads are suspended until the condition they need is met.
     *
     * Java's `synchronized` keyword + wait/notify implement this pattern.
     *
     * Components:
     *   Monitor lock:      intrinsic lock (synchronized block/method)
     *   Condition variable: wait() / notify() / notifyAll()
     *   Monitor methods:   synchronized methods — only one runs at a time
     *
     * vs. Active Object:
     *   Monitor Object: client BLOCKS until method completes (synchronous)
     *   Active Object:  client gets future, does NOT block (asynchronous)
     *
     * Classic example: bounded buffer (producer-consumer).
     */
    public static class MonitorBoundedBuffer<T> {
        private final Object[] buffer;
        private int head = 0, tail = 0, count = 0;
        private final int capacity;

        public MonitorBoundedBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer   = new Object[capacity];
        }

        /**
         * put: blocks if buffer is full (waits on "not full" condition).
         * Synchronized — only one thread enters at a time.
         */
        public synchronized void put(T item) throws InterruptedException {
            // Monitor condition: buffer must not be full
            while (count == capacity) wait();   // suspend until notified

            buffer[tail] = item;
            tail = (tail + 1) % capacity;
            count++;
            notifyAll();   // wake threads waiting on "not empty"
        }

        /**
         * take: blocks if buffer is empty (waits on "not empty" condition).
         */
        @SuppressWarnings("unchecked")
        public synchronized T take() throws InterruptedException {
            while (count == 0) wait();   // suspend until item available

            T item = (T) buffer[head];
            buffer[head] = null;         // help GC
            head = (head + 1) % capacity;
            count--;
            notifyAll();   // wake threads waiting on "not full"
            return item;
        }

        public synchronized int size()    { return count; }
        public synchronized boolean full() { return count == capacity; }
        public synchronized boolean empty(){ return count == 0; }
    }

    /**
     * Monitor Object with ReentrantLock + explicit Conditions.
     * More control than intrinsic lock: separate conditions for full vs empty,
     * tryLock with timeout, interruptible lock acquisition.
     */
    public static class ExplicitMonitorBuffer<T> {
        private final java.util.concurrent.locks.ReentrantLock lock
            = new java.util.concurrent.locks.ReentrantLock();
        private final java.util.concurrent.locks.Condition notFull  = lock.newCondition();
        private final java.util.concurrent.locks.Condition notEmpty = lock.newCondition();

        private final Queue<T> queue;
        private final int capacity;

        public ExplicitMonitorBuffer(int capacity) {
            this.capacity = capacity;
            this.queue    = new ArrayDeque<>(capacity);
        }

        public void put(T item) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.size() >= capacity) notFull.await();
                queue.add(item);
                notEmpty.signal();   // wake ONE waiting consumer (more efficient than notifyAll)
            } finally {
                lock.unlock();
            }
        }

        public T take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty()) notEmpty.await();
                T item = queue.poll();
                notFull.signal();    // wake ONE waiting producer
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Half-Sync/Half-Async
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Half-Sync/Half-Async (HS/HA) Pattern (POSA2):
     *
     * Separates processing into TWO LAYERS:
     *   Async layer (bottom): handles I/O events without blocking; interrupts/callbacks
     *   Sync layer (top):     processes requests synchronously using threads/thread pool
     *   Queue (bridge):       buffers work items between the layers
     *
     * Flow:
     *   I/O event arrives → Async layer enqueues work item (non-blocking)
     *   Sync layer dequeues and processes work items (blocking OK — separate threads)
     *
     * Benefits:
     *   - I/O handling stays fast (never blocks)
     *   - Business logic stays simple (synchronous, no callbacks)
     *   - Clear separation: I/O code vs. business logic
     *
     * Examples in the wild:
     *   - NGINX: event loop (async) + worker processes (sync)
     *   - Node.js: libuv event loop (async) + thread pool (sync for fs/crypto)
     *   - Java NIO: Selector (async) + ExecutorService workers (sync)
     *   - Netty: Boss group (async accept) + Worker group (sync handler)
     *
     * Variants:
     *   Leader/Followers:  dynamic assignment of async role to one of N threads
     *   Reactor:           maps I/O events to handlers (see separate pattern)
     */
    public static class HalfSyncHalfAsync {

        /** Work item flowing from async to sync layer. */
        public record Request(String id, byte[] payload, long arrivedAt) {}

        /** Result flowing back to the client. */
        public record Response(String requestId, String result, long processedAt) {}

        /**
         * Async layer: accepts incoming work items from I/O sources.
         * Never blocks — just enqueues and returns immediately.
         */
        public static class AsyncLayer {
            private final BlockingQueue<Request> queue;
            private final AtomicLong requestCounter = new AtomicLong(0);

            public AsyncLayer(int queueCapacity) {
                this.queue = new LinkedBlockingQueue<>(queueCapacity);
            }

            /** Called from I/O thread (interrupt handler, NIO selector, etc.) */
            public boolean accept(byte[] payload) {
                String id = "req-" + requestCounter.incrementAndGet();
                return queue.offer(new Request(id, payload, System.nanoTime()));
                // offer() is non-blocking: returns false if queue full (backpressure)
            }

            public BlockingQueue<Request> queue() { return queue; }
        }

        /**
         * Sync layer: thread pool that processes requests from the queue.
         * Business logic runs here — blocking is fine (dedicated threads).
         */
        public static class SyncLayer {
            private final BlockingQueue<Request> inputQueue;
            private final BlockingQueue<Response> outputQueue;
            private final ExecutorService threadPool;
            private volatile boolean active = true;

            public SyncLayer(BlockingQueue<Request> inputQueue,
                             int poolSize,
                             int outputCapacity) {
                this.inputQueue  = inputQueue;
                this.outputQueue = new LinkedBlockingQueue<>(outputCapacity);
                this.threadPool  = Executors.newFixedThreadPool(poolSize,
                    r -> Thread.ofPlatform().name("sync-worker").daemon(true).unstarted(r));

                // Start worker threads that pull from input queue
                for (int i = 0; i < poolSize; i++) {
                    threadPool.submit(this::workerLoop);
                }
            }

            private void workerLoop() {
                while (active) {
                    try {
                        Request req = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (req == null) continue;

                        // Business logic runs synchronously here (can block)
                        String result = processRequest(req);

                        outputQueue.offer(new Response(
                            req.id(), result, System.nanoTime()
                        ));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            /** Simulate business logic processing. */
            private String processRequest(Request req) {
                // In real app: parse, validate, call DB, compute, etc.
                return "processed-" + req.id() + "[" + req.payload().length + "B]";
            }

            public BlockingQueue<Response> responses() { return outputQueue; }

            public void shutdown() throws InterruptedException {
                active = false;
                threadPool.shutdown();
                threadPool.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        /**
         * Complete HS/HA system wiring Async + Sync layers.
         */
        public static class Server {
            private final AsyncLayer asyncLayer;
            private final SyncLayer  syncLayer;

            public Server(int queueCapacity, int workerCount) {
                this.asyncLayer = new AsyncLayer(queueCapacity);
                this.syncLayer  = new SyncLayer(asyncLayer.queue(), workerCount, queueCapacity);
            }

            /** I/O callback — called from event loop / NIO selector. */
            public boolean onDataReceived(byte[] data) {
                return asyncLayer.accept(data);   // non-blocking
            }

            public Optional<Response> pollResponse(long timeoutMs)
                    throws InterruptedException {
                return Optional.ofNullable(
                    syncLayer.responses().poll(timeoutMs, TimeUnit.MILLISECONDS)
                );
            }

            public void shutdown() throws InterruptedException { syncLayer.shutdown(); }
        }
    }
}
