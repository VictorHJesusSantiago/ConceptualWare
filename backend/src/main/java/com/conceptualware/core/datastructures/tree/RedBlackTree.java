package com.conceptualware.core.datastructures.tree;

import java.util.*;

public class RedBlackTree<K extends Comparable<K>, V> {

    private static final boolean RED   = true;
    private static final boolean BLACK = false;

    private Node nil;
    private Node root;
    private int  size;

    public RedBlackTree() {
        nil        = new Node(null, null);
        nil.color  = BLACK;
        nil.left   = nil;
        nil.right  = nil;
        nil.parent = nil;
        root       = nil;
    }

    private class Node {
        K      key;
        V      value;
        Node   left, right, parent;
        boolean color;

        Node(K key, V value) {
            this.key   = key;
            this.value = value;
            this.left  = nil;
            this.right = nil;
            this.parent = nil;
            this.color  = RED;
        }
    }

    private void rotateLeft(Node x) {
        Node y  = x.right;
        x.right = y.left;
        if (y.left != nil) y.left.parent = x;
        y.parent = x.parent;
        if (x.parent == nil)       root = y;
        else if (x == x.parent.left) x.parent.left  = y;
        else                         x.parent.right = y;
        y.left   = x;
        x.parent = y;
    }

    private void rotateRight(Node y) {
        Node x  = y.left;
        y.left  = x.right;
        if (x.right != nil) x.right.parent = y;
        x.parent = y.parent;
        if (y.parent == nil)       root = x;
        else if (y == y.parent.right) y.parent.right = x;
        else                          y.parent.left  = x;
        x.right  = y;
        y.parent = x;
    }

    public void insert(K key, V value) {
        Node z = new Node(key, value);
        Node y = nil;
        Node x = root;

        while (x != nil) {
            y = x;
            int cmp = key.compareTo(x.key);
            if      (cmp < 0) x = x.left;
            else if (cmp > 0) x = x.right;
            else { x.value = value; return; }
        }

        z.parent = y;
        if (y == nil)             root       = z;
        else if (key.compareTo(y.key) < 0) y.left  = z;
        else                               y.right = z;

        size++;
        insertFixup(z);
    }

    private void insertFixup(Node z) {
        while (z.parent.color == RED) {
            if (z.parent == z.parent.parent.left) {
                Node y = z.parent.parent.right;
                if (y.color == RED) {
                    z.parent.color         = BLACK;
                    y.color                = BLACK;
                    z.parent.parent.color  = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.right) {
                        z = z.parent;
                        rotateLeft(z);
                    }
                    z.parent.color        = BLACK;
                    z.parent.parent.color = RED;
                    rotateRight(z.parent.parent);
                }
            } else {
                Node y = z.parent.parent.left;
                if (y.color == RED) {
                    z.parent.color        = BLACK;
                    y.color               = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.left) {
                        z = z.parent;
                        rotateRight(z);
                    }
                    z.parent.color        = BLACK;
                    z.parent.parent.color = RED;
                    rotateLeft(z.parent.parent);
                }
            }
        }
        root.color = BLACK;
    }

    public boolean delete(K key) {
        Node z = findNode(key);
        if (z == nil) return false;
        deleteNode(z);
        size--;
        return true;
    }

    private void deleteNode(Node z) {
        Node y = z, x;
        boolean yOriginalColor = y.color;

        if (z.left == nil) {
            x = z.right;
            transplant(z, z.right);
        } else if (z.right == nil) {
            x = z.left;
            transplant(z, z.left);
        } else {
            y             = minimum(z.right);
            yOriginalColor = y.color;
            x = y.right;
            if (y.parent == z) {
                x.parent = y;
            } else {
                transplant(y, y.right);
                y.right        = z.right;
                y.right.parent = y;
            }
            transplant(z, y);
            y.left        = z.left;
            y.left.parent = y;
            y.color       = z.color;
        }

        if (yOriginalColor == BLACK) deleteFixup(x);
    }

    private void transplant(Node u, Node v) {
        if (u.parent == nil)           root          = v;
        else if (u == u.parent.left)   u.parent.left  = v;
        else                           u.parent.right = v;
        v.parent = u.parent;
    }

    private void deleteFixup(Node x) {
        while (x != root && x.color == BLACK) {
            if (x == x.parent.left) {
                Node w = x.parent.right;
                if (w.color == RED) {
                    w.color        = BLACK;
                    x.parent.color = RED;
                    rotateLeft(x.parent);
                    w = x.parent.right;
                }
                if (w.left.color == BLACK && w.right.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else {
                    if (w.right.color == BLACK) {
                        w.left.color = BLACK;
                        w.color      = RED;
                        rotateRight(w);
                        w = x.parent.right;
                    }
                    w.color          = x.parent.color;
                    x.parent.color   = BLACK;
                    w.right.color    = BLACK;
                    rotateLeft(x.parent);
                    x = root;
                }
            } else {
                Node w = x.parent.left;
                if (w.color == RED) {
                    w.color        = BLACK;
                    x.parent.color = RED;
                    rotateRight(x.parent);
                    w = x.parent.left;
                }
                if (w.right.color == BLACK && w.left.color == BLACK) {
                    w.color = RED;
                    x = x.parent;
                } else {
                    if (w.left.color == BLACK) {
                        w.right.color = BLACK;
                        w.color       = RED;
                        rotateLeft(w);
                        w = x.parent.left;
                    }
                    w.color        = x.parent.color;
                    x.parent.color = BLACK;
                    w.left.color   = BLACK;
                    rotateRight(x.parent);
                    x = root;
                }
            }
        }
        x.color = BLACK;
    }

    public Optional<V> get(K key) {
        Node n = findNode(key);
        return n == nil ? Optional.empty() : Optional.of(n.value);
    }

    public boolean contains(K key) { return findNode(key) != nil; }

    private Node findNode(K key) {
        Node x = root;
        while (x != nil) {
            int cmp = key.compareTo(x.key);
            if      (cmp < 0) x = x.left;
            else if (cmp > 0) x = x.right;
            else              return x;
        }
        return nil;
    }

    public List<K> inOrder() {
        List<K> result = new ArrayList<>();
        inOrderHelper(root, result);
        return result;
    }

    private void inOrderHelper(Node n, List<K> result) {
        if (n == nil) return;
        inOrderHelper(n.left, result);
        result.add(n.key);
        inOrderHelper(n.right, result);
    }

    public boolean isValidRedBlackTree() {
        if (root.color != BLACK) return false;
        return blackHeight(root) != -1;
    }

    private int blackHeight(Node n) {
        if (n == nil) return 0;
        if (n.color == RED && n.parent != nil && n.parent.color == RED) return -1;
        int left  = blackHeight(n.left);
        int right = blackHeight(n.right);
        if (left == -1 || right == -1 || left != right) return -1;
        return left + (n.color == BLACK ? 1 : 0);
    }

    private Node minimum(Node n) {
        while (n.left != nil) n = n.left;
        return n;
    }

    public int size()    { return size; }
    public boolean isEmpty() { return size == 0; }
}
