package com.conceptualware.core.patterns;

import java.util.*;
import java.util.concurrent.*;

/**
 * Concept #13 — Microkernel Pattern (POSA vol. 1)
 *
 * Intent:
 *   Separate a minimal functional core from extended functionality and
 *   customer-specific parts. The microkernel serves as a socket for
 *   plugging in extensions and coordinating their collaboration.
 *
 * Structure:
 *   ┌──────────────────────────────────────────────────────────┐
 *   │  External Policy (adapter/client)                        │
 *   ├──────────────────────────────────────────────────────────┤
 *   │  Internal Policy (plug-in / extension)                   │
 *   ├──────────────────────────────────────────────────────────┤
 *   │  Microkernel (core services: IPC, lifecycle, registry)   │
 *   └──────────────────────────────────────────────────────────┘
 *
 * Components:
 *   Microkernel:        minimal services (plugin registry, event bus, IPC)
 *   Internal Server:    plug-in that runs inside the microkernel process
 *   External Server:    plug-in that runs in a separate process/service
 *   Adapter:            translates external requests to internal server calls
 *   Client:             uses adapters to access internal servers
 *
 * Real-world examples:
 *   - Eclipse IDE: core + feature plugins (OSGi bundles)
 *   - VS Code: core + extensions
 *   - Linux kernel (monolithic) vs Mach/GNU Hurd (true microkernel)
 *   - Spring Framework: ApplicationContext + BeanDefinition plugins
 *   - ConceptualWare: this very platform — core services + concept plugins
 *
 * Key difference from Plugin Pattern:
 *   Plugin:     runtime extension of behavior
 *   Microkernel: minimal core + all major features are plugins, including core ones
 *
 * Difference from Service Locator:
 *   Service Locator: anti-pattern (hides dependencies)
 *   Microkernel:     explicit plug-in lifecycle with dependency declarations
 */
public class MicrokernelPattern {

    // ── Plugin contract ────────────────────────────────────────────────────────

    /**
     * Plugin: the unit of extension in a microkernel system.
     * Lifecycle: REGISTERED → STARTED → STOPPED → UNREGISTERED
     */
    public interface Plugin {
        String id();                          // unique plugin identifier
        String[] dependencies();              // plugins this one requires to start first
        void start(PluginContext ctx);         // called when plugin is activated
        void stop();                          // called when plugin is deactivated
        default String version() { return "1.0.0"; }
    }

    /**
     * PluginContext: what the microkernel exposes to each plugin.
     * Plugins communicate ONLY through the context — never directly with each other.
     * This enforces decoupling (plugins are unaware of each other's implementation).
     */
    public interface PluginContext {
        /** Publish an event on the internal bus. */
        void publish(String topic, Object payload);

        /** Subscribe to events from the internal bus. */
        void subscribe(String topic, EventHandler handler);

        /** Look up another plugin's service by interface. */
        <T> Optional<T> getService(Class<T> serviceType);

        /** Register this plugin's service for others to use. */
        <T> void registerService(Class<T> type, T implementation);

        /** Schedule recurring work (ms interval). */
        ScheduledFuture<?> schedule(Runnable task, long intervalMs);
    }

    @FunctionalInterface
    public interface EventHandler { void onEvent(String topic, Object payload); }

    // ── Microkernel implementation ─────────────────────────────────────────────

    /**
     * ConceptualWareKernel: the minimal core.
     *
     * Core services:
     *   - Plugin registry + dependency-ordered lifecycle management
     *   - Synchronous event bus (pub/sub)
     *   - Service registry (type-keyed instances)
     *   - Task scheduler
     */
    public static class ConceptualWareKernel {
        private final Map<String, Plugin>           plugins     = new LinkedHashMap<>();
        private final Map<String, List<EventHandler>> eventBus  = new ConcurrentHashMap<>();
        private final Map<Class<?>, Object>          services   = new ConcurrentHashMap<>();
        private final ScheduledExecutorService       scheduler  =
            Executors.newScheduledThreadPool(2);

        // ── Plugin lifecycle ────────────────────────────────────────────────────

        /**
         * Register a plugin. Plugins are NOT started at registration time —
         * call start() explicitly after all plugins are registered.
         */
        public void register(Plugin plugin) {
            if (plugins.containsKey(plugin.id())) {
                throw new IllegalStateException("Plugin already registered: " + plugin.id());
            }
            plugins.put(plugin.id(), plugin);
        }

        /**
         * Start all registered plugins in dependency order (topological sort).
         * If A depends on B, B starts before A.
         */
        public void startAll() {
            List<String> order = topologicalSort();
            for (String id : order) {
                Plugin p = plugins.get(id);
                if (p == null) throw new IllegalStateException("Unregistered dependency: " + id);
                p.start(contextFor(p));
                System.out.printf("[kernel] started plugin: %s v%s%n", p.id(), p.version());
            }
        }

