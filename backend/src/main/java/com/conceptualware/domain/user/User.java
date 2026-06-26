package com.conceptualware.domain.user;

import com.conceptualware.domain.shared.AggregateRoot;
import com.conceptualware.domain.shared.DomainEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;

/**
 * Concept #7  — OOP: Classe, Atributos, Métodos, Herança (AggregateRoot),
 *   Encapsulamento (private setters), Enum, Record, Modificadores de acesso
 * Concept #12 — DDD: Entidade, Agregado, Objeto de Valor (Email, Username),
 *   Bounded Context (user domain)
 * Concept #14 — SOLID: SRP (User only manages user invariants),
 *   OCP (extensible via events), LSP, ISP, DIP
 * Concept #11 — MongoDB @Document mapping
 * Concept #21 — Segurança: Armazenamento seguro de senha (hash), Role-based access
 */
@Document(collection = "users")
@Getter
@NoArgsConstructor
public class User extends AggregateRoot {

    // ── Value Objects ─────────────────────────────────────────────────────────

    public record Email(String value) {
        public Email {
            Objects.requireNonNull(value, "Email cannot be null");
            if (!value.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
                throw new IllegalArgumentException("Invalid email: " + value);
        }
    }

    public record Username(String value) {
        public Username {
            Objects.requireNonNull(value, "Username cannot be null");
            if (value.isBlank() || value.length() < 3 || value.length() > 50)
                throw new IllegalArgumentException("Username must be 3-50 chars");
            if (!value.matches("^[a-zA-Z0-9_-]+$"))
                throw new IllegalArgumentException("Username may only contain letters, digits, _, -");
        }
    }

    // ── User Roles (RBAC — Concept #21) ──────────────────────────────────────

    public enum Role { USER, PREMIUM, ADMIN }

    // ── User Status ────────────────────────────────────────────────────────────

    public enum Status { ACTIVE, SUSPENDED, PENDING_VERIFICATION, DELETED }

    // ── Skill Level (domain concept) ──────────────────────────────────────────

    public enum SkillLevel { BEGINNER, INTERMEDIATE, ADVANCED, EXPERT }

    // ── Fields ────────────────────────────────────────────────────────────────

    @Indexed(unique = true)
    private String email;

    @Indexed(unique = true)
    private String username;

    private String passwordHash;  // BCrypt — Concept #21

    private Set<Role> roles = new HashSet<>(Set.of(Role.USER));
    private Status status = Status.PENDING_VERIFICATION;
    private SkillLevel skillLevel = SkillLevel.BEGINNER;

    private int totalPoints = 0;
    private int challengesSolved = 0;
    private int currentStreak = 0;
    private int longestStreak = 0;

    private Instant lastActiveAt;
    private Instant emailVerifiedAt;

    // Refresh tokens (Concept #21 — Token management)
    private final Map<String, Instant> refreshTokens = new HashMap<>();

    // Progress tracking — Set of completed concept IDs
    private final Set<String> completedConcepts = new HashSet<>();

    // Favorite algorithms
    private final Set<String> favorites = new HashSet<>();

    // ── Factory Method ────────────────────────────────────────────────────────

    public static User create(String email, String username, String passwordHash) {
        User user = new User();
        user.email = new Email(email).value();
        user.username = new Username(username).value();
        user.passwordHash = Objects.requireNonNull(passwordHash, "Password hash required");
        user.lastActiveAt = Instant.now();
        user.registerEvent(new UserRegisteredEvent(user.email, user.username));
        return user;
    }

    // ── Business Methods ──────────────────────────────────────────────────────

    public void verifyEmail() {
        if (status != Status.PENDING_VERIFICATION)
            throw new IllegalStateException("Email already verified or account suspended");
        this.status = Status.ACTIVE;
        this.emailVerifiedAt = Instant.now();
        registerEvent(new UserEmailVerifiedEvent(this.email));
    }

