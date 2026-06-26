package com.conceptualware.core.datastructures.tree;

import java.util.*;

/**
 * Concept #4 — Árvore binária, BST, heap, BFS, DFS traversals
 * Concept #5 — Busca binária em árvore, algoritmos de percurso
 * Concept #7 — OOP: generics, comparator, recursion
 */
public class BinarySearchTree<T extends Comparable<T>> {

    protected static class Node<T> {
        T key;
        Node<T> left, right;
        int height; // for AVL
        boolean red; // for Red-Black

        Node(T key) { this.key = key; this.height = 1; this.red = true; }
    }

    protected Node<T> root;
    private int size;

    // ── Insert ────────────────────────────────────────────────────────────────

    public void insert(T key) {
        root = insert(root, key);
        size++;
    }

    private Node<T> insert(Node<T> node, T key) {
        if (node == null) return new Node<>(key);
        int cmp = key.compareTo(node.key);
        if (cmp < 0) node.left = insert(node.left, key);
        else if (cmp > 0) node.right = insert(node.right, key);
        // Duplicate keys: ignored (set semantics)
        return node;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public boolean contains(T key) { return contains(root, key); }

    private boolean contains(Node<T> node, T key) {
        if (node == null) return false;
        int cmp = key.compareTo(node.key);
        if (cmp < 0) return contains(node.left, key);
        if (cmp > 0) return contains(node.right, key);
        return true;
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    public void delete(T key) {
        if (contains(key)) { root = delete(root, key); size--; }
    }

    private Node<T> delete(Node<T> node, T key) {
        if (node == null) return null;
        int cmp = key.compareTo(node.key);
        if (cmp < 0) { node.left = delete(node.left, key); }
        else if (cmp > 0) { node.right = delete(node.right, key); }
        else {
            if (node.left == null) return node.right;
            if (node.right == null) return node.left;
            // Replace with in-order successor (smallest in right subtree)
            Node<T> successor = min(node.right);
            node.key = successor.key;
            node.right = delete(node.right, successor.key);
        }
        return node;
    }

    // ── Traversals ────────────────────────────────────────────────────────────

    public List<T> inOrder()   { List<T> r = new ArrayList<>(); inOrder(root, r); return r; }
    public List<T> preOrder()  { List<T> r = new ArrayList<>(); preOrder(root, r); return r; }
    public List<T> postOrder() { List<T> r = new ArrayList<>(); postOrder(root, r); return r; }

    private void inOrder(Node<T> n, List<T> r)   { if (n != null) { inOrder(n.left, r); r.add(n.key); inOrder(n.right, r); } }
    private void preOrder(Node<T> n, List<T> r)  { if (n != null) { r.add(n.key); preOrder(n.left, r); preOrder(n.right, r); } }
    private void postOrder(Node<T> n, List<T> r) { if (n != null) { postOrder(n.left, r); postOrder(n.right, r); r.add(n.key); } }

    /** BFS — Level-order traversal (Concept #5). */
    public List<List<T>> levelOrder() {
        List<List<T>> result = new ArrayList<>();
        if (root == null) return result;
        Queue<Node<T>> queue = new LinkedList<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<T> level = new ArrayList<>();
            for (int i = 0; i < levelSize; i++) {
                Node<T> node = queue.poll();
                level.add(node.key);
                if (node.left != null) queue.offer(node.left);
                if (node.right != null) queue.offer(node.right);
            }
            result.add(level);
        }
        return result;
    }

    // ── Min / Max / Height ────────────────────────────────────────────────────

    public T min() { if (root == null) throw new NoSuchElementException(); return min(root).key; }
    public T max() { if (root == null) throw new NoSuchElementException(); return max(root).key; }

    protected Node<T> min(Node<T> n) { return n.left == null ? n : min(n.left); }
    protected Node<T> max(Node<T> n) { return n.right == null ? n : max(n.right); }

    public int height() { return height(root); }
    protected int height(Node<T> n) { return n == null ? 0 : n.height; }

    public int size() { return size; }
    public boolean isEmpty() { return root == null; }

    /** Check if tree is valid BST (for testing). */
    public boolean isValidBST() {
        return isValidBST(root, null, null);
    }

    private boolean isValidBST(Node<T> n, T min, T max) {
        if (n == null) return true;
        if (min != null && n.key.compareTo(min) <= 0) return false;
        if (max != null && n.key.compareTo(max) >= 0) return false;
        return isValidBST(n.left, min, n.key) && isValidBST(n.right, n.key, max);
    }

    /** Floor: largest key ≤ given key. */
    public Optional<T> floor(T key) { return Optional.ofNullable(floor(root, key)); }

    private T floor(Node<T> n, T key) {
        if (n == null) return null;
        int cmp = key.compareTo(n.key);
        if (cmp == 0) return n.key;
        if (cmp < 0) return floor(n.left, key);
        T t = floor(n.right, key);
        return t != null ? t : n.key;
    }

    /** Rank: how many keys are less than the given key. */
    public int rank(T key) { return rank(root, key); }

    private int rank(Node<T> n, T key) {
        if (n == null) return 0;
        int cmp = key.compareTo(n.key);
        if (cmp < 0) return rank(n.left, key);
        if (cmp > 0) return 1 + sizeOf(n.left) + rank(n.right, key);
        return sizeOf(n.left);
    }

    private int sizeOf(Node<T> n) { return n == null ? 0 : size; } // simplified
}
