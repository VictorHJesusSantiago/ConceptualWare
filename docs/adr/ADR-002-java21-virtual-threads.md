# ADR-002: Java 21 Virtual Threads for Algorithm Execution

**Date:** 2024-01-20
**Status:** ACCEPTED
**Deciders:** Platform Team
**Technical Domain:** Concurrency Model

---

## Context and Problem Statement

Algorithm executions are I/O-bound operations (MongoDB queries, Redis operations) mixed with
CPU-bound operations (sorting 10k elements, graph traversal). With traditional thread-per-request
model and Spring MVC, each concurrent execution consumes a platform thread (OS thread, ~1MB stack).
Under 1000 concurrent users, the server would require ~1GB just for thread stacks.

## Decision Drivers

* Support 1000+ concurrent algorithm executions
* Minimize memory footprint
* Leverage Java 21 LTS features
* Keep programming model simple (no reactive/callback hell)

## Considered Options

**Option A: Traditional platform threads + thread pool**
- Limited to ~500 threads before memory pressure
- Thread pool sizing is operational burden

**Option B: Reactive programming (WebFlux + Project Reactor)**
- Supports millions of concurrent operations
- Steep learning curve, callback composition is hard to debug
- Cannot use blocking APIs without wrapping

**Option C: Java 21 Virtual Threads (Project Loom)**
- Scheduled on carrier (platform) threads, NOT OS threads
- Millions of virtual threads with tiny footprint (~1KB vs ~1MB)
- Blocking a virtual thread: unmounts from carrier (no OS thread blocked)
- Same imperative programming model — no API changes
- `spring.threads.virtual.enabled=true` in Spring Boot 3.2

## Decision

**Use Java 21 Virtual Threads (Option C)**

### Configuration:
```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true  # Tomcat + Spring MVC use virtual threads automatically
```

### Execution isolation:
```java
// Algorithm executions run in dedicated virtual thread with timeout
@PostMapping("/execute")
public ExecutionResult execute(@RequestBody ExecutionRequest req) {
    return Executors.newVirtualThreadPerTaskExecutor()
        .submit(() -> algorithmService.execute(req))
        .get(5, TimeUnit.SECONDS);  // 5s timeout per execution
}
```

## Consequences

**Positive:**
- Spring MVC handles 10,000+ concurrent requests on modest hardware
- No changes to service code — looks like synchronous programming
- Debugging with stack traces (not reactive chain) is straightforward
- MongoDB driver, Redis Lettuce are virtual-thread compatible (blocking I/O remounts)

**Negative:**
- CPU-bound tasks (sorting large arrays) still consume platform threads during computation
- Pinning: synchronized blocks pin virtual thread to carrier — avoid in hot paths
- Virtual threads don't help with CPU saturation (only with I/O-bound waits)

**Monitoring:**
- JFR (Java Flight Recorder): track virtual thread pin events
- Actuator endpoint: `/actuator/metrics/jvm.threads.live` shows virtual thread count

## References

- JEP 444: Virtual Threads
- Spring Boot 3.2 Virtual Threads support
- Project Loom: https://openjdk.org/projects/loom/
