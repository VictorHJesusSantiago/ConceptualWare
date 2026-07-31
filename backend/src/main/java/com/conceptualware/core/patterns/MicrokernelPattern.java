package com.conceptualware.core.patterns;

import java.util.*;
import java.util.concurrent.*;

public class MicrokernelPattern {

    public interface Plugin {
        String id();
        String[] dependencies();
        void start(PluginContext ctx);
        void stop();
        default String version() { return "1.0.0"; }
    }

    public interface PluginContext {
        void publish(String topic, Object payload);

        void subscribe(String topic, EventHandler handler);

        <T> Optional<T> getService(Class<T> serviceType);

        <T> void registerService(Class<T> type, T implementation);

        ScheduledFuture<?> schedule(Runnable task, long intervalMs);
    }

    @FunctionalInterface
    public interface EventHandler { void onEvent(String topic, Object payload); }

    public static class ConceptualWareKernel {
        private final Map<String, Plugin>           plugins     = new LinkedHashMap<>();
        private final Map<String, List<EventHandler>> eventBus  = new ConcurrentHashMap<>();
        private final Map<Class<?>, Object>          services   = new ConcurrentHashMap<>();
        private final ScheduledExecutorService       scheduler  =
            Executors.newScheduledThreadPool(2);

        public void register(Plugin plugin) {
            if (plugins.containsKey(plugin.id())) {
                throw new IllegalStateException("Plugin already registered: " + plugin.id());
            }
            plugins.put(plugin.id(), plugin);
        }

        public void startAll() {
            List<String> order = topologicalSort();
            for (String id : order) {
                Plugin p = plugins.get(id);
                if (p == null) throw new IllegalStateException("Unregistered dependency: " + id);
                p.start(contextFor(p));
                System.out.printf("[kernel] started plugin: %s v%s%n", p.id(), p.version());
            }
        }

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

    public interface ConceptExecutor {
        String execute(String conceptId, String input);
    }

    public static class ExecutionPlugin implements Plugin {
        @Override public String id()           { return "execution"; }
        @Override public String[] dependencies() { return new String[]{"security"}; }

        @Override
        public void start(PluginContext ctx) {
            ctx.registerService(ConceptExecutor.class,
                (conceptId, input) -> "executed-" + conceptId + "(" + input + ")");

            ctx.subscribe("concept.submitted", (topic, payload) ->
                System.out.println("[execution] received: " + payload));
        }

        @Override public void stop() { System.out.println("[execution] stopped"); }
    }

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
