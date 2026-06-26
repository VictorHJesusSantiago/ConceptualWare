package com.conceptualware.core.datastructures.tree;

import java.util.*;

/**
 * Concept #4 — Splay Tree (Árvore Splay):
 *   Self-adjusting BST. After every access (search/insert/delete), the accessed
 *   node is moved to the root via the "splay" operation.
 *
 *   Three splay cases (after each, repeat bottom-up until root):
 *     Zig:     node's parent is root → single rotation
 *     Zig-Zig: node and parent are both left or both right children → rotate parent, then node
 *     Zig-Zag: node is left-right or right-left → double rotation on node
 *
 *   Amortized O(log n) per operation (even though worst case is O(n)).
 *   Locality of reference: recently accessed nodes stay near root.
 *
 *   Used by: Windows NT cache manager, GCC-libstdc++ rope, text editors (gap buffer alternative).
 *
 * Concept #5 — Amortized analysis: potential function Φ = Σ log(size(node))
 *              Access lemma: amortized cost ≤ 3(rank(root) - rank(x)) + 1
 */
public class SplayTree<K extends Comparable<K>, V> {

    private Node root;
    private int  size;

    // ── Node ─────────────────────────────────────────────────────────────────

    private class Node {
        K    key;
        V    value;
        Node left, right, parent;

        Node(K key, V value) {
            this.key   = key;
            this.value = value;
        }
    }

    // ── Rotations ─────────────────────────────────────────────────────────────

    private void rotateRight(Node y) {
        Node x = y.left;
        y.left = x.right;
        if (x.right != null) x.right.parent = y;
        x.parent = y.parent;
        if (y.parent == null)             root = x;
        else if (y == y.parent.left)      y.parent.left  = x;
        else                              y.parent.right = x;
        x.right  = y;
        y.parent = x;
    }

    private void rotateLeft(Node x) {
        Node y  = x.right;
        x.right = y.left;
        if (y.left != null) y.left.parent = x;
        y.parent = x.parent;
        if (x.parent == null)            root = y;
        else if (x == x.parent.left)     x.parent.left  = y;
        else                             x.parent.right = y;
        y.left   = x;
        x.parent = y;
    }

    // ── Splay operation ───────────────────────────────────────────────────────

    /**
     * Splay: brings node x to the root via repeated rotations.
     * Three cases implemented: Zig, Zig-Zig, Zig-Zag.
     */
    private void splay(Node x) {
        while (x.parent != null) {
            Node p = x.parent;
            Node g = p.parent;

            if (g == null) {
                // Zig case: parent is root → single rotation
                if (x == p.left) rotateRight(p);
                else             rotateLeft(p);
            } else if (x == p.left && p == g.left) {
                // Zig-Zig (left-left): rotate grandparent first, then parent
                rotateRight(g);
                rotateRight(p);
            } else if (x == p.right && p == g.right) {
                // Zig-Zig (right-right)
                rotateLeft(g);
                rotateLeft(p);
            } else if (x == p.right && p == g.left) {
                // Zig-Zag (left-right): rotate parent, then grandparent
                rotateLeft(p);
                rotateRight(g);
            } else {
                // Zig-Zag (right-left)
                rotateRight(p);
                rotateLeft(g);
            }
        }
    }

    // ── Insertion ─────────────────────────────────────────────────────────────

    public void insert(K key, V value) {
        if (root == null) {
            root = new Node(key, value);
            size++;
            return;
        }

        Node cur = root, parent = null;
        while (cur != null) {
            parent = cur;
            int cmp = key.compareTo(cur.key);
            if      (cmp < 0) cur = cur.left;
            else if (cmp > 0) cur = cur.right;
            else { cur.value = value; splay(cur); return; } // update
        }

        Node n  = new Node(key, value);
        n.parent = parent;
        int cmp  = key.compareTo(parent.key);
        if (cmp < 0) parent.left  = n;
        else         parent.right = n;

        splay(n); // splay new node to root
        size++;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public Optional<V> get(K key) {
        Node n = findAndSplay(key);
        if (n != null && n.key.compareTo(key) == 0) return Optional.of(n.value);
        return Optional.empty();
    }

    public boolean contains(K key) { return get(key).isPresent(); }

    private Node findAndSplay(K key) {
        Node cur = root, last = null;
        while (cur != null) {
            last = cur;
            int cmp = key.compareTo(cur.key);
            if      (cmp < 0) cur = cur.left;
            else if (cmp > 0) cur = cur.right;
            else              break;
        }
        if (last != null) splay(last);
        return root;
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    public boolean delete(K key) {
        findAndSplay(key);
        if (root == null || root.key.compareTo(key) != 0) return false;

        // Split into left and right subtrees
        Node left  = root.left;
        Node right = root.right;
        if (left  != null) left.parent  = null;
        if (right != null) right.parent = null;

        if (left == null) {
            root = right;
        } else {
            // Find max of left subtree, splay it to root of left, then attach right
            root = left;
            Node maxLeft = left;
            while (maxLeft.right != null) maxLeft = maxLeft.right;
            splay(maxLeft);
            root.right = right;
            if (right != null) right.parent = root;
        }
        size--;
        return true;
    }

    // ── In-order traversal ────────────────────────────────────────────────────

    public List<K> inOrder() {
        List<K> result = new ArrayList<>();
        inOrder(root, result);
        return result;
    }

    private void inOrder(Node n, List<K> result) {
        if (n == null) return;
        inOrder(n.left, result);
        result.add(n.key);
        inOrder(n.right, result);
    }

    /**
     * Amortized analysis demonstration:
     *   Define potential Φ = Σ rank(node) = Σ floor(log2(size(node)))
     *   Access lemma: amortized cost of splay ≤ 3(rank(root) - rank(x)) + 1
     *   This proves O(log n) amortized even with O(n) worst case.
     */
    public record AmortizedAnalysis(int totalOperations, long totalActualCost, double amortizedCostPerOp) {
        public static AmortizedAnalysis measure(SplayTree<?, ?> tree) {
            // Structural demonstration — actual measurement would require instrumented runs
            return new AmortizedAnalysis(0, 0L, Math.log(tree.size + 1) / Math.log(2));
        }
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
}
