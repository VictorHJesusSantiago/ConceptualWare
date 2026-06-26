package com.conceptualware.domain.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * Concept #12 — DDD: Evento de Domínio — imutável, carrega o que aconteceu
 * Concept #7  — OOP: Record (Java 16+), sealed interface (Java 17+)
 * Concept #12 — Event-Driven Architecture, Event Sourcing
 */
public abstract class DomainEvent {

    private final String eventId = UUID.randomUUID().toString();
    private final Instant occurredAt = Instant.now();
    private final String eventType;

    protected DomainEvent(String eventType) { this.eventType = eventType; }

    public String getEventId()      { return eventId; }
    public Instant getOccurredAt()  { return occurredAt; }
    public String getEventType()    { return eventType; }
}
