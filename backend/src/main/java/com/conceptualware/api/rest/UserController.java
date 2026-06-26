package com.conceptualware.api.rest;

import com.conceptualware.infrastructure.persistence.UserRepository;
import com.conceptualware.infrastructure.observability.MetricsService;
import io.micrometer.core.annotation.Timed;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Concept #25 — REST: HATEOAS-ready resource design, path variables, query params
 * Concept #21 — Security: @PreAuthorize, authenticated endpoints
 * Concept #12 — Architecture: Controller in Ports layer (Hexagonal)
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final MetricsService metricsService;

    public UserController(UserRepository userRepository, MetricsService metricsService) {
        this.userRepository = userRepository;
        this.metricsService = metricsService;
    }

    /** GET /api/v1/users/me — current user profile */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Timed("api.users.me")
    public ResponseEntity<?> getMe(Principal principal) {
        return userRepository.findById(principal.getName())
            .map(user -> ResponseEntity.ok(Map.of(
                "id",               user.getId(),
                "username",         user.getUsername().value(),
                "email",            user.getEmail().value(),
                "roles",            user.getRoles(),
                "skillLevel",       user.getSkillLevel(),
                "totalPoints",      user.getTotalPoints(),
                "challengesSolved", user.getChallengesSolved(),
                "currentStreak",    user.getCurrentStreak(),
                "longestStreak",    user.getLongestStreak(),
                "createdAt",        user.getCreatedAt()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/v1/users/leaderboard — top users by points */
    @GetMapping("/leaderboard")
    @Timed("api.users.leaderboard")
    public ResponseEntity<?> getLeaderboard(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<?> leaderboard = userRepository.findTopByTotalPoints(PageRequest.of(0, safeLimit));
        return ResponseEntity.ok(Map.of("items", leaderboard, "limit", safeLimit));
    }

    /** GET /api/v1/users/progress — concept progress breakdown */
    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    @Timed("api.users.progress")
    public ResponseEntity<?> getProgress(Principal principal) {
        return userRepository.findById(principal.getName())
            .map(user -> {
                int completed = user.getCompletedConcepts().size();
                int total = 30;
                Map<String, Object> progress = Map.of(
                    "completedConcepts", completed,
                    "totalConcepts",     total,
                    "percentageByCategory", Map.of(
                        "Algorithms",     computePct(user.getCompletedConcepts(), "ALGO", 35),
                        "Data Structures",computePct(user.getCompletedConcepts(), "DS",   30),
                        "Math",           computePct(user.getCompletedConcepts(), "MATH", 25),
                        "Architecture",   computePct(user.getCompletedConcepts(), "ARCH", 28)
                    )
                );
                return ResponseEntity.ok(progress);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/v1/users/stats — aggregated skill level distribution (admin view) */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStats() {
        List<?> stats = userRepository.countBySkillLevel();
        return ResponseEntity.ok(Map.of("skillLevelDistribution", stats));
    }

    private int computePct(java.util.Set<String> completed, String prefix, int max) {
        long count = completed.stream().filter(c -> c.startsWith(prefix)).count();
        return max > 0 ? (int) Math.min(100, (count * 100) / max) : 0;
    }
}
