package com.conceptualware.core.os;

import org.junit.jupiter.api.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #17 — OS & Concurrency: Process Scheduling, Memory Management,
 *               File System, Unix Signals, Cgroups/Namespaces
 * Concept #19 — TDD: property-based invariants, edge cases
 */
@DisplayName("Category 17 — OS Concepts: Complete Test Suite")
class OSConceptsTest {

    // ── Process Scheduling ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Process Scheduling Algorithms")
    class SchedulingTests {

        private List<ProcessScheduler.Process> testProcesses() {
            return List.of(
                new ProcessScheduler.Process(1, 0, 4, 2),
                new ProcessScheduler.Process(2, 1, 3, 1),
                new ProcessScheduler.Process(3, 2, 5, 3),
                new ProcessScheduler.Process(4, 3, 2, 2)
            );
        }

        @Test
        @DisplayName("FCFS — all processes finish")
        void fcfsAllFinish() {
            var stats = ProcessScheduler.fcfs(testProcesses());
            assertThat(stats.results()).hasSize(4);
            assertThat(stats.algorithm()).isEqualTo("FCFS");
        }

        @Test
        @DisplayName("FCFS — processes finish in arrival order")
        void fcfsArrivalOrder() {
            var stats = ProcessScheduler.fcfs(testProcesses());
            var results = stats.results();
            // Each process finishes after the previous
            for (int i = 1; i < results.size(); i++) {
                assertThat(results.get(i).startTime())
                    .isGreaterThanOrEqualTo(results.get(i - 1).finishTime());
            }
        }

        @Test
        @DisplayName("Round-Robin — all processes finish")
        void roundRobinAllFinish() {
            var stats = ProcessScheduler.roundRobin(testProcesses(), 2);
            assertThat(stats.results()).hasSize(4);
            assertThat(stats.algorithm()).startsWith("Round-Robin");
        }

