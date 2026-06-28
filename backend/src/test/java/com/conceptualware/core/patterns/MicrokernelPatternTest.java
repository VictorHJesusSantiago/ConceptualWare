package com.conceptualware.core.patterns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Microkernel Pattern")
class MicrokernelPatternTest {

    @Test
    @DisplayName("Kernel: plugins start in dependency order (security → execution → leaderboard)")
    void testPluginStartOrder() {
        List<String> startOrder = new ArrayList<>();

        MicrokernelPattern.ConceptualWareKernel kernel = new MicrokernelPattern.ConceptualWareKernel();

        // Wrapper plugins that record start order
        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "security"; }
            @Override public String[] dependencies() { return new String[0]; }
            @Override public void start(MicrokernelPattern.PluginContext ctx) { startOrder.add("security"); }
            @Override public void stop() {}
        });

        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "execution"; }
            @Override public String[] dependencies() { return new String[]{"security"}; }
            @Override public void start(MicrokernelPattern.PluginContext ctx) { startOrder.add("execution"); }
            @Override public void stop() {}
        });

        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "leaderboard"; }
            @Override public String[] dependencies() { return new String[]{"execution"}; }
            @Override public void start(MicrokernelPattern.PluginContext ctx) { startOrder.add("leaderboard"); }
            @Override public void stop() {}
        });

        kernel.startAll();

        assertEquals(List.of("security", "execution", "leaderboard"), startOrder,
            "Plugins must start in topological dependency order");

        kernel.stopAll();
    }

    @Test
    @DisplayName("Kernel: event bus delivers messages to all subscribers on a topic")
    void testEventBus() {
        MicrokernelPattern.ConceptualWareKernel kernel = new MicrokernelPattern.ConceptualWareKernel();

        AtomicInteger handler1Count = new AtomicInteger(0);
        AtomicInteger handler2Count = new AtomicInteger(0);
        List<Object> receivedPayloads = new ArrayList<>();

        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "pluginA"; }
            @Override public String[] dependencies() { return new String[0]; }

            @Override
            public void start(MicrokernelPattern.PluginContext ctx) {
                ctx.subscribe("test.event", (topic, payload) -> {
                    handler1Count.incrementAndGet();
                    receivedPayloads.add(payload);
                });
                ctx.subscribe("test.event", (topic, payload) -> handler2Count.incrementAndGet());
            }

            @Override public void stop() {}
        });

        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "pluginB"; }
            @Override public String[] dependencies() { return new String[0]; }

            @Override
            public void start(MicrokernelPattern.PluginContext ctx) {
                ctx.publish("test.event", "hello-from-B");
            }

            @Override public void stop() {}
        });

        kernel.startAll();

        assertEquals(1, handler1Count.get());
        assertEquals(1, handler2Count.get());
        assertEquals("hello-from-B", receivedPayloads.get(0));

        kernel.stopAll();
    }

    @Test
    @DisplayName("Kernel: service registry allows plugins to share services by type")
    void testServiceRegistry() {
        MicrokernelPattern.ConceptualWareKernel kernel = new MicrokernelPattern.ConceptualWareKernel();

        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "provider"; }
            @Override public String[] dependencies() { return new String[0]; }

            @Override
            public void start(MicrokernelPattern.PluginContext ctx) {
                ctx.registerService(Runnable.class, () -> {});  // register a Runnable service
            }

            @Override public void stop() {}
        });

        final Optional<Runnable>[] found = new Optional[1];
        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "consumer"; }
            @Override public String[] dependencies() { return new String[]{"provider"}; }

            @Override
            public void start(MicrokernelPattern.PluginContext ctx) {
                found[0] = ctx.getService(Runnable.class);
            }

            @Override public void stop() {}
        });

        kernel.startAll();

        assertTrue(found[0].isPresent(), "Consumer must find the service registered by provider");

        kernel.stopAll();
    }

    @Test
    @DisplayName("Kernel: circular dependency throws IllegalStateException")
    void testCircularDependencyDetected() {
        MicrokernelPattern.ConceptualWareKernel kernel = new MicrokernelPattern.ConceptualWareKernel();

        // A depends on B, B depends on A → cycle
        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "A"; }
            @Override public String[] dependencies() { return new String[]{"B"}; }
            @Override public void start(MicrokernelPattern.PluginContext ctx) {}
            @Override public void stop() {}
        });

        kernel.register(new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "B"; }
            @Override public String[] dependencies() { return new String[]{"A"}; }
            @Override public void start(MicrokernelPattern.PluginContext ctx) {}
            @Override public void stop() {}
        });

        assertThrows(IllegalStateException.class, kernel::startAll,
            "Circular dependency must be detected by topological sort");
    }

    @Test
    @DisplayName("Kernel: duplicate plugin registration throws IllegalStateException")
    void testDuplicateRegistrationThrows() {
        MicrokernelPattern.ConceptualWareKernel kernel = new MicrokernelPattern.ConceptualWareKernel();

        MicrokernelPattern.Plugin p = new MicrokernelPattern.Plugin() {
            @Override public String id()             { return "duplicate"; }
            @Override public String[] dependencies() { return new String[0]; }
            @Override public void start(MicrokernelPattern.PluginContext ctx) {}
            @Override public void stop() {}
        };

        kernel.register(p);
        assertThrows(IllegalStateException.class, () -> kernel.register(p));
    }

    @Test
    @DisplayName("Full plugin ecosystem: Security → Execution → Leaderboard")
    void testFullPluginEcosystem() {
        MicrokernelPattern.ConceptualWareKernel kernel = new MicrokernelPattern.ConceptualWareKernel();

        var leaderboard = new MicrokernelPattern.LeaderboardPlugin();
        kernel.register(new MicrokernelPattern.SecurityPlugin());
        kernel.register(new MicrokernelPattern.ExecutionPlugin());
        kernel.register(leaderboard);
        kernel.startAll();

        assertEquals(3, kernel.pluginCount());

        kernel.stopAll();
    }
}
