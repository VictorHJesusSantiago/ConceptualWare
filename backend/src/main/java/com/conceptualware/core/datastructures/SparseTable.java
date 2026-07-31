package com.conceptualware.core.datastructures;

public class SparseTable {

    private final int[][] table;
    private final int[]   log2;
    private final int     n;

    public SparseTable(int[] arr) {
        n     = arr.length;
        int k = n > 1 ? (int)(Math.log(n) / Math.log(2)) + 1 : 1;

        table = new int[n][k];
        log2  = new int[n + 1];

        log2[1] = 0;
        for (int i = 2; i <= n; i++) log2[i] = log2[i / 2] + 1;

        for (int i = 0; i < n; i++) table[i][0] = arr[i];

        for (int j = 1; (1 << j) <= n; j++) {
            for (int i = 0; i + (1 << j) - 1 < n; i++) {
                table[i][j] = Math.min(table[i][j - 1], table[i + (1 << (j - 1))][j - 1]);
            }
        }
    }

    public int queryMin(int l, int r) {
        if (l < 0 || r >= n || l > r) throw new IllegalArgumentException("Invalid range [" + l + "," + r + "]");
        int k = log2[r - l + 1];
        return Math.min(table[l][k], table[r - (1 << k) + 1][k]);
    }

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
