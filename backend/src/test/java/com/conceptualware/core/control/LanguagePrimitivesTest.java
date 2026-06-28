package com.conceptualware.core.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Language Primitives: assert/DbC, generators (Iterator), Symbol, goto-equivalents.
 */
@DisplayName("Language Primitives")
class LanguagePrimitivesTest {

    // ── Design by Contract ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DbC: valid withdrawal satisfies postcondition and invariant")
    void testDbcValidWithdrawal() {
        var account = new LanguagePrimitives.DesignByContract.BankAccount(1000);
        account.withdraw(300);
        assertEquals(700, account.getBalance());
    }

    @Test
    @DisplayName("DbC: precondition rejects negative withdrawal amount")
    void testDbcPreconditionViolation() {
        var account = new LanguagePrimitives.DesignByContract.BankAccount(500);
        assertThrows(AssertionError.class, () -> account.withdraw(-50));
    }

    @Test
    @DisplayName("DbC: precondition rejects overdraft (amount > balance)")
    void testDbcOverdraftRejected() {
        var account = new LanguagePrimitives.DesignByContract.BankAccount(100);
        assertThrows(AssertionError.class, () -> account.withdraw(200));
    }

    // ── Fibonacci Generator ────────────────────────────────────────────────────

    @Test
    @DisplayName("FibonacciGenerator: first ten values are correct")
    void testFibonacciFirst10() {
        var gen = new LanguagePrimitives.FibonacciGenerator(Long.MAX_VALUE);
        long[] expected = {0, 1, 1, 2, 3, 5, 8, 13, 21, 34};
        for (long exp : expected) {
            assertTrue(gen.hasNext());
            assertEquals(exp, gen.next());
        }
    }

    @Test
    @DisplayName("FibonacciGenerator: produces at least 100 values within limit")
    void testFibonacciManyValues() {
        var gen = new LanguagePrimitives.FibonacciGenerator(Long.MAX_VALUE);
        for (int i = 0; i < 100; i++) {
            assertTrue(gen.hasNext());
            gen.next();
        }
        assertEquals(100, gen.produced());
    }

    // ── Range Generator ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RangeGenerator: step=1 produces correct values")
    void testRangeForwardStep1() {
        var range = new LanguagePrimitives.RangeGenerator(0, 5, 1);
        List<Integer> result = new java.util.ArrayList<>();
        for (int v : range) result.add(v);
        assertEquals(List.of(0, 1, 2, 3, 4), result);
    }

    @Test
    @DisplayName("RangeGenerator: step=2 skips correctly")
    void testRangeStep2() {
        var range = new LanguagePrimitives.RangeGenerator(0, 10, 2);
        List<Integer> result = new java.util.ArrayList<>();
        for (int v : range) result.add(v);
        assertEquals(List.of(0, 2, 4, 6, 8), result);
    }

    @Test
    @DisplayName("RangeGenerator: step=0 throws AssertionError (invariant)")
    void testRangeStepZeroThrows() {
        assertThrows(AssertionError.class,
            () -> new LanguagePrimitives.RangeGenerator(0, 10, 0));
    }

    // ── Prime Generator ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PrimeGenerator: first 10 primes are correct")
    void testFirstTenPrimes() {
        var gen = new LanguagePrimitives.PrimeGenerator();
        int[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29};
        for (int p : primes) {
            assertTrue(gen.hasNext());
            assertEquals(p, gen.next());
        }
    }

    @Test
    @DisplayName("PrimeGenerator.takeWhile: primes below 30")
    void testPrimesTakeWhile() {
        var result = LanguagePrimitives.PrimeGenerator.takeWhile(
            new LanguagePrimitives.PrimeGenerator(),
            p -> p < 30
        );
        assertEquals(List.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29), result);
    }

    // ── Symbol ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Symbol: two locally created symbols are never equal")
    void testLocalSymbolsUnique() {
        var sym1 = LanguagePrimitives.Symbol.create("foo");
        var sym2 = LanguagePrimitives.Symbol.create("foo");
        assertNotSame(sym1, sym2, "Local symbols must be reference-unique");
        assertNotEquals(sym1, sym2);
    }

    @Test
    @DisplayName("Symbol.forKey: same key returns same global symbol")
    void testGlobalSymbolEquality() {
        var sym1 = LanguagePrimitives.Symbol.forKey("app.event.click");
        var sym2 = LanguagePrimitives.Symbol.forKey("app.event.click");
        assertSame(sym1, sym2, "Global symbols with same key must be identical");
    }

    @Test
    @DisplayName("Symbol.forKey: different keys return different symbols")
    void testGlobalSymbolDifferentKeys() {
        var a = LanguagePrimitives.Symbol.forKey("key.a");
        var b = LanguagePrimitives.Symbol.forKey("key.b");
        assertNotSame(a, b);
    }

    @Test
    @DisplayName("Tagged: ok() and error() use different symbols")
    void testTaggedSymbols() {
        var ok  = LanguagePrimitives.Tagged.ok("success");
        var err = LanguagePrimitives.Tagged.error("fail");
        assertNotSame(ok.tag(), err.tag());
    }

    // ── Goto Equivalents ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GotoEquivalents: matrix search finds correct cell")
    void testMatrixSearch() {
        int[][] matrix = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        int[] result = LanguagePrimitives.GotoEquivalents.findInMatrix(matrix, 5);
        assertArrayEquals(new int[]{1, 1}, result);
    }

    @Test
    @DisplayName("GotoEquivalents: matrix search returns null when not found")
    void testMatrixSearchNotFound() {
        int[][] matrix = {{1, 2}, {3, 4}};
        int[] result = LanguagePrimitives.GotoEquivalents.findInMatrix(matrix, 99);
        assertNull(result, "findInMatrix must return null when target is absent");
    }
}
