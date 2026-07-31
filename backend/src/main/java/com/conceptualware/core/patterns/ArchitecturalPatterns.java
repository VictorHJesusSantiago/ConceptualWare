package com.conceptualware.core.patterns;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class ArchitecturalPatterns {

    public interface Command {}
    public interface Query<R> {}

    @FunctionalInterface
    public interface CommandHandler<C extends Command> {
        void handle(C command);
    }

    @FunctionalInterface
    public interface QueryHandler<Q extends Query<R>, R> {
        R handle(Q query);
    }

    public static class CommandBus {
        private final Map<Class<?>, CommandHandler<?>> handlers = new ConcurrentHashMap<>();

        public <C extends Command> void register(Class<C> type, CommandHandler<C> handler) {
            handlers.put(type, handler);
        }

        @SuppressWarnings("unchecked")
        public <C extends Command> void dispatch(C command) {
            CommandHandler<C> handler = (CommandHandler<C>) handlers.get(command.getClass());
            if (handler == null) throw new IllegalStateException("Nenhum handler registrado para " + command.getClass());
            handler.handle(command);
        }
    }

    public static class QueryBus {
        private final Map<Class<?>, QueryHandler<?, ?>> handlers = new ConcurrentHashMap<>();

        public <Q extends Query<R>, R> void register(Class<Q> type, QueryHandler<Q, R> handler) {
            handlers.put(type, handler);
        }

        @SuppressWarnings("unchecked")
        public <Q extends Query<R>, R> R dispatch(Q query) {
            QueryHandler<Q, R> handler = (QueryHandler<Q, R>) handlers.get(query.getClass());
            if (handler == null) throw new IllegalStateException("Nenhum handler registrado para " + query.getClass());
            return handler.handle(query);
        }
    }

    public record SagaStep<C>(String name, Function<C, Boolean> action, Function<C, Void> compensate) {}

    public enum SagaStatus { COMPLETED, COMPENSATED, FAILED_UNRECOVERABLE }

    public record SagaResult(SagaStatus status, List<String> executedSteps, String failedStep) {}

    public static class SagaOrchestrator<C> {
        private final List<SagaStep<C>> steps = new ArrayList<>();

        public SagaOrchestrator<C> addStep(SagaStep<C> step) {
            steps.add(step);
            return this;
        }

        public SagaResult execute(C context) {
            List<SagaStep<C>> executed = new ArrayList<>();
            for (SagaStep<C> step : steps) {
                boolean ok;
                try {
                    ok = step.action().apply(context);
                } catch (RuntimeException ex) {
                    ok = false;
                }
                if (!ok) {
                    compensate(executed, context);
                    return new SagaResult(SagaStatus.COMPENSATED,
                        executed.stream().map(SagaStep::name).toList(), step.name());
                }
                executed.add(step);
            }
            return new SagaResult(SagaStatus.COMPLETED,
                executed.stream().map(SagaStep::name).toList(), null);
        }

        private void compensate(List<SagaStep<C>> executed, C context) {
            for (int i = executed.size() - 1; i >= 0; i--) {
                try {
                    executed.get(i).compensate().apply(context);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    public record OutboxMessage(UUID id, String aggregateType, String aggregateId,
                                 String eventType, String payload, Instant occurredAt, boolean published) {}

    public static class OutboxTable {
        private static final int MAX_RETAINED_MESSAGES = 1_000;

        private final Map<UUID, OutboxMessage> rows = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<UUID> insertionOrder = new ConcurrentLinkedQueue<>();

        public UUID append(String aggregateType, String aggregateId, String eventType, String payload) {
            UUID id = UUID.randomUUID();
            rows.put(id, new OutboxMessage(id, aggregateType, aggregateId, eventType, payload, Instant.now(), false));
            insertionOrder.add(id);
            evictOldestBeyondCap();
            return id;
        }

        private void evictOldestBeyondCap() {
            while (insertionOrder.size() > MAX_RETAINED_MESSAGES) {
                UUID oldest = insertionOrder.poll();
                if (oldest == null) break;
                rows.remove(oldest);
            }
        }

        public List<OutboxMessage> pendingMessages() {
            return insertionOrder.stream()
                .map(rows::get)
                .filter(m -> m != null && !m.published())
                .toList();
        }

        public void markPublished(UUID id) {
            rows.computeIfPresent(id, (k, m) -> new OutboxMessage(
                m.id(), m.aggregateType(), m.aggregateId(), m.eventType(), m.payload(), m.occurredAt(), true));
        }

        public int pendingCount() {
            return (int) rows.values().stream().filter(m -> !m.published()).count();
        }
    }
}
