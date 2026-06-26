package com.conceptualware.infrastructure.persistence;

import com.conceptualware.domain.algorithm.Algorithm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.TextQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Concept #11 — MongoDB: Full-text search, Pagination, Sorting,
 *   CRUD operations, Compound queries, Regex
 * Concept #25 — API: Paginação cursor-based e offset (via Pageable)
 * Concept #26 — Performance: Paginação para redução de payload
 */
@Repository
public interface AlgorithmRepository extends MongoRepository<Algorithm, String> {

    Optional<Algorithm> findBySlug(String slug);
    boolean existsBySlug(String slug);

    // Category filter with pagination — Concept #11 & #26
    Page<Algorithm> findByCategory(Algorithm.Category category, Pageable pageable);

    // Difficulty filter
    Page<Algorithm> findByDifficulty(Algorithm.Difficulty difficulty, Pageable pageable);

    // Tags search (MongoDB $in operator)
    @Query("{ 'tags': { $in: ?0 } }")
    Page<Algorithm> findByTagsIn(List<String> tags, Pageable pageable);

    // Full-text search — MongoDB Text Index (Concept #11)
    @Query("{ $text: { $search: ?0 } }")
    Page<Algorithm> fullTextSearch(String searchTerm, Pageable pageable);

    // Popular algorithms
    @Query("{ 'viewCount': { $gte: ?0 } }")
    List<Algorithm> findPopular(int minViews, Pageable pageable);

    // Multi-field compound query
    @Query("{ 'category': ?0, 'difficulty': ?1, 'isStable': ?2 }")
    List<Algorithm> findByCategoryAndDifficultyAndStable(
        Algorithm.Category category, Algorithm.Difficulty difficulty, boolean stable);

    // Algorithms user hasn't seen (for recommendations — Concept #30)
    @Query("{ 'slug': { $nin: ?0 } }")
    List<Algorithm> findNotInSlugs(List<String> viewedSlugs, Pageable pageable);

    // Aggregated rating
    @Query("{ 'averageRating': { $gte: ?0 } }")
    List<Algorithm> findHighlyRated(double minRating);
}
