package com.conceptualware.core.datastructures.tree;

public class AVLTree<T extends Comparable<T>> extends BinarySearchTree<T> {

    @Override
    public void insert(T key) {
        root = insertAVL(root, key);
    }

    @Override
    public void delete(T key) {
        root = deleteAVL(root, key);
    }

    private Node<T> insertAVL(Node<T> node, T key) {
        if (node == null) return new Node<>(key);
        int cmp = key.compareTo(node.key);
        if      (cmp < 0) node.left  = insertAVL(node.left, key);
        else if (cmp > 0) node.right = insertAVL(node.right, key);
        else              return node;

        updateHeight(node);
        return balance(node);
    }

    private Node<T> deleteAVL(Node<T> node, T key) {
        if (node == null) return null;
        int cmp = key.compareTo(node.key);
        if (cmp < 0) { node.left  = deleteAVL(node.left, key); }
        else if (cmp > 0) { node.right = deleteAVL(node.right, key); }
        else {
            if (node.left == null) return node.right;
            if (node.right == null) return node.left;
            Node<T> successor = min(node.right);
            node.key = successor.key;
            node.right = deleteAVL(node.right, successor.key);
        }
        updateHeight(node);
        return balance(node);
    }

    private void updateHeight(Node<T> n) {
        n.height = 1 + Math.max(height(n.left), height(n.right));
    }

    private int balanceFactor(Node<T> n) {
        return n == null ? 0 : height(n.left) - height(n.right);
    }

    private Node<T> balance(Node<T> n) {
        int bf = balanceFactor(n);

        if (bf > 1) {
            if (balanceFactor(n.left) < 0) n.left = rotateLeft(n.left);
            return rotateRight(n);
        }
        if (bf < -1) {
            if (balanceFactor(n.right) > 0) n.right = rotateRight(n.right);
            return rotateLeft(n);
        }
        return n;
    }

    private Node<T> rotateRight(Node<T> y) {
        Node<T> x = y.left;
        Node<T> T2 = x.right;
        x.right = y;
        y.left  = T2;
        updateHeight(y);
        updateHeight(x);
        return x;
    }

    private Node<T> rotateLeft(Node<T> x) {
        Node<T> y  = x.right;
        Node<T> T2 = y.left;
        y.left  = x;
        x.right = T2;
        updateHeight(x);
        updateHeight(y);
        return y;
    }

    public boolean isBalanced() {
        return isBalanced(root);
    }

    private boolean isBalanced(Node<T> n) {
        if (n == null) return true;
        int bf = Math.abs(balanceFactor(n));
        return bf <= 1 && isBalanced(n.left) && isBalanced(n.right);
    }
}
