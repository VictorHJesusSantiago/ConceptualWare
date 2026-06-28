package com.conceptualware.core.patterns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Advanced Concurrency Patterns")
class AdvancedConcurrencyPatternsTest {

    // ── Active Object ──────────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    @DisplayName("ActiveObject: submit executes command asynchronously")
    void testActiveObjectSubmit() throws Exception {
        var ao = new AdvancedConcurrencyPatterns.ActiveObject<Void>(100);
        CountDownLatch latch = new CountDownLatch(1);
        ao.submit(latch::countDown);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        ao.shutdown();
    }

    @Test
    @Timeout(5)
    @DisplayName("ActiveObject: call returns correct result via CompletableFuture")
    void testActiveObjectCall() throws Exception {
        var ao = new AdvancedConcurrencyPatterns.ActiveObject<Long>(100);
        CompletableFuture<Integer> future = ao.call(() -> 6 * 7);
        assertEquals(42, future.get(3, TimeUnit.SECONDS));
        ao.shutdown();
    }

    @Test
    @Timeout(5)
    @DisplayName("ActiveBankAccount: concurrent deposits serialize correctly")
    void testActiveBankAccountDeposits() throws Exception {
        var account = new AdvancedConcurrencyPatterns.ActiveBankAccount(0);
        int depositCount = 100;
        int amount = 10;

        List<CompletableFuture<Long>> futures = new ArrayList<>();
        for (int i = 0; i < depositCount; i++) {
            futures.add(account.deposit(amount));
        }

        // Wait for all deposits
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

        long balance = account.getBalance().get(3, TimeUnit.SECONDS);
        assertEquals(depositCount * amount, balance);
        account.shutdown();
    }

    // ── Monitor Object ────────────────────────────────────────────────────────

    @Test
    @Timeout(5)
    @DisplayName("MonitorBoundedBuffer: put and take preserve FIFO order")
    void testMonitorBufferFifo() throws Exception {
        var buf = new AdvancedConcurrencyPatterns.MonitorBoundedBuffer<Integer>(5);
        buf.put(1);
        buf.put(2);
        buf.put(3);
        assertEquals(1, buf.take());
        assertEquals(2, buf.take());
        assertEquals(3, buf.take());
    }

    @Test
    @Timeout(5)
    @DisplayName("MonitorBoundedBuffer: producer blocks when full, resumes after take")
    void testMonitorBufferBlocking() throws Exception {
        var buf = new AdvancedConcurrencyPatterns.MonitorBoundedBuffer<Integer>(1);
        buf.put(42);    // fills the buffer

        // Producer thread will block trying to put second item
        var producerDone = new CountDownLatch(1);
        Thread producer = Thread.ofVirtual().start(() -> {
            try {
                buf.put(99);  // blocks until consumer takes
                producerDone.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer releases producer by taking
        Thread.sleep(100);
        assertEquals(42, buf.take());   // unblocks producer

        assertTrue(producerDone.await(3, TimeUnit.SECONDS));
        assertEquals(99, buf.take());
        producer.join();
    }

    @Test
    @Timeout(5)
    @DisplayName("ExplicitMonitorBuffer: put and take via ReentrantLock + Condition")
    void testExplicitMonitorBuffer() throws Exception {
        var buf = new AdvancedConcurrencyPatterns.ExplicitMonitorBuffer<String>(3);
        buf.put("hello");
        buf.put("world");
        assertEquals("hello", buf.take());
        assertEquals("world", buf.take());
    }

    // ── Half-Sync/Half-Async ──────────────────────────────────────────────────

    @Test
    @Timeout(5)
    @DisplayName("HalfSyncHalfAsync: data accepted async and processed sync")
    void testHalfSyncHalfAsync() throws Exception {
        var server = new AdvancedConcurrencyPatterns.HalfSyncHalfAsync.Server(100, 2);

        byte[] payload = "test-data".getBytes();
        boolean accepted = server.onDataReceived(payload);   // non-blocking
        assertTrue(accepted, "Async layer should accept the request");

        // Sync layer processes and enqueues response
        Optional<AdvancedConcurrencyPatterns.HalfSyncHalfAsync.Response> response =
            server.pollResponse(3000);

        assertTrue(response.isPresent());
        assertTrue(response.get().result().contains("processed"));

        server.shutdown();
    }

    @Test
    @Timeout(5)
    @DisplayName("HalfSyncHalfAsync: multiple requests all get responses")
    void testHalfSyncHalfAsyncMultiple() throws Exception {
        var server = new AdvancedConcurrencyPatterns.HalfSyncHalfAsync.Server(100, 4);
        int count = 10;

        for (int i = 0; i < count; i++) {
            server.onDataReceived(("request-" + i).getBytes());
        }

        int received = 0;
        while (received < count) {
            Optional<AdvancedConcurrencyPatterns.HalfSyncHalfAsync.Response> resp =
                server.pollResponse(2000);
            if (resp.isEmpty()) break;
            received++;
        }

        assertEquals(count, received, "All requests must produce responses");
        server.shutdown();
    }
}
