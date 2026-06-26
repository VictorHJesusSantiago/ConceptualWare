package com.conceptualware.core.datastructures.tree;

import java.util.*;

/**
 * Concept #4 — B-Tree and B+Tree:
 *   B-Tree: multi-way search tree with order t (min degree).
 *     - Every node has at most 2t-1 keys and 2t children.
 *     - Every non-root node has at least t-1 keys.
 *     - All leaves at same depth → perfectly height-balanced.
 *     - O(log n) search/insert/delete. Used by: file systems (NTFS, ext4), databases (InnoDB pages).
 *
 *   B+Tree variant: stores data only in leaf nodes, linked list between leaves.
 *     - Enables efficient range scans (ORDER BY, BETWEEN).
 *     - Used by: PostgreSQL, MySQL InnoDB, SQLite.
 *
 * Both trees are critical for OLTP database index implementations (Concept #11).
 */
public class BTree<K extends Comparable<K>> {

    private final int t; // minimum degree
    private Node root;

    public BTree(int t) {
        this.t    = t;
        this.root = new Node(true);
    }

    // ── Node ─────────────────────────────────────────────────────────────────

    private class Node {
        List<K>    keys     = new ArrayList<>();
        List<Node> children = new ArrayList<>();
        boolean    isLeaf;

        Node(boolean isLeaf) { this.isLeaf = isLeaf; }

        boolean isFull() { return keys.size() == 2 * t - 1; }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public boolean search(K key) { return searchNode(root, key); }

    private boolean searchNode(Node node, K key) {
        int i = 0;
        while (i < node.keys.size() && key.compareTo(node.keys.get(i)) > 0) i++;

        if (i < node.keys.size() && key.compareTo(node.keys.get(i)) == 0) return true;
        if (node.isLeaf) return false;
        return searchNode(node.children.get(i), key);
    }

    // ── Insertion ─────────────────────────────────────────────────────────────

    public void insert(K key) {
        if (root.isFull()) {
            Node newRoot = new Node(false);
            newRoot.children.add(root);
            splitChild(newRoot, 0);
            root = newRoot;
        }
        insertNonFull(root, key);
    }

    private void insertNonFull(Node node, K key) {
        int i = node.keys.size() - 1;

        if (node.isLeaf) {
            node.keys.add(null); // extend by one
            while (i >= 0 && key.compareTo(node.keys.get(i)) < 0) {
                node.keys.set(i + 1, node.keys.get(i));
                i--;
            }
            node.keys.set(i + 1, key);
        } else {
            while (i >= 0 && key.compareTo(node.keys.get(i)) < 0) i--;
            i++;
            if (node.children.get(i).isFull()) {
                splitChild(node, i);
                if (key.compareTo(node.keys.get(i)) > 0) i++;
            }
            insertNonFull(node.children.get(i), key);
        }
    }