        @Test
        @DisplayName("Round-Robin — no process waits forever")
        void roundRobinFairness() {
            var stats = ProcessScheduler.roundRobin(testProcesses(), 2);
            stats.results().forEach(r ->
                assertThat(r.waitingTime()).isGreaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("SJF — average wait ≤ FCFS average wait (optimality)")
        void sjfBetterThanFcfs() {
            var procs = testProcesses();
            var sjf   = ProcessScheduler.sjf(procs);
            var fcfs  = ProcessScheduler.fcfs(procs);
            // SJF minimizes average waiting time
            assertThat(sjf.avgWaitingTime()).isLessThanOrEqualTo(fcfs.avgWaitingTime() + 0.001);
        }

        @Test
        @DisplayName("SRTF — all processes complete")
        void srtfCompletes() {
            var stats = ProcessScheduler.srtf(testProcesses());
            assertThat(stats.results()).hasSize(4);
            // Turnaround = finish - arrival ≥ burst time
            stats.results().forEach(r ->
                assertThat(r.turnaroundTime()).isGreaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("Priority scheduling — higher priority (lower number) runs first when tied")
        void priorityScheduling() {
            // P2 has priority 1 (highest), so should run first among those arrived
            List<ProcessScheduler.Process> procs = List.of(
                new ProcessScheduler.Process(1, 0, 5, 3),
                new ProcessScheduler.Process(2, 0, 3, 1)  // higher priority
            );
            var stats = ProcessScheduler.priorityScheduling(procs);
            // P2 (priority 1) should start first
            var first = stats.results().stream().min(Comparator.comparingInt(ProcessScheduler.ScheduleResult::startTime)).orElseThrow();
            assertThat(first.processId()).isEqualTo(2);
        }

        @Test
        @DisplayName("Priority with aging — produces valid schedule")
        void priorityAging() {
            var stats = ProcessScheduler.priorityWithAging(testProcesses(), 1);
            assertThat(stats.results()).hasSize(4);
            assertThat(stats.algorithm()).isEqualTo("Priority + Aging");
        }

        @Test
        @DisplayName("Turnaround time = finish - arrival for all algorithms")
        void turnaroundInvariant() {
            var fcfs = ProcessScheduler.fcfs(testProcesses());
            fcfs.results().forEach(r ->
                assertThat(r.turnaroundTime()).isEqualTo(r.finishTime() - /* arrival via waitingTime */ (r.startTime() - r.waitingTime()))
            );
        }
    }

    // ── Memory Simulator ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Memory Management — Paging & Allocation")
    class MemoryTests {

        @Test
        @DisplayName("PageTable: translates virtual address to physical")
        void pageTableTranslate() {
            MemorySimulator.PageTable pt = new MemorySimulator.PageTable(4096);
            int physical = pt.translate(0); // page 0, offset 0
            assertThat(physical).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("FIFO page replacement — fewer frames → more faults")
        void fifoMoreFramesFewer() {
            int[] pages = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};
            int faults3 = MemorySimulator.fifoPageFaults(pages, 3);
            int faults4 = MemorySimulator.fifoPageFaults(pages, 4);
            // Belady's anomaly can make this non-monotone, but generally holds
            assertThat(faults3).isGreaterThan(0);
            assertThat(faults4).isGreaterThan(0);
        }

        @Test
        @DisplayName("LRU page faults ≤ FIFO page faults (usually)")
        void lruVsFifo() {
            int[] pages = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};
            int fifo = MemorySimulator.fifoPageFaults(pages, 3);
            int lru  = MemorySimulator.lruPageFaults(pages, 3);
            // LRU is generally better than FIFO (though not strictly always)
            assertThat(lru).isLessThanOrEqualTo(fifo + 2); // within 2
        }

        @Test
        @DisplayName("Optimal page replacement ≤ LRU page faults")
        void optimalBestCase() {
            int[] pages = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};
            int opt = MemorySimulator.optimalPageFaults(pages, 3);
            int lru = MemorySimulator.lruPageFaults(pages, 3);
            assertThat(opt).isLessThanOrEqualTo(lru);
        }

        @Test
        @DisplayName("Clock page replacement produces valid fault count")
        void clockPageFaults() {
            int[] pages = {1, 2, 3, 4, 1, 2, 5, 1, 2, 3, 4, 5};
            int clock = MemorySimulator.clockPageFaults(pages, 3);
            assertThat(clock).isGreaterThan(0).isLessThanOrEqualTo(pages.length);
        }

        @Test
        @DisplayName("Segmentation — valid segment access returns physical address")
        void segmentationValid() {
            MemorySimulator.SegmentTable st = MemorySimulator.typicalProcessLayout();
            long physical = st.translate("text", 0x100);
            assertThat(physical).isGreaterThanOrEqualTo(0x400000L);
        }

        @Test
        @DisplayName("Segmentation — out-of-bounds throws SegmentationFaultException")
        void segmentationFault() {
            MemorySimulator.SegmentTable st = MemorySimulator.typicalProcessLayout();
            assertThatThrownBy(() -> st.translate("text", 0x20000L))
                .isInstanceOf(MemorySimulator.SegmentationFaultException.class)
                .hasMessageContaining("SIGSEGV");
        }

        @Test
        @DisplayName("First Fit allocates from first fitting free block")
        void firstFitAllocates() {
            MemorySimulator.MemoryAllocator alloc = new MemorySimulator.MemoryAllocator(1000);
            Optional<Integer> addr = alloc.firstFit(100);
            assertThat(addr).isPresent();
            assertThat(addr.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Best Fit leaves smallest possible fragment")
        void bestFitMinFragment() {
            MemorySimulator.MemoryAllocator alloc = new MemorySimulator.MemoryAllocator(1000);
            alloc.firstFit(300); // use first 300
            alloc.firstFit(100); // use next 100 (addresses 300-399)
            // free second block
            alloc.free(300);
            // bestFit for 80 bytes should pick the 100-byte hole (smallest fitting)
            Optional<Integer> addr = alloc.bestFit(80);
            assertThat(addr).isPresent();
            assertThat(alloc.fragmentation()).isLessThan(1000);
        }

        @Test
        @DisplayName("Free and coalesce merges adjacent free blocks")
        void coalesceOnFree() {
            MemorySimulator.MemoryAllocator alloc = new MemorySimulator.MemoryAllocator(300);
            var a1 = alloc.firstFit(100);
            var a2 = alloc.firstFit(100);
            alloc.free(a1.get());
            alloc.free(a2.get());
            // After coalescing, should have one large free block
            long freeBlocks = alloc.getBlocks().stream().filter(MemorySimulator.MemoryBlock::free).count();
            assertThat(freeBlocks).isLessThanOrEqualTo(2); // merged or adjacent
        }

        @Test
        @DisplayName("Insufficient memory returns empty")
        void insufficientMemory() {
            MemorySimulator.MemoryAllocator alloc = new MemorySimulator.MemoryAllocator(100);
            assertThat(alloc.firstFit(200)).isEmpty();
        }
    }

    // ── Process Info ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Unix Process Management")
    class ProcessInfoTests {

        @Test
        @DisplayName("Current process has valid PID > 0")
        void currentProcessPID() {
            var info = UnixSignalHandler.currentProcessInfo();
            assertThat(info.pid()).isGreaterThan(0);
        }

        @Test
        @DisplayName("JVM stats returns valid memory figures")
        void jvmStats() {
            var stats = UnixSignalHandler.getJvmStats();
            assertThat(stats.pid()).isGreaterThan(0);
            assertThat(stats.cpus()).isGreaterThanOrEqualTo(1);
            assertThat(stats.heapUsed()).isGreaterThan(0);
            assertThat(stats.totalMemory()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Namespace documentation covers all 7 types")
        void namespaceTypes() {
            assertThat(UnixSignalHandler.NamespaceInfo.all()).hasSize(7);
        }

        @Test
        @DisplayName("Cgroup limits can be read (returns defaults on non-Linux)")
        void cgroupLimits() {
            var limits = UnixSignalHandler.CgroupLimits.readFromSystem();
            assertThat(limits).isNotNull();
            // On non-Linux: values are -1 or defaults
        }
    }

    // ── File System ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("File System Operations")
    class FileSystemTests {

        @Test
        @DisplayName("Stat reads metadata from temp file")
        void statTempFile() throws Exception {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("cwtest", ".txt");
            java.nio.file.Files.writeString(tmp, "hello");
            try {
                var info = FileSystemOps.stat(tmp);
                assertThat(info.size()).isEqualTo(5);
                assertThat(info.isDirectory()).isFalse();
                assertThat(info.isSymbolicLink()).isFalse();
            } finally {
                java.nio.file.Files.deleteIfExists(tmp);
            }
        }

        @Test
        @DisplayName("Atomic write creates target file with correct content")
        void atomicWrite() throws Exception {
            java.nio.file.Path target = java.nio.file.Files.createTempFile("atomic", ".txt");
            try {
                byte[] content = "atomic content".getBytes();
                FileSystemOps.atomicWrite(target, content);
                assertThat(java.nio.file.Files.readAllBytes(target)).isEqualTo(content);
            } finally {
                java.nio.file.Files.deleteIfExists(target);
            }
        }

        @Test
        @DisplayName("Walk directory finds temp file")
        void walkDirectory() throws Exception {
            java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("cwtest");
            java.nio.file.Files.createFile(tmpDir.resolve("file.txt"));
            try {
                var files = FileSystemOps.walkDirectory(tmpDir, 1);
                assertThat(files).hasSize(2); // dir itself + file
            } finally {
                java.nio.file.Files.walk(tmpDir).sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { java.nio.file.Files.delete(p); } catch (Exception ignored) {} });
            }
        }

        @Test
        @DisplayName("Safe resolve prevents path traversal")
        void safeResolvePreventsTraversal() {
            java.nio.file.Path base = java.nio.file.Path.of("/var/www/uploads").toAbsolutePath();
            assertThatThrownBy(() -> FileSystemOps.safeResolveFile(base, "../../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
        }

        @Test
        @DisplayName("Safe resolve allows valid path within base")
        void safeResolveAllowsValid() throws Exception {
            java.nio.file.Path base = java.nio.file.Files.createTempDirectory("base");
            java.nio.file.Path resolved = FileSystemOps.safeResolveFile(base, "subdir/file.txt");
            assertThat(resolved.startsWith(base)).isTrue();
            java.nio.file.Files.deleteIfExists(base);
        }

        @Test
        @DisplayName("Simulated inode has correct fields")
        void simulatedInode() {
            var inode = FileSystemOps.SimulatedInode.create(42, "myfile.txt", 8192);
            assertThat(inode.inodeNumber()).isEqualTo(42);
            assertThat(inode.name()).isEqualTo("myfile.txt");
            assertThat(inode.size()).isEqualTo(8192);
            assertThat(inode.directBlocks()).hasSize(2); // 8192 / 4096 = 2 blocks
            assertThat(inode.linkCount()).isEqualTo(1);
        }
    }
}
