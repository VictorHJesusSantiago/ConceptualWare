package com.conceptualware.core.control;

import java.util.*;
import java.util.function.*;

/**
 * Concept #1/#2/#3 — Language Primitives
 *
 * Demonstrates language-level primitives that are concepts in their own right:
 *   - assert (design-by-contract, pre/post-conditions, invariants)
 *   - Generator / yield pattern (lazy sequences, push vs pull iteration)
 *   - Symbol / Atom (unique identity without equality by value)
 *   - goto equivalent (labeled break/continue — Java's structured goto)
 *
 * NOTE on goto: Java deliberately omits goto as a keyword (it is a reserved word
 * but throws a compile error if used). The structured alternatives are:
 *   - labeled break:    `outerLoop: for(...) { ... break outerLoop; }`
 *   - labeled continue: `outerLoop: for(...) { ... continue outerLoop; }`
 * These cover all legitimate goto use cases while preventing spaghetti control flow.
 * The C demo (c-demos/05_fork_exec_signals.c) shows real goto usage in C.
 */
public class LanguagePrimitives {

    // ── assert ─────────────────────────────────────────────────────────────────
    /**
     * Concept: assert — Design by Contract (Bertrand Meyer, 1986)
     *
     * Asserts encode CORRECTNESS ASSUMPTIONS about program state.
     * They differ from validation in intent:
     *   Validation: "this input might be wrong" (user/external data)
     *   Assert:     "this CANNOT be wrong" (programmer logic invariant)
     *
     * Java assert:
     *   assert <condition>;            — throws AssertionError if false
     *   assert <condition> : message;  — AssertionError with message
     *   Enable with: java -ea (disabled by default for performance)
     *
     * Three kinds (DbC):
     *   Precondition:  what must be true BEFORE a method runs (caller's duty)
     *   Postcondition: what must be true AFTER a method runs (callee's duty)
     *   Invariant:     what must always be true about an object's state
     */
    public static class DesignByContract {

        /** Invariant: balance must never be negative */
        private long balanceCents;

        public DesignByContract(long initialBalanceCents) {
            // Precondition: initial balance must be non-negative
            assert initialBalanceCents >= 0
                : "Precondition violated: initial balance cannot be negative, got " + initialBalanceCents;
            this.balanceCents = initialBalanceCents;
            assertInvariant();
        }

        public void deposit(long amountCents) {
            // Precondition
            assert amountCents > 0
                : "Precondition: deposit amount must be positive";

            long balanceBefore = balanceCents;
            balanceCents += amountCents;

            // Postcondition: balance increased by exactly amount
            assert balanceCents == balanceBefore + amountCents
                : "Postcondition violated: deposit did not increase balance correctly";
            assertInvariant();
        }

        public void withdraw(long amountCents) {
            // Precondition: sufficient funds
            assert amountCents > 0 : "Precondition: withdraw amount must be positive";
            assert amountCents <= balanceCents
                : "Precondition: insufficient funds — tried " + amountCents + ", have " + balanceCents;

            long balanceBefore = balanceCents;
            balanceCents -= amountCents;

            // Postcondition
            assert balanceCents == balanceBefore - amountCents;
            assertInvariant();
        }

        public long getBalance() { return balanceCents; }

        /** Class invariant — called at end of every mutating method */
        private void assertInvariant() {
            assert balanceCents >= 0
                : "Invariant violated: balance is negative (" + balanceCents + ")";
        }
    }

    /**
     * assertThrows — utility for asserting that code raises a specific exception.
     * Used in test code; analogous to JUnit's assertThrows.
     */
    public static <T extends Throwable> T assertThrows(Class<T> exType, Runnable block) {
        try {
            block.run();
            throw new AssertionError("Expected " + exType.getSimpleName() + " to be thrown");
        } catch (Throwable t) {
            if (exType.isInstance(t)) return exType.cast(t);
            throw new AssertionError("Expected " + exType.getSimpleName()
                + " but got " + t.getClass().getSimpleName(), t);
        }
    }

