package com.conceptualware.domain.shared;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Concept #7  — OOP: Classe abstrata, atributo estático, encapsulamento
 * Concept #12 — DDD: Agregado (Aggregate Root) — entidade raiz de consistência
 * Concept #14 — SOLID: SRP — base class handles identity, events, timestamps only
 */
public abstract class AggregateRoot {

    @Id
    protected String id;

    @CreatedDate
    protected Instant createdAt;

    @LastModifiedDate
    protected Instant updatedAt;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // Domain events (Concept 12 — Event-Driven Architecture, Event Sourcing)
    protected void registerEvent(DomainEvent event) { domainEvents.add(event); }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    public String getId()         { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregateRoot other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return id != null ? id.hashCode() : super.hashCode(); }
}