        /** Stop all plugins in REVERSE startup order (dependencies last). */
        public void stopAll() {
            List<String> order = topologicalSort();
            Collections.reverse(order);
            for (String id : order) {
                Plugin p = plugins.get(id);
                if (p != null) {
                    p.stop();
                    System.out.printf("[kernel] stopped plugin: %s%n", p.id());
                }
            }
            scheduler.shutdownNow();
        }

        /** Kahn's algorithm for topological sort of plugin dependencies. */
        private List<String> topologicalSort() {
            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, List<String>> adj = new HashMap<>();

            for (Plugin p : plugins.values()) {
                inDegree.putIfAbsent(p.id(), 0);
                for (String dep : p.dependencies()) {
                    adj.computeIfAbsent(dep, k -> new ArrayList<>()).add(p.id());
                    inDegree.merge(p.id(), 1, Integer::sum);
                }
            }

            Queue<String> queue = new LinkedList<>();
            inDegree.forEach((id, deg) -> { if (deg == 0) queue.add(id); });

            List<String> order = new ArrayList<>();
            while (!queue.isEmpty()) {
                String id = queue.poll();
                order.add(id);
                adj.getOrDefault(id, List.of()).forEach(dep -> {
                    if (inDegree.merge(dep, -1, Integer::sum) == 0) queue.add(dep);
                });
            }

            if (order.size() != plugins.size()) {
                throw new IllegalStateException("Circular plugin dependency detected");
            }
            return order;
        }

        /** Create a PluginContext scoped to a specific plugin. */
        private PluginContext contextFor(Plugin plugin) {
            return new PluginContext() {
                @Override
                public void publish(String topic, Object payload) {
                    eventBus.getOrDefault(topic, List.of())
                        .forEach(h -> h.onEvent(topic, payload));
                }

                @Override
                public void subscribe(String topic, EventHandler handler) {
                    eventBus.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                        .add(handler);
                }

                @Override
                public <T> Optional<T> getService(Class<T> type) {
                    return Optional.ofNullable(type.cast(services.get(type)));
                }

                @Override
                public <T> void registerService(Class<T> type, T impl) {
                    services.put(type, impl);
                }

                @Override
                public ScheduledFuture<?> schedule(Runnable task, long intervalMs) {
                    return scheduler.scheduleAtFixedRate(
                        task, intervalMs, intervalMs, TimeUnit.MILLISECONDS
                    );
                }
            };
        }

        public int pluginCount() { return plugins.size(); }
    }

    // ── Example plugins ────────────────────────────────────────────────────────

    /** Service interface registered by ExecutionPlugin. */
    public interface ConceptExecutor {
        String execute(String conceptId, String input);
    }

    /** Plugin: handles concept execution logic. */
    public static class ExecutionPlugin implements Plugin {
        @Override public String id()           { return "execution"; }
        @Override public String[] dependencies() { return new String[]{"security"}; }

        @Override
        public void start(PluginContext ctx) {
            // Register a service that other plugins can use
            ctx.registerService(ConceptExecutor.class,
                (conceptId, input) -> "executed-" + conceptId + "(" + input + ")");

            // Subscribe to concept-submitted events
            ctx.subscribe("concept.submitted", (topic, payload) ->
                System.out.println("[execution] received: " + payload));
        }

        @Override public void stop() { System.out.println("[execution] stopped"); }
    }

    /** Plugin: security checks (must start before execution). */
    public static class SecurityPlugin implements Plugin {
        @Override public String id()             { return "security"; }
        @Override public String[] dependencies() { return new String[0]; }

        @Override
        public void start(PluginContext ctx) {
            ctx.subscribe("concept.submitted", (topic, payload) ->
                System.out.println("[security] validating request: " + payload));
        }

        @Override public void stop() {}
    }

    /** Plugin: leaderboard (depends on execution for scores). */
    public static class LeaderboardPlugin implements Plugin {
        @Override public String id()             { return "leaderboard"; }
        @Override public String[] dependencies() { return new String[]{"execution"}; }

        private final Map<String, Integer> scores = new ConcurrentHashMap<>();

        @Override
        public void start(PluginContext ctx) {
            ctx.subscribe("execution.completed", (topic, payload) -> {
                String user = payload.toString();
                scores.merge(user, 1, Integer::sum);
                System.out.println("[leaderboard] " + user + " → " + scores.get(user) + " points");
            });
        }

        @Override public void stop() {}

        public Map<String, Integer> getScores() { return Collections.unmodifiableMap(scores); }
    }
}
