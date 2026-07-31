package com.conceptualware.core.patterns;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public class AdvancedConcurrencyPatterns {

    public static class ActiveObject<T> {
        private final BlockingQueue<Callable<Void>> queue;
        private final Thread scheduler;
        private volatile boolean running = true;

        public ActiveObject(int queueCapacity) {
            this.queue     = new LinkedBlockingQueue<>(queueCapacity);
            this.scheduler = Thread.ofVirtual().start(this::runScheduler);
        }

        public void submit(Runnable command) {
            try {
                queue.put(() -> { command.run(); return null; });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

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

        private void runScheduler() {
            while (running || !queue.isEmpty()) {
                try {
                    Callable<Void> request = queue.poll(10, TimeUnit.MILLISECONDS);
                    if (request != null) request.call();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                }
            }
        }

        public void shutdown() throws InterruptedException {
            running = false;
            scheduler.join(5000);
        }
    }

    public static class ActiveBankAccount {
        private final ActiveObject<Long> activeObject = new ActiveObject<>(1000);
        private long balance;

        public ActiveBankAccount(long initialBalance) {
            this.balance = initialBalance;
        }

        public CompletableFuture<Long> deposit(long amount) {
            return activeObject.call(() -> {
                balance += amount;
                return balance;
            });
        }

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

    public static class MonitorBoundedBuffer<T> {
        private final Object[] buffer;
        private int head = 0, tail = 0, count = 0;
        private final int capacity;

        public MonitorBoundedBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer   = new Object[capacity];
        }

        public synchronized void put(T item) throws InterruptedException {
            while (count == capacity) wait();

            buffer[tail] = item;
            tail = (tail + 1) % capacity;
            count++;
            notifyAll();
        }

        @SuppressWarnings("unchecked")
        public synchronized T take() throws InterruptedException {
            while (count == 0) wait();

            T item = (T) buffer[head];
            buffer[head] = null;
            head = (head + 1) % capacity;
            count--;
            notifyAll();
            return item;
        }

        public synchronized int size()    { return count; }
        public synchronized boolean full() { return count == capacity; }
        public synchronized boolean empty(){ return count == 0; }
    }

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
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        public T take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty()) notEmpty.await();
                T item = queue.poll();
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    public static class HalfSyncHalfAsync {

        public record Request(String id, byte[] payload, long arrivedAt) {}

        public record Response(String requestId, String result, long processedAt) {}

        public static class AsyncLayer {
            private final BlockingQueue<Request> queue;
            private final AtomicLong requestCounter = new AtomicLong(0);

            public AsyncLayer(int queueCapacity) {
                this.queue = new LinkedBlockingQueue<>(queueCapacity);
            }

            public boolean accept(byte[] payload) {
                String id = "req-" + requestCounter.incrementAndGet();
                return queue.offer(new Request(id, payload, System.nanoTime()));
            }

            public BlockingQueue<Request> queue() { return queue; }
        }

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

                for (int i = 0; i < poolSize; i++) {
                    threadPool.submit(this::workerLoop);
                }
            }

            private void workerLoop() {
                while (active) {
                    try {
                        Request req = inputQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (req == null) continue;

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

            private String processRequest(Request req) {
                return "processed-" + req.id() + "[" + req.payload().length + "B]";
            }

            public BlockingQueue<Response> responses() { return outputQueue; }

            public void shutdown() throws InterruptedException {
                active = false;
                threadPool.shutdown();
                threadPool.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        public static class Server {
            private final AsyncLayer asyncLayer;
            private final SyncLayer  syncLayer;

            public Server(int queueCapacity, int workerCount) {
                this.asyncLayer = new AsyncLayer(queueCapacity);
                this.syncLayer  = new SyncLayer(asyncLayer.queue(), workerCount, queueCapacity);
            }

            public boolean onDataReceived(byte[] data) {
                return asyncLayer.accept(data);
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
