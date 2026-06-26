package com.conceptualware.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// Concept 17 & 18 — Concurrency: Thread Pool, Virtual Threads, Async execution
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // Virtual Thread executor — Project Loom (Java 21) — Concept 17
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    // Platform Thread pool for CPU-bound algorithm tasks — Concept 17
    @Bean(name = "algorithmExecutor")
    public ThreadPoolTaskExecutor algorithmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("algo-worker-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // Default async executor uses Virtual Threads (Concept 18)
    @Override
    public Executor getAsyncExecutor() {
        return virtualThreadExecutor();
    }
}
