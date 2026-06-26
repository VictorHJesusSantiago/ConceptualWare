package com.conceptualware.core.datastructures.heap;

import java.util.*;

/**
 * Concept #4 — Heap mínimo (Min-Heap), Heap máximo (Max-Heap), Fila de prioridade
 * Concept #5 — Heap Sort, K-th largest/smallest
 * Concept #9 — Memória Heap (alocação dinâmica) vs Stack
 */
public class Heap<T> {

    private final List<T> data = new ArrayList<>();
    private final Comparator<T> comparator;
    private final boolean isMinHeap;

    /** Min-heap with natural ordering. */
    public static <T extends Comparable<T>> Heap<T> minHeap() {
        return new Heap<>(Comparator.naturalOrder(), true);
    }

    /** Max-heap with natural ordering. */
    public static <T extends Comparable<T>> Heap<T> maxHeap() {
        return new Heap<>(Comparator.reverseOrder(), false);
    }

    public Heap(Comparator<T> comparator, boolean isMinHeap) {
        this.comparator = comparator;
        this.isMinHeap = isMinHeap;
    }

    public void insert(T item) {
        data.add(item);
        siftUp(data.size() - 1);
    }

    public T peek() {
        if (isEmpty()) throw new NoSuchElementException("Heap is empty");
        return data.get(0);
    }

    public T poll() {
        if (isEmpty()) throw new NoSuchElementException("Heap is empty");
        T top = data.get(0);
        int last = data.size() - 1;
        data.set(0, data.get(last));
        data.remove(last);
        if (!isEmpty()) siftDown(0);
        return top;
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (comparator.compare(data.get(i), data.get(parent)) < 0) {
                swap(i, parent);
                i = parent;
            } else break;
        }
    }

    private void siftDown(int i) {
        int size = data.size();
        while (true) {
            int target = i, left = 2*i+1, right = 2*i+2;
            if (left < size && comparator.compare(data.get(left), data.get(target)) < 0) target = left;
            if (right < size && comparator.compare(data.get(right), data.get(target)) < 0) target = right;
            if (target == i) break;
            swap(i, target);
            i = target;
        }
    }

    private void swap(int a, int b) {
        T tmp = data.get(a); data.set(a, data.get(b)); data.set(b, tmp);
    }

    public boolean isEmpty() { return data.isEmpty(); }
    public int size()        { return data.size(); }

    /** Heapify an existing array in O(n). */
    public static <T extends Comparable<T>> Heap<T> heapify(List<T> items) {
        Heap<T> heap = Heap.minHeap();
        heap.data.addAll(items);
        for (int i = heap.data.size() / 2 - 1; i >= 0; i--) {
            heap.siftDown(i);
        }
        return heap;
    }

    /** HeapSort — O(n log n) (Concept #5). */
    public static <T extends Comparable<T>> List<T> heapSort(List<T> items) {
        Heap<T> maxH = Heap.maxHeap();
        maxH.data.addAll(items);
        // Heapify
        for (int i = maxH.data.size() / 2 - 1; i >= 0; i--) maxH.siftDown(i);
        List<T> sorted = new ArrayList<>(items.size());
        while (!maxH.isEmpty()) sorted.add(0, maxH.poll());
        return sorted;
    }

    /** K largest elements — uses min-heap of size K. O(n log k). */
    public static <T extends Comparable<T>> List<T> kLargest(List<T> items, int k) {
        Heap<T> minH = Heap.minHeap();
        for (T item : items) {
            minH.insert(item);
            if (minH.size() > k) minH.poll();
        }
        List<T> result = new ArrayList<>();
        while (!minH.isEmpty()) result.add(0, minH.poll());
        return result;
    }

    /** Priority Queue wrapper — Concept #4. */
    public static class PriorityQueue<T> {
        private final Heap<T> heap;

        public PriorityQueue(Comparator<T> comparator) {
            this.heap = new Heap<>(comparator, true);
        }

        public void enqueue(T item)  { heap.insert(item); }
        public T dequeue()           { return heap.poll(); }
        public T peek()              { return heap.peek(); }
        public boolean isEmpty()     { return heap.isEmpty(); }
        public int size()            { return heap.size(); }
    }
}