    /** Split child[i] of parent — child must be full (2t-1 keys). */
    private void splitChild(Node parent, int i) {
        Node full  = parent.children.get(i);
        Node right = new Node(full.isLeaf);

        // Median key moves up to parent
        parent.keys.add(i, full.keys.get(t - 1));
        parent.children.add(i + 1, right);

        // Right half of keys and children go to the new node
        right.keys.addAll(full.keys.subList(t, full.keys.size()));
        full.keys.subList(t - 1, full.keys.size()).clear();

        if (!full.isLeaf) {
            right.children.addAll(full.children.subList(t, full.children.size()));
            full.children.subList(t, full.children.size()).clear();
        }
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    public void delete(K key) { delete(root, key); }

    private void delete(Node node, K key) {
        int idx = findKey(node, key);

        if (idx < node.keys.size() && key.compareTo(node.keys.get(idx)) == 0) {
            if (node.isLeaf) {
                node.keys.remove(idx);
            } else {
                deleteFromInternal(node, idx);
            }
        } else {
            if (node.isLeaf) return; // key not present
            boolean lastChild = (idx == node.keys.size());
            if (node.children.get(idx).keys.size() < t) fill(node, idx);
            if (lastChild && idx > node.keys.size())
                delete(node.children.get(idx - 1), key);
            else
                delete(node.children.get(idx), key);
        }
    }

    private void deleteFromInternal(Node node, int idx) {
        K key = node.keys.get(idx);
        if (node.children.get(idx).keys.size() >= t) {
            K pred = getPredecessor(node, idx);
            node.keys.set(idx, pred);
            delete(node.children.get(idx), pred);
        } else if (node.children.get(idx + 1).keys.size() >= t) {
            K succ = getSuccessor(node, idx);
            node.keys.set(idx, succ);
            delete(node.children.get(idx + 1), succ);
        } else {
            merge(node, idx);
            delete(node.children.get(idx), key);
        }
    }

    private int findKey(Node node, K key) {
        int idx = 0;
        while (idx < node.keys.size() && key.compareTo(node.keys.get(idx)) > 0) idx++;
        return idx;
    }

    private K getPredecessor(Node node, int idx) {
        Node cur = node.children.get(idx);
        while (!cur.isLeaf) cur = cur.children.get(cur.keys.size());
        return cur.keys.get(cur.keys.size() - 1);
    }

    private K getSuccessor(Node node, int idx) {
        Node cur = node.children.get(idx + 1);
        while (!cur.isLeaf) cur = cur.children.get(0);
        return cur.keys.get(0);
    }

    private void fill(Node node, int idx) {
        if (idx != 0 && node.children.get(idx - 1).keys.size() >= t)
            borrowFromPrev(node, idx);
        else if (idx != node.keys.size() && node.children.get(idx + 1).keys.size() >= t)
            borrowFromNext(node, idx);
        else {
            if (idx != node.keys.size()) merge(node, idx);
            else                         merge(node, idx - 1);
        }
    }

    private void borrowFromPrev(Node node, int idx) {
        Node child  = node.children.get(idx);
        Node sibling = node.children.get(idx - 1);
        child.keys.add(0, node.keys.get(idx - 1));
        node.keys.set(idx - 1, sibling.keys.remove(sibling.keys.size() - 1));
        if (!sibling.isLeaf)
            child.children.add(0, sibling.children.remove(sibling.children.size() - 1));
    }

    private void borrowFromNext(Node node, int idx) {
        Node child   = node.children.get(idx);
        Node sibling = node.children.get(idx + 1);
        child.keys.add(node.keys.get(idx));
        node.keys.set(idx, sibling.keys.remove(0));
        if (!sibling.isLeaf)
            child.children.add(sibling.children.remove(0));
    }

    private void merge(Node node, int idx) {
        Node left  = node.children.get(idx);
        Node right = node.children.get(idx + 1);
        left.keys.add(node.keys.remove(idx));
        left.keys.addAll(right.keys);
        if (!right.isLeaf) left.children.addAll(right.children);
        node.children.remove(idx + 1);
        if (root.keys.isEmpty()) root = root.children.get(0); // shrink tree height
    }

    // ── In-order traversal ───────────────────────────────────────────────────

    public List<K> inOrder() {
        List<K> result = new ArrayList<>();
        inOrderHelper(root, result);
        return result;
    }

    private void inOrderHelper(Node node, List<K> result) {
        for (int i = 0; i < node.keys.size(); i++) {
            if (!node.isLeaf) inOrderHelper(node.children.get(i), result);
            result.add(node.keys.get(i));
        }
        if (!node.isLeaf) inOrderHelper(node.children.get(node.keys.size()), result);
    }

    // ── B+Tree simulation ─────────────────────────────────────────────────────

    /**
     * BPlusTree inner class — all values at leaves, leaves linked for range scan.
     * Demonstrates the key structural difference from B-Tree.
     */
    public static class BPlusTree<K extends Comparable<K>, V> {

        private final int order;
        private BPlusNode<K, V> root;

        public BPlusTree(int order) {
            this.order = order;
            this.root  = new BPlusLeaf<>();
        }

        @SuppressWarnings("unchecked")
        public void insert(K key, V value) {
            BPlusNode<K, V> newChild = root.insert(key, value, order);
            if (newChild != null) {
                BPlusInternal<K, V> newRoot = new BPlusInternal<>();
                newRoot.keys.add(newChild.firstKey());
                newRoot.children.add(root);
                newRoot.children.add(newChild);
                root = newRoot;
            }
        }

        public Optional<V> search(K key) { return root.search(key); }

        /** Range scan: key feature of B+Tree (O(k + log n) where k = result count). */
        public List<V> rangeQuery(K from, K to) {
            return root.rangeQuery(from, to);
        }

        // ── B+Tree node hierarchy ─────────────────────────────────────────────

        abstract static class BPlusNode<K extends Comparable<K>, V> {
            List<K> keys = new ArrayList<>();
            abstract BPlusNode<K, V> insert(K key, V value, int order);
            abstract Optional<V>     search(K key);
            abstract List<V>         rangeQuery(K from, K to);
            abstract K               firstKey();
        }

        static class BPlusLeaf<K extends Comparable<K>, V> extends BPlusNode<K, V> {
            List<V>         values = new ArrayList<>();
            BPlusLeaf<K, V> next;  // linked list → range scans

            @Override
            BPlusNode<K, V> insert(K key, V value, int order) {
                int i = 0;
                while (i < keys.size() && key.compareTo(keys.get(i)) > 0) i++;
                if (i < keys.size() && key.compareTo(keys.get(i)) == 0) {
                    values.set(i, value); return null; // update
                }
                keys.add(i, key);
                values.add(i, value);

                if (keys.size() < order) return null;

                // Split leaf
                int mid = keys.size() / 2;
                BPlusLeaf<K, V> sibling = new BPlusLeaf<>();
                sibling.keys.addAll(keys.subList(mid, keys.size()));
                sibling.values.addAll(values.subList(mid, values.size()));
                keys.subList(mid, keys.size()).clear();
                values.subList(mid, values.size()).clear();
                sibling.next = this.next;
                this.next    = sibling;
                return sibling;
            }

            @Override
            Optional<V> search(K key) {
                int i = 0;
                while (i < keys.size() && key.compareTo(keys.get(i)) > 0) i++;
                if (i < keys.size() && key.compareTo(keys.get(i)) == 0) return Optional.of(values.get(i));
                return Optional.empty();
            }

            @Override
            List<V> rangeQuery(K from, K to) {
                List<V> result = new ArrayList<>();
                BPlusLeaf<K, V> cur = this;
                while (cur != null) {
                    for (int i = 0; i < cur.keys.size(); i++) {
                        if (cur.keys.get(i).compareTo(from) >= 0 && cur.keys.get(i).compareTo(to) <= 0)
                            result.add(cur.values.get(i));
                        if (cur.keys.get(i).compareTo(to) > 0) return result;
                    }
                    cur = cur.next;
                }
                return result;
            }

            @Override
            K firstKey() { return keys.get(0); }
        }

        static class BPlusInternal<K extends Comparable<K>, V> extends BPlusNode<K, V> {
            List<BPlusNode<K, V>> children = new ArrayList<>();

            @Override
            BPlusNode<K, V> insert(K key, V value, int order) {
                int i = 0;
                while (i < keys.size() && key.compareTo(keys.get(i)) >= 0) i++;
                BPlusNode<K, V> newChild = children.get(i).insert(key, value, order);
                if (newChild == null) return null;

                keys.add(i, newChild.firstKey());
                children.add(i + 1, newChild);

                if (keys.size() < order) return null;

                int mid = keys.size() / 2;
                BPlusInternal<K, V> sibling = new BPlusInternal<>();
                sibling.keys.addAll(keys.subList(mid + 1, keys.size()));
                sibling.children.addAll(children.subList(mid + 1, children.size()));
                keys.subList(mid, keys.size()).clear();
                children.subList(mid + 1, children.size()).clear();
                return sibling;
            }

            @Override
            Optional<V> search(K key) {
                int i = 0;
                while (i < keys.size() && key.compareTo(keys.get(i)) >= 0) i++;
                return children.get(i).search(key);
            }

            @Override
            List<V> rangeQuery(K from, K to) {
                int i = 0;
                while (i < keys.size() && from.compareTo(keys.get(i)) >= 0) i++;
                return children.get(i).rangeQuery(from, to);
            }

            @Override
            K firstKey() { return children.get(0).firstKey(); }
        }
    }
}
