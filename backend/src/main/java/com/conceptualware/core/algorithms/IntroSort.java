package com.conceptualware.core.algorithms;

/**
 * Concept #5 — IntroSort (Introsort):
 *   Hybrid sorting algorithm used by C++ std::sort and .NET Array.Sort.
 *   Combines three algorithms to guarantee O(n log n) worst case with low constants:
 *
 *   1. QuickSort   — O(n log n) average, cache-friendly, fast in practice
 *   2. HeapSort    — O(n log n) worst case guarantee (kicks in when recursion is too deep)
 *   3. InsertionSort — O(n²) but extremely fast for small arrays (n ≤ 16)
 *
 *   Strategy:
 *     - Recursion depth limit = 2·floor(log₂(n))
 *     - If depth exceeded → switch to HeapSort (prevents QuickSort's O(n²) worst case)
 *     - If partition size ≤ 16 → switch to InsertionSort (fewer comparisons, better cache)
 *
 * Concept #5 — Algorithm design: hybrid algorithms, adaptive sorting
 */
public class IntroSort {

    private static final int INSERTION_SORT_THRESHOLD = 16;

    // ── IntroSort entry point ─────────────────────────────────────────────────

    public static void sort(int[] arr) {
        if (arr == null || arr.length <= 1) return;
        int depthLimit = 2 * (int)(Math.log(arr.length) / Math.log(2));
        introsort(arr, 0, arr.length - 1, depthLimit);
    }

    private static void introsort(int[] arr, int lo, int hi, int depthLimit) {
        int size = hi - lo + 1;

        if (size <= INSERTION_SORT_THRESHOLD) {
            insertionSort(arr, lo, hi);          // small partition → Insertion Sort
            return;
        }

        if (depthLimit == 0) {
            heapSort(arr, lo, hi);               // depth exceeded → Heap Sort
            return;
        }

        int pivot = medianOfThree(arr, lo, lo + size / 2, hi);
        int p     = partition(arr, lo, hi, pivot);

        introsort(arr, lo, p - 1, depthLimit - 1);
        introsort(arr, p + 1, hi, depthLimit - 1);
    }

    // ── Median-of-three pivot selection ──────────────────────────────────────

    /** Picks median of arr[a], arr[b], arr[c] — reduces chance of worst-case QuickSort. */
    private static int medianOfThree(int[] arr, int a, int b, int c) {
        if (arr[a] > arr[b]) swap(arr, a, b);
        if (arr[a] > arr[c]) swap(arr, a, c);
        if (arr[b] > arr[c]) swap(arr, b, c);
        return arr[b]; // arr[b] is now the median
    }

    // ── Partition (Lomuto scheme) ─────────────────────────────────────────────

    private static int partition(int[] arr, int lo, int hi, int pivotVal) {
        // Move pivot to end
        for (int i = lo; i <= hi; i++) {
            if (arr[i] == pivotVal) { swap(arr, i, hi); break; }
        }

        int i = lo - 1;
        for (int j = lo; j < hi; j++) {
            if (arr[j] <= arr[hi]) { i++; swap(arr, i, j); }
        }
        swap(arr, i + 1, hi);
        return i + 1;
    }

    // ── Insertion Sort (for small partitions) ────────────────────────────────

    private static void insertionSort(int[] arr, int lo, int hi) {
        for (int i = lo + 1; i <= hi; i++) {
            int key = arr[i];
            int j   = i - 1;
            while (j >= lo && arr[j] > key) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    // ── Heap Sort (fallback for deep recursion) ───────────────────────────────

    private static void heapSort(int[] arr, int lo, int hi) {
        int n = hi - lo + 1;

        // Build max-heap on the sub-array
        for (int i = n / 2 - 1; i >= 0; i--) heapify(arr, lo, n, i);

        // Extract elements from heap
        for (int i = n - 1; i > 0; i--) {
            swap(arr, lo, lo + i);
            heapify(arr, lo, i, 0);
        }
    }

    private static void heapify(int[] arr, int base, int n, int i) {
        int largest = i;
        int left    = 2 * i + 1;
        int right   = 2 * i + 2;

        if (left  < n && arr[base + left]  > arr[base + largest]) largest = left;
        if (right < n && arr[base + right] > arr[base + largest]) largest = right;

        if (largest != i) {
            swap(arr, base + i, base + largest);
            heapify(arr, base, n, largest);
        }
    }

    // ── Pure QuickSort (for comparison) ──────────────────────────────────────

    public static void quickSort(int[] arr, int lo, int hi) {
        if (lo < hi) {
            int p = partitionHoare(arr, lo, hi);
            quickSort(arr, lo, p);
            quickSort(arr, p + 1, hi);
        }
    }

    /** Hoare partition — fewer swaps than Lomuto on average. */
    private static int partitionHoare(int[] arr, int lo, int hi) {
        int pivot = arr[lo + (hi - lo) / 2];
        int i = lo - 1, j = hi + 1;
        while (true) {
            do i++; while (arr[i] < pivot);
            do j--; while (arr[j] > pivot);
            if (i >= j) return j;
            swap(arr, i, j);
        }
    }

    // ── Pure Heap Sort (standalone) ───────────────────────────────────────────

    public static void heapSort(int[] arr) {
        int n = arr.length;
        for (int i = n / 2 - 1; i >= 0; i--) heapify(arr, 0, n, i);
        for (int i = n - 1; i > 0; i--) { swap(arr, 0, i); heapify(arr, 0, i, 0); }
    }

    private static void swap(int[] arr, int i, int j) {
        int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
    }

    // ── Complexity comparison ─────────────────────────────────────────────────

    public record SortComplexity(String algorithm, String best, String average, String worst, String space) {
        public static SortComplexity[] all() {
            return new SortComplexity[]{
                new SortComplexity("IntroSort",     "O(n log n)", "O(n log n)", "O(n log n)", "O(log n)"),
                new SortComplexity("QuickSort",     "O(n log n)", "O(n log n)", "O(n²)",      "O(log n)"),
                new SortComplexity("HeapSort",      "O(n log n)", "O(n log n)", "O(n log n)", "O(1)"),
                new SortComplexity("MergeSort",     "O(n log n)", "O(n log n)", "O(n log n)", "O(n)"),
                new SortComplexity("InsertionSort", "O(n)",       "O(n²)",      "O(n²)",      "O(1)"),
                new SortComplexity("TimSort",       "O(n)",       "O(n log n)", "O(n log n)", "O(n)"),
            };
        }
    }
}
