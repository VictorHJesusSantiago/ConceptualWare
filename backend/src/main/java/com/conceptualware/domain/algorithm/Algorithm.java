package com.conceptualware.domain.algorithm;

import com.conceptualware.domain.shared.AggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.Collections;

@Document(collection = "algorithms")
@Getter
@NoArgsConstructor
public class Algorithm extends AggregateRoot {

    public enum Category {
        SORTING, SEARCHING, GRAPH, DYNAMIC_PROGRAMMING, STRING,
        GREEDY, DIVIDE_AND_CONQUER, BACKTRACKING, MATHEMATICAL,
        DATA_STRUCTURES, COMPRESSION, CRYPTOGRAPHY, CONCURRENCY
    }

    public enum Difficulty { EASY, MEDIUM, HARD, EXPERT }

    public record Complexity(String time, String space, String description) {}

    public record Implementation(String language, String code, boolean isVerified) {}

    public record ExecutionStep(int stepNumber, String description, int[] arrayState,
                                 Map<String, Object> variables) {}

    @Indexed(unique = true)
    private String slug;

    @TextIndexed(weight = 10)
    private String name;

    @TextIndexed(weight = 5)
    private String description;

    private Category category;
    private Difficulty difficulty;

    private Complexity timeComplexity;
    private Complexity spaceComplexity;

    private final List<String> tags = new ArrayList<>();
    private final List<Implementation> implementations = new ArrayList<>();
    private final List<String> prerequisites = new ArrayList<>();
    private final List<String> applications = new ArrayList<>();

    private int viewCount = 0;
    private int likeCount = 0;
    private double averageRating = 0;
    private int ratingCount = 0;

    private boolean isStable;
    private boolean isInPlace;
    private boolean isRecursive;

    public static Algorithm create(String slug, String name, String description,
                                    Category category, Difficulty difficulty,
                                    Complexity timeComplexity, Complexity spaceComplexity) {
        Algorithm algo = new Algorithm();
        algo.slug = Objects.requireNonNull(slug);
        algo.name = Objects.requireNonNull(name);
        algo.description = Objects.requireNonNull(description);
        algo.category = Objects.requireNonNull(category);
        algo.difficulty = Objects.requireNonNull(difficulty);
        algo.timeComplexity = Objects.requireNonNull(timeComplexity);
        algo.spaceComplexity = Objects.requireNonNull(spaceComplexity);
        return algo;
    }

    public void addImplementation(String language, String code) {
        implementations.add(new Implementation(language, code, false));
    }

    public void addTag(String tag) { tags.add(tag.toLowerCase().trim()); }
    public void addPrerequisite(String algorithmSlug) { prerequisites.add(algorithmSlug); }
    public void addApplication(String application) { applications.add(application); }

    public void recordView() { this.viewCount++; }
    public void recordLike() { this.likeCount++; }
    public void removeLike() { if (this.likeCount > 0) this.likeCount--; }

    public void recordRating(double rating) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be 1-5");
        double totalRating = this.averageRating * this.ratingCount + rating;
        this.ratingCount++;
        this.averageRating = totalRating / this.ratingCount;
    }

    public boolean isPopular() { return viewCount > 1000 || likeCount > 100; }

    public List<Implementation> getImplementations() {
        return Collections.unmodifiableList(implementations);
    }

    public List<String> getTags() { return Collections.unmodifiableList(tags); }

    public List<String> getPrerequisites() { return Collections.unmodifiableList(prerequisites); }

    public List<String> getApplications() { return Collections.unmodifiableList(applications); }
}
