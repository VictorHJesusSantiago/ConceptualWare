package com.conceptualware.infrastructure.messaging;

import com.conceptualware.domain.shared.AggregateRoot;
import com.conceptualware.domain.shared.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.List;

/**
 * Concept #12 — Event-Driven Architecture, Event Sourcing, Outbox Pattern
 * Concept #18 — Programação Assíncrona: @Async, event loop
 * Concept #13 — Observer / Pub-Sub Pattern
 *
 * Outbox Pattern — observação importante sobre MongoDB:
 *
 *   Spring Data MongoDB NÃO participa do PlatformTransactionManager JPA por
 *   padrão. O @TransactionalEventListener(AFTER_COMMIT) só funciona de forma
 *   garantida quando há uma transação Spring ativa (ex: @Transactional com JPA
 *   ou com MongoTransactionManager explícito e replica set).
 *
 *   Estratégia implementada aqui (Outbox Pattern simplificado para MongoDB):
 *   1. publishEvents() é chamado DENTRO do método de aplicação marcado com
 *      @Transactional (ver ApplicationService).
 *   2. O evento é publicado via ApplicationEventPublisher imediatamente.
 *   3. handleDomainEvent é @Async — executa em virtual thread, desacoplado do
 *      fluxo de escrita.
 *
 *   Target state (Outbox real):
 *   - Persistir evento em coleção `outbox_events` na mesma operação de escrita
 *     do agregado (atomicidade garantida pelo MongoDB multi-document transaction
 *     com replica set habilitado).
 *   - Um relay job (scheduled ou change stream) publica os eventos e marca como
 *     processados. Ver ADR pendente para decisão de implementação.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publica todos os eventos de domínio de um agregado.
     *
     * IMPORTANTE: este método deve ser chamado dentro de um contexto transacional
     * (@Transactional no Application Service) para que o @TransactionalEventListener
     * funcione corretamente. Sem transação ativa, Spring pubblica imediatamente.
     */
    @Transactional
    public void publishEvents(AggregateRoot aggregate) {
        List<DomainEvent> events = aggregate.pullDomainEvents();
        events.forEach(event -> {
            log.info("Publishing domain event: type={} eventId={}", event.getEventType(), event.getEventId());
            applicationEventPublisher.publishEvent(event);
        });
    }

    /**
     * Listener assíncrono de eventos de domínio.
     *
     * - @TransactionalEventListener(AFTER_COMMIT): só executa após commit da
     *   transação Spring. Se não há transação ativa, Spring usa AFTER_COMPLETION
     *   como fallback (fallbackExecution = true).
     * - @Async("virtualThreadExecutor"): executa em virtual thread (Project Loom),
     *   não bloqueia o thread do request.
     */
    @Async("virtualThreadExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleDomainEvent(DomainEvent event) {
        log.debug("Handling domain event async: type={} eventId={}", event.getEventType(), event.getEventId());
        switch (event.getEventType()) {
            case "user.registered"    -> log.info("New user registered — queuing welcome email");
            case "user.email.verified"-> log.info("Email verified — unlocking premium features check");
            case "challenge.completed"-> log.info("Challenge completed — updating leaderboard async");
            case "user.points.earned" -> log.info("Points earned — checking achievements async");
            case "concept.completed"  -> log.info("Concept completed — updating progress async");
            case "user.role.changed"  -> log.info("Role changed — invalidating permission cache");
            default -> log.debug("Unhandled event type: {}", event.getEventType());
        }
    }
}
