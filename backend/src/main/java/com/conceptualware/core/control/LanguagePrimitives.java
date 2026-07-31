package com.conceptualware.core.control;

import java.util.*;
import java.util.function.*;

public class LanguagePrimitives {

    public static class DesignByContract {

        private long balanceCents;

        public DesignByContract(long initialBalanceCents) {
            assert initialBalanceCents >= 0
                : "Precondition violated: initial balance cannot be negative, got " + initialBalanceCents;
            this.balanceCents = initialBalanceCents;
            assertInvariant();
        }

        public void deposit(long amountCents) {
            assert amountCents > 0
                : "Precondition: deposit amount must be positive";

            long balanceBefore = balanceCents;
            balanceCents += amountCents;

            assert balanceCents == balanceBefore + amountCents
                : "Postcondition violated: deposit did not increase balance correctly";
            assertInvariant();
        }

        public void withdraw(long amountCents) {
            assert amountCents > 0 : "Precondition: withdraw amount must be positive";
            assert amountCents <= balanceCents
                : "Precondition: insufficient funds — tried " + amountCents + ", have " + balanceCents;

            long balanceBefore = balanceCents;
            balanceCents -= amountCents;

            assert balanceCents == balanceBefore - amountCents;
            assertInvariant();
        }

        public long getBalance() { return balanceCents; }

        private void assertInvariant() {
            assert balanceCents >= 0
                : "Invariant violated: balance is negative (" + balanceCents + ")";
        }
    }

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

    public static class PrimeGenerator implements Iterator<Integer> {
        private int candidate = 2;
        private final List<Integer> found = new ArrayList<>();

        @Override public boolean hasNext() { return true; }

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

    public static final class Symbol {
        private static final Map<String, Symbol> REGISTRY = new HashMap<>();

        private final String description;
        private final boolean global;

        private Symbol(String description, boolean global) {
            this.description = description;
            this.global      = global;
        }

        public static Symbol create(String description) {
            return new Symbol(description, false);
        }

        public static Symbol forKey(String key) {
            return REGISTRY.computeIfAbsent(key, k -> new Symbol(k, true));
        }

        public static final Symbol OK    = forKey("ok");
        public static final Symbol ERROR = forKey("error");
        public static final Symbol NONE  = forKey("none");

        public String description() { return description; }

        @Override public String toString() { return "Symbol(" + description + ")"; }

    }

    public record Tagged<V>(Symbol tag, V value) {
        public boolean isOk()    { return tag == Symbol.OK; }
        public boolean isError() { return tag == Symbol.ERROR; }

        public static <V> Tagged<V> ok(V value)    { return new Tagged<>(Symbol.OK, value); }
        public static <V> Tagged<V> error(V reason) { return new Tagged<>(Symbol.ERROR, reason); }
    }

    public static class GotoEquivalents {

        public static int[] findInMatrix(int[][] matrix, int target) {
            int[] result = null;

            outerLoop:
            for (int r = 0; r < matrix.length; r++) {
                for (int c = 0; c < matrix[r].length; c++) {
                    if (matrix[r][c] == target) {
                        result = new int[]{r, c};
                        break outerLoop;
                    }
                }
            }
            return result;
        }

        public static List<int[]> findAllNotInRow(int[][] matrix, Set<Integer> excluded) {
            List<int[]> results = new ArrayList<>();

            outerLoop:
            for (int r = 0; r < matrix.length; r++) {
                for (int c = 0; c < matrix[r].length; c++) {
                    if (excluded.contains(matrix[r][c])) {
                        continue outerLoop;
                    }
                }
                results.add(new int[]{r});
            }
            return results;
        }

        public static String cleanupPattern(boolean simulateError) {
            StringBuilder log = new StringBuilder();
            try {
                log.append("acquired resource; ");
                if (simulateError) throw new RuntimeException("simulated error");
                log.append("used resource; ");
                return log + "done";
            } finally {
                log.append("released resource");
            }
        }
    }
}
