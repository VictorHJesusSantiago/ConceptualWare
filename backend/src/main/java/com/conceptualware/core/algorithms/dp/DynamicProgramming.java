package com.conceptualware.core.algorithms.dp;

import java.util.*;

/**
 * Concept #5 — Programação Dinâmica (DP):
 *   Memoização, Tabulação, Problema da Mochila (Knapsack),
 *   LCS (Longest Common Subsequence), Coin Change, Edit Distance,
 *   Longest Increasing Subsequence, Matrix Chain Multiplication
 *
 * Concept #5 — Backtracking, Branch and Bound, Recursão de cauda
 * Concept #8 — FP: Memoização como técnica funcional
 */
public class DynamicProgramming {

    // ── Fibonacci — Bottom-up Tabulation vs Memoization ──────────────────────

    /** Fibonacci via tabulação (bottom-up DP). O(n) time, O(1) space. */
    public static long fibTabulation(int n) {
        if (n <= 1) return n;
        long prev2 = 0, prev1 = 1;
        for (int i = 2; i <= n; i++) {
            long curr = prev1 + prev2;
            prev2 = prev1; prev1 = curr;
        }
        return prev1;
    }

    /** Fibonacci via memoização (top-down DP). O(n) time and space. */
    private final Map<Integer, Long> memo = new HashMap<>();
    public long fibMemo(int n) {
        if (n <= 1) return n;
        return memo.computeIfAbsent(n, k -> fibMemo(k - 1) + fibMemo(k - 2));
    }

    // ── 0/1 Knapsack — O(n*W) ────────────────────────────────────────────────

    /** Classic 0/1 Knapsack: maximize value with weight ≤ capacity. */
    public static int knapsack01(int[] weights, int[] values, int capacity) {
        int n = weights.length;
        int[][] dp = new int[n + 1][capacity + 1];
        for (int i = 1; i <= n; i++) {
            for (int w = 0; w <= capacity; w++) {
                dp[i][w] = dp[i-1][w];
                if (weights[i-1] <= w)
                    dp[i][w] = Math.max(dp[i][w], dp[i-1][w - weights[i-1]] + values[i-1]);
            }
        }
        return dp[n][capacity];
    }

    /** Unbounded Knapsack: each item can be used unlimited times. */
    public static int knapsackUnbounded(int[] weights, int[] values, int capacity) {
        int[] dp = new int[capacity + 1];
        for (int w = 1; w <= capacity; w++) {
            for (int i = 0; i < weights.length; i++) {
                if (weights[i] <= w) dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
            }
        }
        return dp[capacity];
    }

    // ── Longest Common Subsequence — O(m*n) ──────────────────────────────────

    public static int lcs(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = a.charAt(i-1) == b.charAt(j-1)
                    ? dp[i-1][j-1] + 1
                    : Math.max(dp[i-1][j], dp[i][j-1]);
        return dp[m][n];
    }

    /** Reconstruct the actual LCS string. */
    public static String lcsString(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = a.charAt(i-1) == b.charAt(j-1)
                    ? dp[i-1][j-1] + 1
                    : Math.max(dp[i-1][j], dp[i][j-1]);

        StringBuilder sb = new StringBuilder();
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (a.charAt(i-1) == b.charAt(j-1)) { sb.insert(0, a.charAt(i-1)); i--; j--; }
            else if (dp[i-1][j] > dp[i][j-1]) i--;
            else j--;
        }
        return sb.toString();
    }

    // ── Edit Distance (Levenshtein) — O(m*n) ─────────────────────────────────

    public static int editDistance(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = a.charAt(i-1) == b.charAt(j-1)
                    ? dp[i-1][j-1]
                    : 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
        return dp[m][n];
    }

    // ── Coin Change — O(amount * coins) ──────────────────────────────────────

    /** Minimum number of coins to make amount. */
    public static int coinChange(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, amount + 1);
        dp[0] = 0;
        for (int i = 1; i <= amount; i++)
            for (int coin : coins)
                if (coin <= i) dp[i] = Math.min(dp[i], dp[i - coin] + 1);
        return dp[amount] > amount ? -1 : dp[amount];
    }

    /** Count distinct ways to make amount. */
    public static int coinChangeWays(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        dp[0] = 1;
        for (int coin : coins)
            for (int i = coin; i <= amount; i++)
                dp[i] += dp[i - coin];
        return dp[amount];
    }

    // ── Longest Increasing Subsequence — O(n log n) ───────────────────────────

    public static int lis(int[] arr) {
        List<Integer> tails = new ArrayList<>();
        for (int x : arr) {
            int lo = 0, hi = tails.size();
            while (lo < hi) {
                int mid = (lo + hi) / 2;
                if (tails.get(mid) < x) lo = mid + 1; else hi = mid;
            }
            if (lo == tails.size()) tails.add(x);
            else tails.set(lo, x);
        }
        return tails.size();
    }

    // ── Longest Palindromic Subsequence ───────────────────────────────────────

    public static int longestPalindromicSubsequence(String s) {
        return lcs(s, new StringBuilder(s).reverse().toString());
    }

    // ── Matrix Chain Multiplication — O(n³) ──────────────────────────────────

    public static int matrixChainMultiplication(int[] dims) {
        int n = dims.length - 1;
        int[][] dp = new int[n][n];
        for (int len = 2; len <= n; len++) {
            for (int i = 0; i <= n - len; i++) {
                int j = i + len - 1;
                dp[i][j] = Integer.MAX_VALUE;
                for (int k = i; k < j; k++) {
                    int cost = dp[i][k] + dp[k+1][j] + dims[i] * dims[k+1] * dims[j+1];
                    dp[i][j] = Math.min(dp[i][j], cost);
                }
            }
        }
        return dp[0][n - 1];
    }

    // ── Backtracking — N-Queens ────────────────────────────────────────────────

    public static List<List<String>> solveNQueens(int n) {
        List<List<String>> solutions = new ArrayList<>();
        int[] queens = new int[n];
        Arrays.fill(queens, -1);
        solveNQueensHelper(queens, 0, n, solutions);
        return solutions;
    }

    private static void solveNQueensHelper(int[] queens, int row, int n,
                                            List<List<String>> solutions) {
        if (row == n) {
            solutions.add(buildBoard(queens, n));
            return;
        }
        for (int col = 0; col < n; col++) {
            if (isValidQueenPlacement(queens, row, col)) {
                queens[row] = col;
                solveNQueensHelper(queens, row + 1, n, solutions);
                queens[row] = -1; // backtrack
            }
        }
    }

    private static boolean isValidQueenPlacement(int[] queens, int row, int col) {
        for (int r = 0; r < row; r++) {
            if (queens[r] == col || Math.abs(queens[r] - col) == Math.abs(r - row))
                return false;
        }
        return true;
    }

    private static List<String> buildBoard(int[] queens, int n) {
        List<String> board = new ArrayList<>();
        for (int row = 0; row < n; row++) {
            char[] line = new char[n];
            Arrays.fill(line, '.');
            line[queens[row]] = 'Q';
            board.add(new String(line));
        }
        return board;
    }

    // ── Monte Carlo — Probabilistic Algorithm ─────────────────────────────────

    public static double estimatePi(int samples) {
        int inside = 0;
        Random rand = new Random(42);
        for (int i = 0; i < samples; i++) {
            double x = rand.nextDouble(), y = rand.nextDouble();
            if (x * x + y * y <= 1) inside++;
        }
        return 4.0 * inside / samples;
    }
}
