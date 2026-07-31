package com.conceptualware.core.os;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.*;
import java.lang.management.*;

@Component
public class UnixSignalHandler {

    private static final Logger log = LoggerFactory.getLogger(UnixSignalHandler.class);

    private final AtomicBoolean shuttingDown    = new AtomicBoolean(false);
    private final AtomicLong    activeRequests  = new AtomicLong(0);
    private final List<Consumer<ShutdownEvent>> shutdownListeners = new ArrayList<>();

    public record ShutdownEvent(String signal, long activeRequests, String reason) {}

    public UnixSignalHandler() {
        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("SIGTERM/SIGINT received — initiating graceful shutdown");
            gracefulShutdown("JVM_SHUTDOWN_HOOK");
        }, "shutdown-hook"));
    }

    @PreDestroy
    public void onApplicationShutdown() {
        log.info("Spring context closing — @PreDestroy called");
        gracefulShutdown("SPRING_CONTEXT_CLOSE");
    }

    private void gracefulShutdown(String source) {
        if (!shuttingDown.compareAndSet(false, true)) return;

        ShutdownEvent event = new ShutdownEvent(source, activeRequests.get(),
            "Graceful shutdown initiated by " + source);

        shutdownListeners.forEach(l -> {
            try { l.accept(event); }
            catch (Exception e) { log.warn("Shutdown listener error", e); }
        });

        long deadline = System.currentTimeMillis() + 30_000;
        while (activeRequests.get() > 0 && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        if (activeRequests.get() > 0) {
            log.warn("Shutdown timeout — {} requests still active", activeRequests.get());
        } else {
            log.info("All requests drained — shutdown complete");
        }
    }

    public void addShutdownListener(Consumer<ShutdownEvent> listener) {
        shutdownListeners.add(listener);
    }

    public boolean isShuttingDown() { return shuttingDown.get(); }

    public void trackRequestStart() { activeRequests.incrementAndGet(); }
    public void trackRequestEnd()   { activeRequests.decrementAndGet(); }

    public static ProcessInfo currentProcessInfo() {
        ProcessHandle self = ProcessHandle.current();
        ProcessHandle.Info info = self.info();

        return new ProcessInfo(
            self.pid(),
            info.command().orElse("unknown"),
            info.commandLine().orElse("unknown"),
            info.user().orElse("unknown"),
            info.startInstant().orElse(null),
            info.totalCpuDuration().map(d -> d.toMillis()).orElse(0L)
        );
    }

    public record ProcessInfo(
        long   pid,
        String command,
        String commandLine,
        String user,
        java.time.Instant startTime,
        long   cpuTimeMillis
    ) {}

    public static List<ProcessInfo> childProcesses() {
        return ProcessHandle.current().children()
            .map(h -> {
                var info = h.info();
                return new ProcessInfo(h.pid(),
                    info.command().orElse("?"),
                    info.commandLine().orElse("?"),
                    info.user().orElse("?"),
                    info.startInstant().orElse(null),
                    info.totalCpuDuration().map(d -> d.toMillis()).orElse(0L));
            })
            .toList();
    }

    public static JvmStats getJvmStats() {
        Runtime rt  = Runtime.getRuntime();
        var memBean = ManagementFactory.getMemoryMXBean();
        var gc      = ManagementFactory.getGarbageCollectorMXBeans();

        long gcTime = gc.stream().mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionTime).sum();
        long gcCount = gc.stream().mapToLong(java.lang.management.GarbageCollectorMXBean::getCollectionCount).sum();

        return new JvmStats(
            ProcessHandle.current().pid(),
            rt.availableProcessors(),
            rt.totalMemory(),
            rt.freeMemory(),
            rt.maxMemory(),
            memBean.getHeapMemoryUsage().getUsed(),
            memBean.getNonHeapMemoryUsage().getUsed(),
            ManagementFactory.getThreadMXBean().getThreadCount(),
            gcCount,
            gcTime
        );
    }

    public record JvmStats(
        long pid,
        int  cpus,
        long totalMemory,
        long freeMemory,
        long maxMemory,
        long heapUsed,
        long nonHeapUsed,
        int  threadCount,
        long gcCount,
        long gcTimeMs
    ) {}

    public record CgroupLimits(
        long  cpuQuotaUs,
        long  cpuPeriodUs,
        long  memoryLimitBytes,
        long  memoryCurrentBytes
    ) {
        public static CgroupLimits readFromSystem() {
            long cpuQuota  = readLongFile("/sys/fs/cgroup/cpu.max",     -1);
            long cpuPeriod = 100_000L;
            long memLimit  = readLongFile("/sys/fs/cgroup/memory.max",  -1);
            long memUsed   = readLongFile("/sys/fs/cgroup/memory.current", 0);
            return new CgroupLimits(cpuQuota, cpuPeriod, memLimit, memUsed);
        }

        private static long readLongFile(String path, long defaultVal) {
            try {
                String content = java.nio.file.Files.readString(java.nio.file.Path.of(path)).trim();
                if ("max".equals(content)) return Long.MAX_VALUE;
                return Long.parseLong(content.split("\\s+")[0]);
            } catch (Exception ignored) { return defaultVal; }
        }

        public double cpuLimitCores() {
            return cpuQuotaUs < 0 ? -1 : (double) cpuQuotaUs / cpuPeriodUs;
        }
    }

    public record NamespaceInfo(String type, String description, String dockerExample) {
        public static NamespaceInfo[] all() {
            return new NamespaceInfo[]{
                new NamespaceInfo("pid",     "Process ID isolation",        "--pid host"),
                new NamespaceInfo("net",     "Network isolation",           "--network none"),
                new NamespaceInfo("mnt",     "Filesystem mount isolation",  "--volume /host:/container"),
                new NamespaceInfo("uts",     "Hostname/domain isolation",   "--hostname mycontainer"),
                new NamespaceInfo("ipc",     "IPC resource isolation",      "--ipc shareable"),
                new NamespaceInfo("user",    "User/group ID mapping",       "--user 1000:1000"),
                new NamespaceInfo("cgroup",  "Cgroup root isolation",       "automatic in Docker"),
            };
        }
    }
}
