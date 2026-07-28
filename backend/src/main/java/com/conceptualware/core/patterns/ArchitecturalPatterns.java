package com.conceptualware.core.patterns;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Concept #12 — Padrões Arquiteturais (demos in-process, sem infraestrutura externa):
 *
 *   CQRS (Command Query Responsibility Segregation):
 *     Separa o modelo de escrita (Command) do modelo de leitura (Query),
 *     cada um com seu próprio pipeline. Aqui simplificado com dois barramentos
 *     in-memory — em produção, o lado de leitura normalmente usa uma projeção
 *     desnormalizada (read model) atualizada de forma assíncrona pelos eventos.
 *
 *   Saga (orquestração):
 *     Coordena uma transação de negócio distribuída em múltiplos passos locais,
 *     com compensação (rollback lógico) em caso de falha de qualquer passo —
 *     substitui transações distribuídas (2PC) por consistência eventual.
 *
 *   Outbox Pattern (tabela local):
 *     Garante atomicidade entre "persistir estado" e "publicar evento" sem
 *     transação distribuída: grava o evento na mesma transação/tabela do
 *     agregado, e um processo separado (relay) drena a tabela e publica.
 *     Ver também {@code infrastructure.messaging.DomainEventPublisher} para a
 *     versão real usada nos agregados MongoDB deste projeto.
 */
public class ArchitecturalPatterns {

    // ── CQRS ───────────────────────────────────────────────────────────────

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

    /** Barramento de comandos: um handler por tipo de comando, sem retorno (write model). */
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

    /** Barramento de queries: um handler por tipo de query, com retorno (read model). */
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

    // ── Saga (orquestração com compensação) ───────────────────────────────────

    /** Um passo da saga: ação direta + ação de compensação (rollback lógico). */
    public record SagaStep<C>(String name, Function<C, Boolean> action, Function<C, Void> compensate) {}

    public enum SagaStatus { COMPLETED, COMPENSATED, FAILED_UNRECOVERABLE }

    public record SagaResult(SagaStatus status, List<String> executedSteps, String failedStep) {}

    /**
     * Orquestrador de Saga: executa passos em sequência; se um passo falha,
     * compensa (na ordem inversa) todos os passos já executados com sucesso.
     */
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
                    // Best-effort: falha de compensação é logada em produção, não interrompe o rollback dos demais passos.
                }
            }
        }
    }

    // ── Outbox Pattern (tabela local simulada em memória) ─────────────────────

    public record OutboxMessage(UUID id, String aggregateType, String aggregateId,
                                 String eventType, String payload, Instant occurredAt, boolean published) {}

    /**
     * Simula a "tabela outbox": eventos são inseridos atomicamente junto ao
     * agregado (mesma transação lógica) e um relay separado os drena e publica,
     * marcando-os como publicados. Garante "at-least-once delivery" sem 2PC.
     */
    public static class OutboxTable {
        private final Map<UUID, OutboxMessage> rows = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<UUID> insertionOrder = new ConcurrentLinkedQueue<>();

        /** Passo 1: grava o evento — chamado na MESMA transação que persiste o agregado. */
        public UUID append(String aggregateType, String aggregateId, String eventType, String payload) {
            UUID id = UUID.randomUUID();
            rows.put(id, new OutboxMessage(id, aggregateType, aggregateId, eventType, payload, Instant.now(), false));
            insertionOrder.add(id);
            return id;
        }

        /** Passo 2: relay drena mensagens pendentes em ordem de inserção (FIFO). */
        public List<OutboxMessage> pendingMessages() {
            return insertionOrder.stream()
                .map(rows::get)
                .filter(m -> m != null && !m.published())
                .toList();
        }

        /** Passo 3: após publicação bem-sucedida no broker, marca como publicado (idempotente). */
        public void markPublished(UUID id) {
            rows.computeIfPresent(id, (k, m) -> new OutboxMessage(
                m.id(), m.aggregateType(), m.aggregateId(), m.eventType(), m.payload(), m.occurredAt(), true));
        }

        public int pendingCount() {
            return (int) rows.values().stream().filter(m -> !m.published()).count();
        }
    }
}
