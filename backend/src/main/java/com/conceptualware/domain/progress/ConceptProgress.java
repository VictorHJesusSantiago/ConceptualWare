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

    public Set<String> getCompletedConcepts() {
        return Collections.unmodifiableSet(completedConcepts);
    }

    public List<ProgressEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

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
