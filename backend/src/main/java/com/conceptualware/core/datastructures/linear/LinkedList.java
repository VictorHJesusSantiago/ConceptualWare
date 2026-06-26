package com.conceptualware.core.datastructures.linear;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Concept #4 — Estruturas de Dados Lineares:
 *   Lista ligada simples, Lista duplamente ligada, Lista circular
 *
 * Concept #7 — OOP: Generic class, encapsulation, inner class, iterator pattern
 * Concept #8 — FP: Iterable, generic types
 */
public class LinkedList<T> implements Iterable<T> {

    // ── Singly Linked List ───────────────────────────────────────────────────

    private static class Node<T> {
        T data;
        Node<T> next;
        Node(T data) { this.data = data; }
    }

    private Node<T> head;
    private int size;

    public void addFirst(T data) {
        Node<T> node = new Node<>(data);
        node.next = head;
        head = node;
        size++;
    }

    public void addLast(T data) {
        Node<T> node = new Node<>(data);
        if (head == null) { head = node; size++; return; }
        Node<T> curr = head;
        while (curr.next != null) curr = curr.next;
        curr.next = node;
        size++;
    }

    public T removeFirst() {
        if (head == null) throw new NoSuchElementException("List is empty");
        T data = head.data;
        head = head.next;
        size--;
        return data;
    }

    public T removeLast() {
        if (head == null) throw new NoSuchElementException("List is empty");
        if (head.next == null) { T d = head.data; head = null; size--; return d; }
        Node<T> curr = head;
        while (curr.next.next != null) curr = curr.next;
        T data = curr.next.data;
        curr.next = null;
        size--;
        return data;
    }

    public void reverse() {
        Node<T> prev = null, curr = head;
        while (curr != null) {
            Node<T> next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        head = prev;
    }

    /** Floyd's cycle detection — Concept #5. */
    public boolean hasCycle() {
        Node<T> slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) return true;
        }
        return false;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Node<T> current = head;
            public boolean hasNext() { return current != null; }
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                T data = current.data;
                current = current.next;
                return data;
            }
        };
    }

    // ── Doubly Linked List ───────────────────────────────────────────────────

    public static class DoublyLinkedList<T> implements Iterable<T> {

        private static class DNode<T> {
            T data;
            DNode<T> prev, next;
            DNode(T data) { this.data = data; }
        }

        private DNode<T> head, tail;
        private int size;

        public void addFirst(T data) {
            DNode<T> node = new DNode<>(data);
            if (head == null) { head = tail = node; size++; return; }
            node.next = head;
            head.prev = node;
            head = node;
            size++;
        }

        public void addLast(T data) {
            DNode<T> node = new DNode<>(data);
            if (tail == null) { head = tail = node; size++; return; }
            node.prev = tail;
            tail.next = node;
            tail = node;
            size++;
        }

        public T removeFirst() {
            if (head == null) throw new NoSuchElementException();
            T d = head.data;
            head = head.next;
            if (head != null) head.prev = null; else tail = null;
            size--;
            return d;
        }

        public T removeLast() {
            if (tail == null) throw new NoSuchElementException();
            T d = tail.data;
            tail = tail.prev;
            if (tail != null) tail.next = null; else head = null;
            size--;
            return d;
        }

        public T peekFirst() { if (head == null) throw new NoSuchElementException(); return head.data; }
        public T peekLast()  { if (tail == null) throw new NoSuchElementException(); return tail.data; }
        public int size()    { return size; }
        public boolean isEmpty() { return size == 0; }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<>() {
                DNode<T> cur = head;
                public boolean hasNext() { return cur != null; }
                public T next() { T d = cur.data; cur = cur.next; return d; }
            };
        }
    }

    // ── Circular Linked List ────────────────────────────────────────────────

    public static class CircularLinkedList<T> {
        private Node<T> tail; // tail.next == head (the sentinel back-link)
        private int size;

        private static class Node<T> { T data; Node<T> next; Node(T d) { data = d; } }

        public void add(T data) {
            Node<T> node = new Node<>(data);
            if (tail == null) { tail = node; tail.next = tail; }
            else { node.next = tail.next; tail.next = node; tail = node; }
            size++;
        }

        public T removeHead() {
            if (tail == null) throw new NoSuchElementException();
            Node<T> head = tail.next;
            if (head == tail) { T d = head.data; tail = null; size--; return d; }
            tail.next = head.next;
            size--;
            return head.data;
        }

        public int size() { return size; }
        public boolean isEmpty() { return size == 0; }
    }
}
