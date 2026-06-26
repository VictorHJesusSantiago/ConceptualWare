package com.conceptualware.core.datastructures.linear;

import java.util.EmptyStackException;

/**
 * Concept #4 — Pilha (Stack), Fila (Queue), Deque, Fila de prioridade
 * Concept #9 — Memória Stack vs Heap (Stack = LIFO call frame)
 * Concept #17 — Memória virtual, stack frames
 */
public class Stack<T> {

    private static class Node<T> {
        T data; Node<T> next;
        Node(T d, Node<T> n) { data = d; next = n; }
    }

    private Node<T> top;
    private int size;

    /** LIFO — Last In, First Out. */
    public void push(T item) {
        top = new Node<>(item, top);
        size++;
    }

    public T pop() {
        if (isEmpty()) throw new EmptyStackException();
        T data = top.data;
        top = top.next;
        size--;
        return data;
    }

    public T peek() {
        if (isEmpty()) throw new EmptyStackException();
        return top.data;
    }

    public boolean isEmpty() { return top == null; }
    public int size()        { return size; }

    // ── Stack Applications ────────────────────────────────────────────────────

    /** Check balanced parentheses — classic stack use case. */
    public static boolean isBalanced(String s) {
        Stack<Character> stack = new Stack<>();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                stack.push(c);
            } else if (c == ')' || c == ']' || c == '}') {
                if (stack.isEmpty()) return false;
                char open = stack.pop();
                if ((c == ')' && open != '(') ||
                    (c == ']' && open != '[') ||
                    (c == '}' && open != '{')) return false;
            }
        }
        return stack.isEmpty();
    }

    /** Evaluate postfix (RPN) expression — stack-based evaluation. */
    public static double evaluateRPN(String[] tokens) {
        Stack<Double> stack = new Stack<>();
        for (String token : tokens) {
            switch (token) {
                case "+" -> { double b = stack.pop(); stack.push(stack.pop() + b); }
                case "-" -> { double b = stack.pop(); stack.push(stack.pop() - b); }
                case "*" -> { double b = stack.pop(); stack.push(stack.pop() * b); }
                case "/" -> { double b = stack.pop(); stack.push(stack.pop() / b); }
                default  -> stack.push(Double.parseDouble(token));
            }
        }
        return stack.pop();
    }

    // ── Queue ────────────────────────────────────────────────────────────────

    public static class Queue<T> {
        private final LinkedList.DoublyLinkedList<T> list = new LinkedList.DoublyLinkedList<>();

        public void enqueue(T item) { list.addLast(item); }  // FIFO
        public T dequeue()           { return list.removeFirst(); }
        public T peek()              { return list.peekFirst(); }
        public boolean isEmpty()     { return list.isEmpty(); }
        public int size()            { return list.size(); }
    }

    // ── Deque (Double-ended Queue) ─────────────────────────────────────────────

    public static class Deque<T> {
        private final LinkedList.DoublyLinkedList<T> list = new LinkedList.DoublyLinkedList<>();

        public void addFirst(T item)  { list.addFirst(item); }
        public void addLast(T item)   { list.addLast(item); }
        public T removeFirst()        { return list.removeFirst(); }
        public T removeLast()         { return list.removeLast(); }
        public T peekFirst()          { return list.peekFirst(); }
        public T peekLast()           { return list.peekLast(); }
        public boolean isEmpty()      { return list.isEmpty(); }
        public int size()             { return list.size(); }
    }

    // ── Circular Buffer / Ring Buffer ─────────────────────────────────────────

    public static class CircularBuffer<T> {
        private final Object[] buffer;
        private int head, tail, size;
        private final int capacity;

        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new Object[capacity];
        }

        public void write(T item) {
            if (isFull()) throw new IllegalStateException("Buffer full");
            buffer[tail] = item;
            tail = (tail + 1) % capacity;
            size++;
        }

        @SuppressWarnings("unchecked")
        public T read() {
            if (isEmpty()) throw new java.util.NoSuchElementException("Buffer empty");
            T item = (T) buffer[head];
            head = (head + 1) % capacity;
            size--;
            return item;
        }

        public boolean isEmpty() { return size == 0; }
        public boolean isFull()  { return size == capacity; }
        public int size()        { return size; }
        public int capacity()    { return capacity; }
    }
}
