package com.conceptualware.core.datastructures;

/**
 * Concept #4 — Sparse Table (Tabela Esparsa):
 *   Static data structure for Range Minimum/Maximum Query (RMQ).
 *
 *   Build:  O(n log n) time and space — precomputes answers for all ranges [i, i+2^j-1]
 *   Query:  O(1) using the "overlapping intervals" trick:
 *             min(i, j) = min(table[i][k], table[j-2^k+1][k])
 *             where k = floor(log2(j-i+1))
 *
 *   This is STRICTLY better than segment tree (O(log n) per query) for
 *   immutable arrays where queries vastly outnumber updates.
 *
 *   Used by: LCA (Lowest Common Ancestor) algorithms, string suffix arrays,
 *            offline range queries in competitive programming.
 *
 * Concept #5 — Dynamic programming on intervals, logarithmic decomposition
 */
public class SparseTable {

    private final int[][] table;  // table[i][j] = min of arr[i..i+2^j-1]
    private final int[]   log2;   // precomputed floor(log2(n)) for each n
    private final int     n;

    /**
     * Builds the sparse table for Range Minimum Query on the given array.
     * @param arr input array (must not be modified after construction)
     */
    public SparseTable(int[] arr) {
        n     = arr.length;
        int k = n > 1 ? (int)(Math.log(n) / Math.log(2)) + 1 : 1;

        table = new int[n][k];
        log2  = new int[n + 1];

        // Precompute log2 table (faster than calling Math.log in queries)
        log2[1] = 0;
        for (int i = 2; i <= n; i++) log2[i] = log2[i / 2] + 1;

        // Base case: ranges of length 1
        for (int i = 0; i < n; i++) table[i][0] = arr[i];

        // Fill table: range [i, i+2^j-1] = min of [i, i+2^(j-1)-1] and [i+2^(j-1), i+2^j-1]
        for (int j = 1; (1 << j) <= n; j++) {
            for (int i = 0; i + (1 << j) - 1 < n; i++) {
                table[i][j] = Math.min(table[i][j - 1], table[i + (1 << (j - 1))][j - 1]);
            }
        }
    }

    /**
     * Range Minimum Query: returns min of arr[l..r] in O(1).
     * Uses overlapping intervals — valid because min is idempotent (min(x,x)=x).
     */
    public int queryMin(int l, int r) {
        if (l < 0 || r >= n || l > r) throw new IllegalArgumentException("Invalid range [" + l + "," + r + "]");
        int k = log2[r - l + 1];
        return Math.min(table[l][k], table[r - (1 << k) + 1][k]);
    }

    /**
     * Range Maximum Query using the same overlapping trick but with a separate table.
     * Builds on construction — demonstrates same structure works for max.
     */
    public static class RangeMaxTable {
        private final int[][] table;
        private final int[]   log2;
        private final int     n;

        public RangeMaxTable(int[] arr) {
            n     = arr.length;
            int k = n > 1 ? (int)(Math.log(n) / Math.log(2)) + 1 : 1;
            table = new int[n][k];
            log2  = new int[n + 1];

            log2[1] = 0;
            for (int i = 2; i <= n; i++) log2[i] = log2[i / 2] + 1;
            for (int i = 0; i < n; i++) table[i][0] = arr[i];

            for (int j = 1; (1 << j) <= n; j++)
                for (int i = 0; i + (1 << j) - 1 < n; i++)
                    table[i][j] = Math.max(table[i][j - 1], table[i + (1 << (j - 1))][j - 1]);
        }

        public int queryMax(int l, int r) {
            if (l < 0 || r >= n || l > r) throw new IllegalArgumentException("Invalid range");
            int k = log2[r - l + 1];
            return Math.max(table[l][k], table[r - (1 << k) + 1][k]);
        }
    }

    /**
     * Demonstrates the Sparse Table complexity advantage:
     *   Build once O(n log n), then answer unlimited queries in O(1).
     *   For q queries: O(n log n + q) vs Segment Tree O(n + q log n).
     *   Break-even: when q >> n, Sparse Table wins.
     */
    public record ComplexityComparison(
        String dataStructure,
        String buildTime,
        String queryTime,
        String updateTime,
        String bestFor
    ) {
        public static ComplexityComparison sparseTable() {
            return new ComplexityComparison("Sparse Table", "O(n log n)", "O(1)", "Not supported (static)", "Many queries, no updates");
        }
        public static ComplexityComparison segmentTree() {
            return new ComplexityComparison("Segment Tree", "O(n)", "O(log n)", "O(log n)", "Mix of queries and updates");
        }
        public static ComplexityComparison naiveArray() {
            return new ComplexityComparison("Naive Scan", "O(1)", "O(n)", "O(1)", "Very few queries");
        }
    }

    public int size() { return n; }
}
