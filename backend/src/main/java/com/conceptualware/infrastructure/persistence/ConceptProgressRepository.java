package com.conceptualware.infrastructure.persistence;

import com.conceptualware.domain.progress.ConceptProgress;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ConceptProgressRepository extends MongoRepository<ConceptProgress, String> {
    Optional<ConceptProgress> findByUserId(String userId);
}
