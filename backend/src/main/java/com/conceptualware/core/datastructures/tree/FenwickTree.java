package com.conceptualware.core.datastructures.tree;

/**
 * Concept #5 — Fenwick Tree / Binary Indexed Tree (BIT):
 *   Prefix-sum queries e point updates em O(log n), com O(n) de espaço.
 *   Baseado na representação binária dos índices (lowbit = i & -i).
 */
public class FenwickTree {

    private final long[] tree; // 1-indexed
    private final int n;

    public FenwickTree(int size) {
        this.n = size;
        this.tree = new long[size + 1];
    }

    public static FenwickTree fromArray(long[] values) {
        FenwickTree bit = new FenwickTree(values.length);
        for (int i = 0; i < values.length; i++) bit.update(i, values[i]);
        return bit;
    }

    /** Adiciona delta ao índice i (0-indexed). O(log n). */
    public void update(int i, long delta) {
        for (int idx = i + 1; idx <= n; idx += idx & (-idx)) {
            tree[idx] += delta;
        }
    }

    /** Soma prefixo [0, i] (0-indexed, inclusive). O(log n). */
    public long prefixSum(int i) {
        long sum = 0;
        for (int idx = i + 1; idx > 0; idx -= idx & (-idx)) {
            sum += tree[idx];
        }
        return sum;
    }

    /** Soma no intervalo [l, r] (0-indexed, inclusive). O(log n). */
    public long rangeSum(int l, int r) {
        if (l == 0) return prefixSum(r);
        return prefixSum(r) - prefixSum(l - 1);
    }

    public int size() {
        return n;
    }
}