    // ── Generator / yield ──────────────────────────────────────────────────────
    /**
     * Concept: Generator (yield)
     *
     * A generator is a function that can be PAUSED and RESUMED, producing
     * a sequence of values lazily (one at a time) without computing all upfront.
     *
     * Python/JavaScript: `yield` pauses a function and returns a value.
     * Java doesn't have `yield` for generators, but the pattern is achieved via:
     *   1. Iterator<T>        — pull-based, caller asks for next value
     *   2. Spliterator<T>     — parallel-capable iteration
     *   3. Stream<T>          — lazy functional pipeline over generators
     *   4. Virtual threads    — can simulate coroutines (Java 21+)
     *
     * The TypeScript demo (gateway/src/concepts/generators.ts) shows native yield.
     *
     * KEY INSIGHT: Iterator IS the generator pattern — a lazy sequence producer.
     *   hasNext() = generator is not exhausted
     *   next()    = yield the next value
     */

    /**
     * Fibonacci generator: produces Fibonacci numbers lazily, on demand.
     * Equivalent to Python's:
     *   def fib():
     *     a, b = 0, 1
     *     while True:
     *         yield a
     *         a, b = b, a + b
     */
    public static class FibonacciGenerator implements Iterator<Long> {
        private long a = 0, b = 1;
        private final long limit;
        private int count = 0;

        public FibonacciGenerator(long limit) { this.limit = limit; }

        @Override public boolean hasNext() { return a <= limit; }

        @Override public Long next() {
            if (!hasNext()) throw new NoSuchElementException();
            long value = a;
            long next = a + b;
            a = b;
            b = next;
            count++;
            return value;
        }

        public int produced() { return count; }
    }

    /**
     * Range generator: produces integers from start (inclusive) to end (exclusive).
     * Equivalent to Python's range(start, end, step).
     * Memory: O(1) regardless of range size — classic generator advantage.
     */
    public static class RangeGenerator implements Iterable<Integer>, Iterator<Integer> {
        private final int end, step;
        private int current;

        public RangeGenerator(int start, int end, int step) {
            assert step != 0 : "step cannot be zero";
            assert (step > 0 && start <= end) || (step < 0 && start >= end)
                : "infinite range: start=" + start + " end=" + end + " step=" + step;
            this.current = start;
            this.end     = end;
            this.step    = step;
        }

        @Override public boolean hasNext() {
            return step > 0 ? current < end : current > end;
        }

        @Override public Integer next() {
            if (!hasNext()) throw new NoSuchElementException();
            int val = current;
            current += step;
            return val;
        }

        @Override public Iterator<Integer> iterator() { return this; }
    }

    /**
     * Infinite generator with takeWhile: prime number sieve (lazy).
     * Demonstrates generator as infinite sequence + external termination.
     */
    public static class PrimeGenerator implements Iterator<Integer> {
        private int candidate = 2;
        private final List<Integer> found = new ArrayList<>();

        @Override public boolean hasNext() { return true; }  // infinite

        @Override public Integer next() {
            while (!isPrime(candidate)) candidate++;
            int prime = candidate++;
            found.add(prime);
            return prime;
        }

        private boolean isPrime(int n) {
            for (int p : found) {
                if (p * p > n) return true;
                if (n % p == 0) return false;
            }
            return true;
        }

        /** takeWhile: collect primes while predicate holds. */
        public static List<Integer> takeWhile(Iterator<Integer> gen, Predicate<Integer> pred) {
            List<Integer> result = new ArrayList<>();
            while (gen.hasNext()) {
                int v = gen.next();
                if (!pred.test(v)) break;
                result.add(v);
            }
            return result;
        }
    }

    // ── Symbol / Atom ──────────────────────────────────────────────────────────
    /**
     * Concept: Symbol / Atom
     *
     * A Symbol is a UNIQUE, OPAQUE identifier whose identity is its own value.
     * Two symbols with the same description are NOT equal:
     *   Symbol.for("foo") != Symbol.for("foo")  // in JS: true — each is unique
     *   (unless using a global registry: Symbol.for() returns same for same key)
     *
     * Erlang Atoms: like symbols but interned — the atom `ok` always equals `ok`.
     *
     * Java equivalents:
     *   - Enum constants (compile-time atoms)
     *   - `new Object()` (unique identity, no value)
     *   - String.intern() (string atom — reference equality after interning)
     *
     * Use cases:
     *   - Map keys where description doesn't matter, only identity
     *   - Preventing name collisions in shared namespaces (e.g., well-known keys)
     *   - Erlang message tags: `{ok, Value}`, `{error, Reason}`
     */
    public static final class Symbol {
        private static final Map<String, Symbol> REGISTRY = new HashMap<>();

