package com.conceptualware.core.control;

import java.util.*;

/**
 * Concept #2 — Estruturas de Controle (complete coverage):
 *   if/else, switch/case, ternary, for, while, do-while, foreach,
 *   nested loop, break, continue, return, goto (labeled break),
 *   try/catch/finally, throw, multiple exceptions, exception hierarchy,
 *   checked vs unchecked, assert, Pattern matching (switch expressions), yield.
 *
 * Concept #6  — Paradigma orientado a eventos / imperativo
 * Concept #10 — DSL de controle de fluxo
 */
public class ControlFlowEngine {

    // ── §1  if / else if / else ─────────────────────────────────────────────

    public String classifyTemperature(double celsius) {
        if (celsius < 0) {
            return "freezing";
        } else if (celsius < 15) {
            return "cold";
        } else if (celsius < 25) {
            return "comfortable";
        } else if (celsius < 35) {
            return "warm";
        } else {
            return "hot";
        }
    }

    // ── §2  switch / case / default ─────────────────────────────────────────

    public String dayName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1  -> "Monday";
            case 2  -> "Tuesday";
            case 3  -> "Wednesday";
            case 4  -> "Thursday";
            case 5  -> "Friday";
            case 6  -> "Saturday";
            case 7  -> "Sunday";
            default -> "Unknown";
        };
    }

    // ── §3  Ternary operator ─────────────────────────────────────────────────

    public String isEven(int n) { return (n % 2 == 0) ? "even" : "odd"; }

    // ── §4  for (loop contado) ───────────────────────────────────────────────

    public int sumUpTo(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) sum += i;
        return sum;
    }

    // ── §5  while (pré-condição) ─────────────────────────────────────────────

    public int collatzSteps(int n) {
        int steps = 0;
        while (n != 1) {
            n = (n % 2 == 0) ? n / 2 : 3 * n + 1;
            steps++;
        }
        return steps;
    }

    // ── §6  do-while (pós-condição) ──────────────────────────────────────────

    public int digitalRoot(int n) {
        do {
            int sum = 0;
            while (n > 0) { sum += n % 10; n /= 10; }
            n = sum;
        } while (n >= 10);
        return n;
    }

    // ── §7  foreach / enhanced-for ───────────────────────────────────────────

    public int sumArray(int[] arr) {
        int total = 0;
        for (int val : arr) total += val; // enhanced for = foreach
        return total;
    }

    // ── §8  Nested loops ─────────────────────────────────────────────────────

    public int[][] multiplicationTable(int size) {
        int[][] table = new int[size][size];
        for (int i = 0; i < size; i++)         // outer loop
            for (int j = 0; j < size; j++)     // nested loop
                table[i][j] = (i + 1) * (j + 1);
        return table;
    }

    // ── §9  break / continue ─────────────────────────────────────────────────

    /** Returns first prime >= n. Uses break to exit the inner loop. */
    public int nextPrime(int n) {
        outer: for (int candidate = Math.max(n, 2); ; candidate++) {
            for (int d = 2; d * d <= candidate; d++) {
                if (candidate % d == 0) continue outer; // continue to next candidate
            }
            return candidate; // break from method — is prime
        }
    }

    // ── §10  GOTO equivalent — Java Labeled Break ────────────────────────────
    // Java does not have `goto` but labeled break/continue serve the same purpose:
    // jumping out of a specific named block (even nested ones).

    /**
     * Finds coordinates of `target` in a 2D matrix using a labeled break.
     * The label `search:` marks the outer loop; `break search` jumps out of both loops.
     * This is the idiomatic Java equivalent of `goto`.
     */
    public int[] labeledBreakSearch(int[][] matrix, int target) {
        int[] found = {-1, -1};

        search:                                      // ← label (goto target)
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                if (matrix[row][col] == target) {
                    found[0] = row;
                    found[1] = col;
                    break search;                    // ← goto: jumps directly out of labeled block
                }
            }
        }
        return found;
    }

    // ── §11  try / catch / finally / throw ──────────────────────────────────

    public double safeDivide(double a, double b) {
        try {
            if (b == 0) throw new ArithmeticException("Division by zero");
            return a / b;
        } catch (ArithmeticException e) {
            return Double.NaN;
        } finally {
            // always executes — cleanup, logging, resource release
        }
    }

    // ── §12  Multiple exception capture ─────────────────────────────────────

    public String parseAndFormat(String input) {
        try {
            int value = Integer.parseInt(input.trim()); // may throw NumberFormatException or NPE
            return "Value: " + value;
        } catch (NumberFormatException e) {
            return "Not a number: " + e.getMessage();
        } catch (NullPointerException e) {
            return "Input was null";
        } catch (Exception e) {                         // base-class catch-all last
            return "Unexpected: " + e.getClass().getSimpleName();
        }
    }

    // ── §13  Exception hierarchy (checked vs unchecked) ─────────────────────

    /** Checked exception — must be declared or caught (Concept #2). */
    public static class ConceptNotFoundException extends Exception {          // checked
        public ConceptNotFoundException(String concept) {
            super("Concept not found: " + concept);
        }
    }

    /** Unchecked exception — extends RuntimeException (Concept #2). */
    public static class InvalidInputException extends RuntimeException {      // unchecked
        public InvalidInputException(String msg) { super(msg); }
    }

    public String lookupConcept(String name) throws ConceptNotFoundException {
        if (name == null || name.isBlank()) throw new InvalidInputException("Name cannot be blank");
        Set<String> known = Set.of("OOP", "FP", "DDD", "SOLID", "CQRS", "TDD");
        if (!known.contains(name)) throw new ConceptNotFoundException(name);
        return "Concept '" + name + "' found";
    }

    // ── §14  assert (Concept #2) ─────────────────────────────────────────────
    // Java assert keyword: enabled with -ea JVM flag.
    // Used for design-by-contract invariant checking during development.

    public int factorial(int n) {
        assert n >= 0 : "n must be non-negative, got: " + n; // ← assert keyword (Concept #2)
        if (n == 0) return 1;
        return n * factorial(n - 1);
    }

    // ── §15  Java 21 Pattern Matching switch expressions (Concept #2) ────────

    sealed interface Shape permits Shape.Circle, Shape.Rectangle, Shape.Triangle {
        record Circle(double radius) implements Shape {}
        record Rectangle(double w, double h) implements Shape {}
        record Triangle(double base, double height) implements Shape {}
    }

    public double area(Shape shape) {
        return switch (shape) {                                     // pattern matching switch
            case Shape.Circle c       -> Math.PI * c.radius() * c.radius();
            case Shape.Rectangle r    -> r.w() * r.h();
            case Shape.Triangle t     -> 0.5 * t.base() * t.height();
        };
    }

    // ── §16  Async/await control flow ────────────────────────────────────────
    // Demonstrated in AlgorithmApplicationService (CompletableFuture + @Async).
    // Here we show structured try/catch around async result retrieval.

    public <T> T safeGet(java.util.concurrent.Future<T> future) {
        try {
            return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new InvalidInputException("Operation timed out");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new InvalidInputException("Async execution failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidInputException("Thread interrupted");
        }
    }
}
