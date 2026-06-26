package com.conceptualware.core.algorithms;

import com.conceptualware.core.algorithms.dp.DynamicProgramming;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — Testes: TDD, AAA, Property-based testing patterns
 * Concept #5  — DP: Fibonacci, Knapsack, LCS, Edit Distance, Coin Change, N-Queens
 */
@DisplayName("Dynamic Programming — Unit Tests")
class DynamicProgrammingTest {

    // ── Fibonacci ──────────────────────────────────────────────────────────────

    @Test @DisplayName("fib(0) = 0") void fib0() { assertThat(DynamicProgramming.fibTabulation(0)).isEqualTo(0); }
    @Test @DisplayName("fib(1) = 1") void fib1() { assertThat(DynamicProgramming.fibTabulation(1)).isEqualTo(1); }
    @Test @DisplayName("fib(10) = 55") void fib10() { assertThat(DynamicProgramming.fibTabulation(10)).isEqualTo(55); }
    @Test @DisplayName("fib(50) = 12586269025") void fib50() {
        assertThat(DynamicProgramming.fibTabulation(50)).isEqualTo(12_586_269_025L);
    }

    // ── Knapsack ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Knapsack — classic example")
    void knapsackClassic() {
        int[] weights = {1, 3, 4, 5};
        int[] values  = {1, 4, 5, 7};
        assertThat(DynamicProgramming.knapsack01(weights, values, 7)).isEqualTo(9);
    }

    @Test
    @DisplayName("Knapsack — empty items returns 0")
    void knapsackEmpty() {
        assertThat(DynamicProgramming.knapsack01(new int[]{}, new int[]{}, 10)).isEqualTo(0);
    }

    // ── LCS ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LCS of 'ABCBDAB' and 'BDCAB' is 4")
    void lcsClassic() {
        assertThat(DynamicProgramming.lcs("ABCBDAB", "BDCAB")).isEqualTo(4);
    }

    @Test
    @DisplayName("LCS of identical strings is string length")
    void lcsIdentical() {
        String s = "ALGORITHM";
        assertThat(DynamicProgramming.lcs(s, s)).isEqualTo(s.length());
    }

    @Test
    @DisplayName("LCS of empty string is 0")
    void lcsEmpty() {
        assertThat(DynamicProgramming.lcs("", "ABC")).isEqualTo(0);
    }

    // ── Edit Distance ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Edit distance between same strings is 0")
    void editDistanceSame() {
        assertThat(DynamicProgramming.editDistance("kitten", "kitten")).isEqualTo(0);
    }

    @Test
    @DisplayName("Edit distance kitten → sitting is 3")
    void editDistanceKittenSitting() {
        assertThat(DynamicProgramming.editDistance("kitten", "sitting")).isEqualTo(3);
    }

    @Test
    @DisplayName("Edit distance to/from empty string is string length")
    void editDistanceFromEmpty() {
        assertThat(DynamicProgramming.editDistance("", "abc")).isEqualTo(3);
        assertThat(DynamicProgramming.editDistance("abc", "")).isEqualTo(3);
    }

    // ── Coin Change ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Coin change [1,5,10,25] for 30 = 2 coins (25+5)")
    void coinChange30() {
        assertThat(DynamicProgramming.coinChange(new int[]{1, 5, 10, 25}, 30)).isEqualTo(2);
    }

    @Test
    @DisplayName("Coin change — impossible returns -1")
    void coinChangeImpossible() {
        assertThat(DynamicProgramming.coinChange(new int[]{2}, 3)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Coin change for 0 = 0 coins")
    void coinChangeZero() {
        assertThat(DynamicProgramming.coinChange(new int[]{1, 5}, 0)).isEqualTo(0);
    }

    // ── N-Queens ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("4-Queens has 2 solutions")
    void nQueens4() {
        assertThat(DynamicProgramming.solveNQueens(4)).hasSize(2);
    }

    @Test
    @DisplayName("8-Queens has 92 solutions")
    void nQueens8() {
        assertThat(DynamicProgramming.solveNQueens(8)).hasSize(92);
    }

    // ── LIS ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LIS of [10,9,2,5,3,7,101,18] is 4")
    void lisTest() {
        assertThat(DynamicProgramming.lis(new int[]{10,9,2,5,3,7,101,18})).isEqualTo(4);
    }

    // ── Monte Carlo Pi ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Monte Carlo Pi estimate should be within 0.1 of actual π")
    void monteCarloShouldApproximatePi() {
        double estimate = DynamicProgramming.estimatePi(1_000_000);
        assertThat(estimate).isCloseTo(Math.PI, within(0.05));
    }
}
