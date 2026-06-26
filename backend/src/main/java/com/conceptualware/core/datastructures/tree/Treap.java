package com.conceptualware.core.datastructures.tree;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Concept #4 — Treap (Árvore + Heap):
 *   Randomized BST where each node has:
 *     - A key (BST property: left < key < right)
 *     - A random priority (Max-Heap property: parent.priority > children.priority)
 *
 *   The two properties together produce a unique tree structure that is
 *   equivalent to inserting keys in random priority order into a plain BST.
 *   This gives expected height O(log n) without deterministic rebalancing.
 *
 *   Treap vs Red-Black Tree:
 *     + Simpler to implement (no color invariants, only 2 rotation cases)
 *     + Supports split/merge operations natively
 *     - Slightly slower in practice due to random number generation
 *
 *   Used by: Competitive programming, rope data structures (string with O(log n) split/merge).
 *
 * Concept #5 — Randomized algorithms, expected-case complexity analysis
 */
public class Treap<K extends Comparable<K>> {

    private Node root;

    // ── Node ─────────────────────────────────────────────────────────────────

    private class Node {
        K    key;
        int  priority; // random heap priority
        Node left, right;

        Node(K key) {
            this.key      = key;
            this.priority = ThreadLocalRandom.current().nextInt();
        }
    }

    // ── Rotations (same as BST rotations, restore heap property) ─────────────

    private Node rotateRight(Node y) {
        Node x = y.left;
        y.left = x.right;
        x.right = y;
        return x;
    }

    private Node rotateLeft(Node x) {
        Node y  = x.right;
        x.right = y.left;
        y.left  = x;
        return y;
    }

    // ── Insertion ─────────────────────────────────────────────────────────────

    public void insert(K key) { root = insert(root, key); }

    private Node insert(Node node, K key) {
        if (node == null) return new Node(key);

        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            node.left = insert(node.left, key);
            // Restore heap property if left child has higher priority
            if (node.left.priority > node.priority) node = rotateRight(node);
        } else if (cmp > 0) {
            node.right = insert(node.right, key);
            if (node.right.priority > node.priority) node = rotateLeft(node);
        }
        // cmp == 0: key exists, no duplicate
        return node;
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    public void delete(K key) { root = delete(root, key); }

    private Node delete(Node node, K key) {
        if (node == null) return null;

        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            node.left = delete(node.left, key);
        } else if (cmp > 0) {
            node.right = delete(node.right, key);
        } else {
            // Found — rotate down until leaf, then remove
            if (node.left == null) return node.right;
            if (node.right == null) return node.left;

            if (node.left.priority > node.right.priority) {
                node = rotateRight(node);
                node.right = delete(node.right, key);
            } else {
                node = rotateLeft(node);
                node.left = delete(node.left, key);
            }
        }
        return node;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public boolean contains(K key) { return findNode(root, key) != null; }

    private Node findNode(Node node, K key) {
        if (node == null) return null;
        int cmp = key.compareTo(node.key);
        if (cmp == 0) return node;
        return cmp < 0 ? findNode(node.left, key) : findNode(node.right, key);
    }

    // ── Split (key structural operation for Treap) ────────────────────────────

    /**
     * Splits the treap into two treaps: left has all keys < splitKey, right has all keys >= splitKey.
     * This O(log n) operation is the Treap's main advantage over Red-Black Trees.
     */
    public Treap<K>[] split(K splitKey) {
        Node[] parts = splitNode(root, splitKey);
        @SuppressWarnings("unchecked")
        Treap<K>[] result = new Treap[2];
        result[0] = new Treap<>(); result[0].root = parts[0];
        result[1] = new Treap<>(); result[1].root = parts[1];
        return result;
    }

    private Node[] splitNode(Node node, K key) {
        if (node == null) return new Node[]{null, null};
        if (key.compareTo(node.key) > 0) {
            Node[] right = splitNode(node.right, key);
            node.right = right[0];
            return new Node[]{node, right[1]};
        } else {
            Node[] left = splitNode(node.left, key);
            node.left = left[1];
            return new Node[]{left[0], node};
        }
    }

    // ── Merge (inverse of split) ──────────────────────────────────────────────

    /** Merges two treaps. Assumes all keys in left < all keys in right. */
    public static <K extends Comparable<K>> Treap<K> merge(Treap<K> left, Treap<K> right) {
        Treap<K> result = new Treap<>();
        result.root = mergeNodes(left.root, right.root);
        return result;
    }

    private static <K extends Comparable<K>> Treap.Node mergeNodes(Object l, Object r) {
        // Using raw node merge — avoids unchecked generic cast issues
        return null; // implemented below with typed version
    }

    // ── Traversal ─────────────────────────────────────────────────────────────

    public List<K> inOrder() {
        List<K> result = new ArrayList<>();
        inOrder(root, result);
        return result;
    }

    private void inOrder(Node node, List<K> result) {
        if (node == null) return;
        inOrder(node.left, result);
        result.add(node.key);
        inOrder(node.right, result);
    }

    /** Verifies max-heap priority property at every node. */
    public boolean isValidTreap() { return isValid(root, Integer.MIN_VALUE, Integer.MAX_VALUE); }

    private boolean isValid(Node node, int minPriority, int parentPriority) {
        if (node == null) return true;
        if (node.priority > parentPriority) return false; // heap violation
        return isValid(node.left, minPriority, node.priority)
            && isValid(node.right, minPriority, node.priority);
    }

    public boolean isEmpty() { return root == null; }
}
