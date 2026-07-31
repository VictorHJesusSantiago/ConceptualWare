package com.conceptualware.domain.progress;

import com.conceptualware.domain.shared.DomainEvent;

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
