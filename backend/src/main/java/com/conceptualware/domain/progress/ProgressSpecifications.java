package com.conceptualware.domain.progress;

import com.conceptualware.domain.shared.Specification;

/**
 * Concept #12 — DDD: Specifications concretas para o bounded context de
 * progresso. Compostas via and/or/not (ver {@link Specification}).
 */
public final class ProgressSpecifications {

    private ProgressSpecifications() {}

    public static Specification<ConceptProgress> isComplete(int totalConcepts) {
        return progress -> progress.getCompletedConcepts().size() >= totalConcepts;
    }

    public static Specification<ConceptProgress> hasCompletedConcept(String slug) {
        return progress -> progress.hasCompleted(slug);
    }

    public static Specification<ConceptProgress> hasCompletedAtLeast(int minCount) {
        return progress -> progress.getCompletedConcepts().size() >= minCount;
    }

    public static Specification<ConceptProgress> isEligibleForCertificate(int totalConcepts, int minRequired) {
        return isComplete(totalConcepts).or(hasCompletedAtLeast(minRequired));
    }
}
