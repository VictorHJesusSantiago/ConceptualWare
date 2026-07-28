package com.conceptualware.application;

import com.conceptualware.domain.progress.ConceptProgress;
import com.conceptualware.domain.progress.ProgressSpecifications;
import com.conceptualware.infrastructure.messaging.DomainEventPublisher;
import com.conceptualware.infrastructure.persistence.ConceptProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concept #12 — DDD: Application Service — orquestra o agregado, garante
 * atomicidade (@Transactional) e publica eventos de domínio via Outbox
 * simplificado (ver DomainEventPublisher).
 */
@Service
@RequiredArgsConstructor
public class ConceptProgressApplicationService {

    private static final int TOTAL_CONCEPTS_IN_CATALOG = 800;
    private static final int MIN_CONCEPTS_FOR_CERTIFICATE = 640; // 80% do catálogo

    private final ConceptProgressRepository repository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public ConceptProgress recordCompletion(String userId, String conceptSlug) {
        ConceptProgress progress = repository.findByUserId(userId)
            .orElseGet(() -> ConceptProgress.start(userId, TOTAL_CONCEPTS_IN_CATALOG));

        progress.recordConceptCompleted(conceptSlug);
        ConceptProgress saved = repository.save(progress);
        eventPublisher.publishEvents(saved);
        return saved;
    }

    public ConceptProgress findOrCreate(String userId) {
        return repository.findByUserId(userId)
            .orElseGet(() -> ConceptProgress.start(userId, TOTAL_CONCEPTS_IN_CATALOG));
    }

    public boolean isEligibleForCertificate(String userId) {
        ConceptProgress progress = findOrCreate(userId);
        return ProgressSpecifications
            .isEligibleForCertificate(TOTAL_CONCEPTS_IN_CATALOG, MIN_CONCEPTS_FOR_CERTIFICATE)
            .isSatisfiedBy(progress);
    }
}