    public void addPoints(int points) {
        if (points <= 0) throw new IllegalArgumentException("Points must be positive");
        this.totalPoints += points;
        updateSkillLevel();
        registerEvent(new PointsEarnedEvent(this.id, points, this.totalPoints));
    }

    public void markChallengeCompleted(String challengeId) {
        this.challengesSolved++;
        this.currentStreak++;
        this.longestStreak = Math.max(this.longestStreak, this.currentStreak);
        registerEvent(new ChallengeCompletedEvent(this.id, challengeId));
    }

    public void completeConcept(String conceptId) {
        this.completedConcepts.add(conceptId);
        registerEvent(new ConceptCompletedEvent(this.id, conceptId));
    }

    public void addToFavorites(String algorithmId) { this.favorites.add(algorithmId); }
    public void removeFromFavorites(String algorithmId) { this.favorites.remove(algorithmId); }

    public void storeRefreshToken(String token) {
        // Limit to 5 concurrent sessions — Concept #21 (session management)
        if (refreshTokens.size() >= 5) {
            String oldest = refreshTokens.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
            if (oldest != null) refreshTokens.remove(oldest);
        }
        refreshTokens.put(token, Instant.now().plusSeconds(604800)); // 7 days
    }

    public boolean isRefreshTokenValid(String token) {
        Instant expiry = refreshTokens.get(token);
        return expiry != null && Instant.now().isBefore(expiry);
    }

    public void revokeRefreshToken(String token) { refreshTokens.remove(token); }

    public void recordActivity() { this.lastActiveAt = Instant.now(); }

    public void promoteToAdmin() {
        this.roles.add(Role.ADMIN);
        registerEvent(new UserRoleChangedEvent(this.id, Role.ADMIN));
    }

    public boolean isActive() { return status == Status.ACTIVE; }
    public boolean hasRole(Role role) { return roles.contains(role); }

    // ── Knowledge Progress (Concept #30 — recommendations) ────────────────────

    public double progressPercentage(int totalConcepts) {
        if (totalConcepts <= 0) return 0;
        return (double) completedConcepts.size() / totalConcepts * 100;
    }

    private void updateSkillLevel() {
        this.skillLevel = switch (totalPoints) {
            case int p when p >= 10000 -> SkillLevel.EXPERT;
            case int p when p >= 3000  -> SkillLevel.ADVANCED;
            case int p when p >= 500   -> SkillLevel.INTERMEDIATE;
            default                     -> SkillLevel.BEGINNER;
        };
    }

    // ── Domain Events ─────────────────────────────────────────────────────────

    public record UserRegisteredEvent(String email, String username) extends DomainEvent {
        public UserRegisteredEvent { super("user.registered"); }
        public UserRegisteredEvent(String email, String username) {
            this();
            // Java 21 compact record constructor
        }
    }

    public record UserEmailVerifiedEvent(String email) extends DomainEvent {
        public UserEmailVerifiedEvent { super("user.email.verified"); }
        public UserEmailVerifiedEvent(String email) { this(); }
    }

    public record PointsEarnedEvent(String userId, int points, int totalPoints) extends DomainEvent {
        public PointsEarnedEvent { super("user.points.earned"); }
        public PointsEarnedEvent(String userId, int points, int totalPoints) { this(); }
    }

    public record ChallengeCompletedEvent(String userId, String challengeId) extends DomainEvent {
        public ChallengeCompletedEvent { super("challenge.completed"); }
        public ChallengeCompletedEvent(String userId, String challengeId) { this(); }
    }

    public record ConceptCompletedEvent(String userId, String conceptId) extends DomainEvent {
        public ConceptCompletedEvent { super("concept.completed"); }
        public ConceptCompletedEvent(String userId, String conceptId) { this(); }
    }

    public record UserRoleChangedEvent(String userId, Role newRole) extends DomainEvent {
        public UserRoleChangedEvent { super("user.role.changed"); }
        public UserRoleChangedEvent(String userId, Role newRole) { this(); }
    }
}
