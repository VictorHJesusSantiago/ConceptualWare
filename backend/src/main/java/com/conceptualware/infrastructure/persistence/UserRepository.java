package com.conceptualware.infrastructure.persistence;

import com.conceptualware.domain.user.User;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query("{ 'roles': ?0, 'status': 'ACTIVE' }")
    List<User> findActiveByRole(String role);

    @Query("{ 'lastActiveAt': { $gte: ?0 }, 'status': 'ACTIVE' }")
    List<User> findRecentlyActive(Instant since);

    @Query("{ 'skillLevel': ?0 }")
    List<User> findBySkillLevel(String skillLevel);

    @Aggregation(pipeline = {
        "{ $match: { 'status': 'ACTIVE' } }",
        "{ $sort: { 'totalPoints': -1 } }",
        "{ $limit: ?0 }",
        "{ $project: { 'username': 1, 'totalPoints': 1, 'skillLevel': 1, 'challengesSolved': 1 } }"
    })
    List<User> findTopUsers(int limit);

    @Aggregation(pipeline = {
        "{ $group: { _id: '$skillLevel', count: { $sum: 1 }, avgPoints: { $avg: '$totalPoints' } } }",
        "{ $sort: { 'avgPoints': -1 } }"
    })
    List<Object> getSkillLevelStats();
}
