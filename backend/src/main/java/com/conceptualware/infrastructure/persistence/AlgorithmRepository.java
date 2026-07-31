package com.conceptualware.infrastructure.persistence;

import com.conceptualware.domain.algorithm.Algorithm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlgorithmRepository extends MongoRepository<Algorithm, String> {

    Optional<Algorithm> findBySlug(String slug);
    boolean existsBySlug(String slug);

    Page<Algorithm> findByCategory(Algorithm.Category category, Pageable pageable);

    Page<Algorithm> findByDifficulty(Algorithm.Difficulty difficulty, Pageable pageable);

    @Query("{ 'tags': { $in: ?0 } }")
    Page<Algorithm> findByTagsIn(List<String> tags, Pageable pageable);

    @Query("{ $text: { $search: ?0 } }")
    Page<Algorithm> fullTextSearch(String searchTerm, Pageable pageable);

    @Query("{ 'viewCount': { $gte: ?0 } }")
    List<Algorithm> findPopular(int minViews, Pageable pageable);

    @Query("{ 'category': ?0, 'difficulty': ?1, 'isStable': ?2 }")
    List<Algorithm> findByCategoryAndDifficultyAndStable(
        Algorithm.Category category, Algorithm.Difficulty difficulty, boolean stable);

    @Query("{ 'slug': { $nin: ?0 } }")
    List<Algorithm> findNotInSlugs(List<String> viewedSlugs, Pageable pageable);

    @Query("{ 'averageRating': { $gte: ?0 } }")
    List<Algorithm> findHighlyRated(double minRating);
}
