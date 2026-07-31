package com.conceptualware.core.datastructures.tree;

public class FenwickTree {

    private final long[] tree;
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

    public void update(int i, long delta) {
        for (int idx = i + 1; idx <= n; idx += idx & (-idx)) {
            tree[idx] += delta;
        }
    }

    public long prefixSum(int i) {
        long sum = 0;
        for (int idx = i + 1; idx > 0; idx -= idx & (-idx)) {
            sum += tree[idx];
        }
        return sum;
    }

    public long rangeSum(int l, int r) {
        if (l == 0) return prefixSum(r);
        return prefixSum(r) - prefixSum(l - 1);
    }

    public int size() {
        return n;
    }
}
