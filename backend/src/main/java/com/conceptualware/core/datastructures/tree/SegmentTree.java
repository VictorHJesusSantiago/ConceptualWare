package com.conceptualware.core.datastructures.tree;

public class SegmentTree {

    private final int[] tree;
    private final int n;
    private final Operation op;

    public enum Operation { SUM, MIN, MAX }

    public SegmentTree(int[] data, Operation op) {
        this.n = data.length;
        this.op = op;
        this.tree = new int[4 * n];
        build(data, 0, 0, n - 1);
    }

    private void build(int[] data, int node, int start, int end) {
        if (start == end) {
            tree[node] = data[start];
        } else {
            int mid = (start + end) / 2;
            build(data, 2*node+1, start, mid);
            build(data, 2*node+2, mid+1, end);
            tree[node] = combine(tree[2*node+1], tree[2*node+2]);
        }
    }

    public void update(int idx, int val) { update(0, 0, n - 1, idx, val); }

    private void update(int node, int start, int end, int idx, int val) {
        if (start == end) { tree[node] = val; return; }
        int mid = (start + end) / 2;
        if (idx <= mid) update(2*node+1, start, mid, idx, val);
        else            update(2*node+2, mid+1, end, idx, val);
        tree[node] = combine(tree[2*node+1], tree[2*node+2]);
    }

    public int query(int l, int r) { return query(0, 0, n - 1, l, r); }

    private int query(int node, int start, int end, int l, int r) {
        if (r < start || end < l) return identity();
        if (l <= start && end <= r) return tree[node];
        int mid = (start + end) / 2;
        return combine(query(2*node+1, start, mid, l, r),
                       query(2*node+2, mid+1, end, l, r));
    }

    private int combine(int a, int b) {
        return switch (op) {
            case SUM -> a + b;
            case MIN -> Math.min(a, b);
            case MAX -> Math.max(a, b);
        };
    }

    private int identity() {
        return switch (op) {
            case SUM -> 0;
            case MIN -> Integer.MAX_VALUE;
            case MAX -> Integer.MIN_VALUE;
        };
    }

    public static class FenwickTree {
        private final int[] bit;
        private final int n;

        public FenwickTree(int n) {
            this.n = n;
            this.bit = new int[n + 1];
        }

        public FenwickTree(int[] data) {
            this.n = data.length;
            this.bit = new int[n + 1];
            for (int i = 0; i < n; i++) update(i + 1, data[i]);
        }

        public void update(int i, int delta) {
            for (; i <= n; i += i & (-i)) bit[i] += delta;
        }

        public int prefixSum(int i) {
            int sum = 0;
            for (; i > 0; i -= i & (-i)) sum += bit[i];
            return sum;
        }

        public int rangeSum(int l, int r) { return prefixSum(r) - prefixSum(l - 1); }

        public int query(int i) { return rangeSum(i, i); }
    }
}
