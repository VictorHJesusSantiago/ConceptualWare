package com.conceptualware.domain.progress;

import com.conceptualware.domain.shared.DomainEvent;

/**
 * Concept #12 — DDD: Evento de domínio do bounded context "progress".
 * DomainEvent é classe abstrata (não interface/record) — ver CLAUDE.md:
 * eventos NÃO podem ser `record X(...) extends DomainEvent` (records não
 * podem estender classes em Java).
 */
public final class ConceptProgressEvent extends DomainEvent {

    private final String userId;
    private final String conceptSlug;
    private final int attemptNumber;

    public ConceptProgressEvent(String userId, String conceptSlug, int attemptNumber) {
        super("concept.completed");
        this.userId = userId;
        this.conceptSlug = conceptSlug;
        this.attemptNumber = attemptNumber;
    }

    public String getUserId()       { return userId; }
    public String getConceptSlug()  { return conceptSlug; }
    public int getAttemptNumber()   { return attemptNumber; }
}
