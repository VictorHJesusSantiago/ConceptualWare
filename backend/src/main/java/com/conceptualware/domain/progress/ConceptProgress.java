package com.conceptualware.domain.progress;

import com.conceptualware.domain.shared.AggregateRoot;
import com.conceptualware.domain.shared.Percentage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Concept #12 — DDD: Novo Aggregate Root — "ConceptProgress" (bounded context
 * de progresso do usuário através dos 800+ conceitos do catálogo).
 *
 *   Invariante de consistência: um conceito só pode ser marcado como
 *   concluído uma vez por usuário; tentativas subsequentes incrementam
 *   attemptCount mas não duplicam o registro de conclusão.
 *
 *   Event Sourcing simplificado: além do estado atual (completedConcepts),
 *   mantemos um log append-only de "ProgressEntry" no próprio documento —
 *   suficiente para reconstituir o histórico sem precisar de um event store
 *   dedicado. Para volume alto, migrar para uma coleção `progress_events`
 *   separada replayed via `pullDomainEvents()`.
 */
@Document(collection = "concept_progress")
@Getter
@NoArgsConstructor
public class ConceptProgress extends AggregateRoot {

    public record ProgressEntry(String conceptSlug, int attemptNumber, boolean completed, Instant recordedAt) {}

    private String userId;
    private final Set<String> completedConcepts = new LinkedHashSet<>();
    private final List<ProgressEntry> history = new ArrayList<>();
    private int totalConceptsInCatalog;

    private ConceptProgress(String userId, int totalConceptsInCatalog) {
        this.userId = userId;
        this.totalConceptsInCatalog = totalConceptsInCatalog;
    }

    public static ConceptProgress start(String userId, int totalConceptsInCatalog) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId é obrigatório");
        }
        return new ConceptProgress(userId, totalConceptsInCatalog);
    }

    /** Getter explícito com wrapper imutável — nunca expor coleção mutável via @Getter cru. */
    public Set<String> getCompletedConcepts() {
        return Collections.unmodifiableSet(completedConcepts);
    }

    public List<ProgressEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** Registra a conclusão de um conceito. Idempotente — repetir não duplica nem regride. */
    public void recordConceptCompleted(String conceptSlug) {
        if (conceptSlug == null || conceptSlug.isBlank()) {
            throw new IllegalArgumentException("conceptSlug é obrigatório");
        }
        int attemptNumber = (int) history.stream()
            .filter(e -> e.conceptSlug().equals(conceptSlug))
            .count() + 1;

        boolean alreadyCompleted = completedConcepts.contains(conceptSlug);
        history.add(new ProgressEntry(conceptSlug, attemptNumber, !alreadyCompleted, Instant.now()));

        if (!alreadyCompleted) {
            completedConcepts.add(conceptSlug);
            registerEvent(new ConceptProgressEvent(userId, conceptSlug, attemptNumber));
        }
    }

    public Percentage completionPercentage() {
        return Percentage.of(completedConcepts.size(), totalConceptsInCatalog);
    }

    public boolean hasCompleted(String conceptSlug) {
        return completedConcepts.contains(conceptSlug);
    }
}