        private final String description;
        private final boolean global;

        // Private constructor — Symbols are created only through factory methods
        private Symbol(String description, boolean global) {
            this.description = description;
            this.global      = global;
        }

        /**
         * Create a LOCAL symbol — unique even if another has the same description.
         *   Symbol s1 = Symbol.create("id");
         *   Symbol s2 = Symbol.create("id");
         *   s1 != s2  (different object references — unique identities)
         */
        public static Symbol create(String description) {
            return new Symbol(description, false);   // new object each time
        }

        /**
         * Create a GLOBAL symbol — same description always returns same instance.
         * Equivalent to JS Symbol.for() / Erlang atoms.
         *   Symbol s1 = Symbol.forKey("ok");
         *   Symbol s2 = Symbol.forKey("ok");
         *   s1 == s2  (same object — reference equality works)
         */
        public static Symbol forKey(String key) {
            return REGISTRY.computeIfAbsent(key, k -> new Symbol(k, true));
        }

        /** Well-known symbols (Erlang-style atoms for common outcomes) */
        public static final Symbol OK    = forKey("ok");
        public static final Symbol ERROR = forKey("error");
        public static final Symbol NONE  = forKey("none");

        public String description() { return description; }

        @Override public String toString() { return "Symbol(" + description + ")"; }

        // Identity-based equals (default Object behavior — NOT overridden)
        // Two local symbols with same description are != each other
    }

    /**
     * Erlang-style tagged tuple using Symbol atoms:
     *   {ok, Value} or {error, Reason}
     */
    public record Tagged<V>(Symbol tag, V value) {
        public boolean isOk()    { return tag == Symbol.OK; }
        public boolean isError() { return tag == Symbol.ERROR; }

        public static <V> Tagged<V> ok(V value)    { return new Tagged<>(Symbol.OK, value); }
        public static <V> Tagged<V> error(V reason) { return new Tagged<>(Symbol.ERROR, reason); }
    }

    // ── Labeled break/continue (Java's structured goto) ───────────────────────
    /**
     * Concept: goto alternatives in Java
     *
     * Java's `goto` is reserved but not implemented. Labeled break/continue
     * cover all legitimate use cases.
     *
     * Examples where goto is traditionally used:
     *   1. Exiting nested loops                → labeled break
     *   2. Skipping iterations in nested loops → labeled continue
     *   3. Cleanup on error in C              → Java uses try/finally
     */
    public static class GotoEquivalents {

        /** Labeled break — exit outer loop when inner condition met. */
        public static int[] findInMatrix(int[][] matrix, int target) {
            int[] result = null;

            // "goto found" equivalent — jumps out of both loops
            outerLoop:
            for (int r = 0; r < matrix.length; r++) {
                for (int c = 0; c < matrix[r].length; c++) {
                    if (matrix[r][c] == target) {
                        result = new int[]{r, c};
                        break outerLoop;  // labeled break = goto past outerLoop
                    }
                }
            }
            return result;
        }

        /** Labeled continue — skip to next outer iteration. */
        public static List<int[]> findAllNotInRow(int[][] matrix, Set<Integer> excluded) {
            List<int[]> results = new ArrayList<>();

            outerLoop:
            for (int r = 0; r < matrix.length; r++) {
                for (int c = 0; c < matrix[r].length; c++) {
                    if (excluded.contains(matrix[r][c])) {
                        continue outerLoop;   // skip entire row if any excluded value found
                    }
                }
                results.add(new int[]{r});   // whole row is clean
            }
            return results;
        }

        /**
         * Cleanup pattern (C goto's most common legitimate use):
         * In C: allocate → use → goto cleanup on error → cleanup:
         *
         *   In Java: try-finally / try-with-resources achieves the same,
         *   with better semantics (RAII-like, exception-safe).
         */
        public static String cleanupPattern(boolean simulateError) {
            StringBuilder log = new StringBuilder();
            // In C this would use goto cleanup:
            try {
                log.append("acquired resource; ");
                if (simulateError) throw new RuntimeException("simulated error");
                log.append("used resource; ");
                return log + "done";
            } finally {
                log.append("released resource");
                // always runs — equivalent to C's cleanup: label
            }
        }
    }
}
