package com.conceptualware.domain.challenge;

import com.conceptualware.domain.shared.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

/**
 * Concept #7  — OOP: Composição, Associação, Enum, Classe interna
 * Concept #12 — DDD: Entidade, Objeto de Valor (TestCase, Submission)
 * Concept #19 — Testes: Casos de teste, critérios de aceitação (Given-When-Then)
 */
@Document(collection = "challenges")
@Getter
@NoArgsConstructor
public class Challenge extends AggregateRoot {

    public enum Difficulty { EASY, MEDIUM, HARD, EXPERT }
    public enum ChallengeType { ALGORITHM, DATA_STRUCTURE, DEBUGGING, DESIGN }
    public enum Language { JAVA, TYPESCRIPT, JAVASCRIPT, PYTHON, GO }

    // Test case — Given-When-Then structure (Concept #19)
    public record TestCase(
        String description,   // Given
        String input,         // When
        String expectedOutput, // Then
        boolean isPublic,
        int points
    ) {}

    public record Submission(
        String userId,
        String language,
        String code,
        boolean passed,
        int passedTests,
        int totalTests,
        long executionTimeMs,
        long memoryUsedBytes,
        java.time.Instant submittedAt
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private String title;
    private String description;
    private String problemStatement;
    private String constraints;
    private Difficulty difficulty;
    private ChallengeType type;

    private final List<TestCase> testCases = new ArrayList<>();
    private final List<Submission> submissions = new ArrayList<>();
    private final Set<String> allowedLanguages = new HashSet<>();

    private int points;
    private int timeLimitMs = 5000;
    private int memoryLimitMb = 256;
    private int maxInputSize = 10_000;

    private String starterCode;
    private String solutionCode; // Hidden from users
    private String algorithmSlug; // Related algorithm

    private double successRate = 0;
    private int totalAttempts = 0;
    private int successfulAttempts = 0;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Challenge create(String title, String description, String problemStatement,
                                    Difficulty difficulty, ChallengeType type, int points) {
        Challenge c = new Challenge();
        c.title = Objects.requireNonNull(title);
        c.description = Objects.requireNonNull(description);
        c.problemStatement = Objects.requireNonNull(problemStatement);
        c.difficulty = Objects.requireNonNull(difficulty);
        c.type = Objects.requireNonNull(type);
        if (points < 0) throw new IllegalArgumentException("Points must be non-negative");
        c.points = points;
        return c;
    }

    // ── Business Methods ──────────────────────────────────────────────────────

    public void addTestCase(String desc, String input, String expected, boolean pub, int pts) {
        testCases.add(new TestCase(desc, input, expected, pub, pts));
    }

    public void recordSubmission(Submission submission) {
        submissions.add(submission);
        totalAttempts++;
        if (submission.passed()) successfulAttempts++;
        successRate = totalAttempts > 0 ? (double) successfulAttempts / totalAttempts * 100 : 0;
    }

    public List<TestCase> publicTestCases() {
        return testCases.stream().filter(TestCase::isPublic).toList();
    }

    public OptionalInt maxPoints() {
        return testCases.stream().mapToInt(TestCase::points).reduce(Integer::sum);
    }

    public boolean isHard() {
        return difficulty == Difficulty.HARD || difficulty == Difficulty.EXPERT;
    }
}
