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

@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void publishEvents(AggregateRoot aggregate) {
        List<DomainEvent> events = aggregate.pullDomainEvents();
        events.forEach(event -> {
            log.info("Publishing domain event: type={} eventId={}", event.getEventType(), event.getEventId());
            applicationEventPublisher.publishEvent(event);
        });
    }

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
