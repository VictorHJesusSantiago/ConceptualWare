package com.conceptualware.infrastructure.messaging;

import com.conceptualware.domain.shared.AggregateRoot;
import com.conceptualware.domain.shared.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.List;

/**
 * Concept #12 — Event-Driven Architecture, Event Sourcing, Outbox Pattern
 * Concept #18 — Programação Assíncrona: @Async, event loop
 * Concept #13 — Observer / Pub-Sub Pattern
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publish all domain events from an aggregate.
     * Events are published AFTER transaction commits (Outbox Pattern — Concept #12).
     */
    public void publishEvents(AggregateRoot aggregate) {
        List<DomainEvent> events = aggregate.pullDomainEvents();
        events.forEach(event -> {
            log.info("Publishing domain event: type={} id={}", event.getEventType(), event.getEventId());
            applicationEventPublisher.publishEvent(event);
        });
    }

    /** Async event listener — decouples event handling from command processing. */
    @Async("virtualThreadExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDomainEvent(DomainEvent event) {
        log.debug("Handling domain event async: type={}", event.getEventType());
        // Route to appropriate handler based on event type
        switch (event.getEventType()) {
            case "user.registered"   -> log.info("New user registered — sending welcome email");
            case "challenge.completed" -> log.info("Challenge completed — updating leaderboard");
            case "user.points.earned" -> log.info("Points earned — checking achievements");
            default -> log.debug("Unhandled event type: {}", event.getEventType());
        }
    }
}
