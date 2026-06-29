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

    // ── Lockout por força bruta (Concept #21 — Brute Force Protection) ──────────
    // Bloqueia a conta por LOCKOUT_DURATION após MAX_FAILED_ATTEMPTS consecutivos.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final java.time.Duration LOCKOUT_DURATION = java.time.Duration.ofMinutes(15);

    private int failedLoginAttempts = 0;
    private Instant lockedUntil;

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

    // ── Brute Force Protection ────────────────────────────────────────────────

    /** Retorna true se a conta está bloqueada no momento. */
    public boolean isLockedOut() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /** Registra uma tentativa de login inválida e aplica lockout se necessário. */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plus(LOCKOUT_DURATION);
        }
    }

    /** Reseta o contador após login bem-sucedido. */
    public void resetFailedLogins() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Tempo restante de lockout (zero se não bloqueado). */
    public java.time.Duration lockoutRemaining() {
        if (!isLockedOut()) return java.time.Duration.ZERO;
        return java.time.Duration.between(Instant.now(), lockedUntil);
    }

    public void promoteToAdmin() {
        this.roles.add(Role.ADMIN);
        registerEvent(new UserRoleChangedEvent(this.id, Role.ADMIN));
    }

    public boolean isActive() { return status == Status.ACTIVE; }
    public boolean hasRole(Role role) { return roles.contains(role); }

    // ── Getters seguros para coleções mutáveis ────────────────────────────────
    // O @Getter do Lombok no nível da classe expõe as referências internas.
    // Esses métodos sobrescrevem o comportamento para retornar cópias somente-leitura.

    /** Papéis do usuário — imutável para o chamador. */
    public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }

    /** Conceitos concluídos — imutável. */
    public Set<String> getCompletedConcepts() { return Collections.unmodifiableSet(completedConcepts); }

    /** Favoritos — imutável. */
    public Set<String> getFavorites() { return Collections.unmodifiableSet(favorites); }

    // refreshTokens NÃO é exposto: contém segredos. Acesso somente via métodos de domínio.

    // ── Knowledge Progress (Concept #30 — recommendations) ────────────────────

    public double progressPercentage(int totalConcepts) {
        if (totalConcepts <= 0) return 0;
        return (double) completedConcepts.size() / totalConcepts * 100;
    }

    private void updateSkillLevel() {
        // NOTE: primitive type patterns in `switch` are not available in Java 21
        // (records/sealed patterns are, but `case int p when ...` is not), so use guards.
        if (totalPoints >= 10000)     this.skillLevel = SkillLevel.EXPERT;
        else if (totalPoints >= 3000) this.skillLevel = SkillLevel.ADVANCED;
        else if (totalPoints >= 500)  this.skillLevel = SkillLevel.INTERMEDIATE;
        else                          this.skillLevel = SkillLevel.BEGINNER;
    }

    // ── Domain Events ─────────────────────────────────────────────────────────
    // A Java `record` cannot extend a class (records implicitly extend java.lang.Record),
    // so domain events that must be DomainEvent subtypes are immutable final classes.

    @Getter
    public static final class UserRegisteredEvent extends DomainEvent {
        private final String email;
        private final String username;
        public UserRegisteredEvent(String email, String username) {
            super("user.registered");
            this.email = email;
            this.username = username;
        }
    }

    @Getter
    public static final class UserEmailVerifiedEvent extends DomainEvent {
        private final String email;
        public UserEmailVerifiedEvent(String email) {
            super("user.email.verified");
            this.email = email;
        }
    }

    @Getter
    public static final class PointsEarnedEvent extends DomainEvent {
        private final String userId;
        private final int points;
        private final int totalPoints;
        public PointsEarnedEvent(String userId, int points, int totalPoints) {
            super("user.points.earned");
            this.userId = userId;
            this.points = points;
            this.totalPoints = totalPoints;
        }
    }

    @Getter
    public static final class ChallengeCompletedEvent extends DomainEvent {
        private final String userId;
        private final String challengeId;
        public ChallengeCompletedEvent(String userId, String challengeId) {
            super("challenge.completed");
            this.userId = userId;
            this.challengeId = challengeId;
        }
    }

    @Getter
    public static final class ConceptCompletedEvent extends DomainEvent {
        private final String userId;
        private final String conceptId;
        public ConceptCompletedEvent(String userId, String conceptId) {
            super("concept.completed");
            this.userId = userId;
            this.conceptId = conceptId;
        }
    }

    @Getter
    public static final class UserRoleChangedEvent extends DomainEvent {
        private final String userId;
        private final Role newRole;
        public UserRoleChangedEvent(String userId, Role newRole) {
            super("user.role.changed");
            this.userId = userId;
            this.newRole = newRole;
        }
    }
}
