package com.conceptualware.core.algorithms.sorting;

import java.util.*;

public class SortingAlgorithms {

    public static int[] bubbleSort(int[] arr) {
        int[] a = arr.clone();
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - i - 1; j++) {
                if (a[j] > a[j + 1]) { swap(a, j, j + 1); swapped = true; }
            }
            if (!swapped) break;
        }
        return a;
    }

    public static int[] selectionSort(int[] arr) {
        int[] a = arr.clone();
        for (int i = 0; i < a.length - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < a.length; j++)
                if (a[j] < a[minIdx]) minIdx = j;
            swap(a, i, minIdx);
        }
        return a;
    }

    public static int[] insertionSort(int[] arr) {
        int[] a = arr.clone();
        for (int i = 1; i < a.length; i++) {
            int key = a[i], j = i - 1;
            while (j >= 0 && a[j] > key) { a[j + 1] = a[j]; j--; }
            a[j + 1] = key;
        }
        return a;
    }

    public static int[] shellSort(int[] arr) {
        int[] a = arr.clone();
        int gap = a.length / 2;
        while (gap > 0) {
            for (int i = gap; i < a.length; i++) {
                int temp = a[i], j = i;
                while (j >= gap && a[j - gap] > temp) { a[j] = a[j - gap]; j -= gap; }
                a[j] = temp;
            }
            gap /= 2;
        }
        return a;
    }

    public static int[] mergeSort(int[] arr) {
        if (arr.length <= 1) return arr.clone();
        int[] a = arr.clone();
        mergeSortHelper(a, 0, a.length - 1);
        return a;
    }

    private static void mergeSortHelper(int[] a, int left, int right) {
        if (left >= right) return;
        int mid = left + (right - left) / 2;
        mergeSortHelper(a, left, mid);
        mergeSortHelper(a, mid + 1, right);
        merge(a, left, mid, right);
    }

    private static void merge(int[] a, int left, int mid, int right) {
        int[] tmp = Arrays.copyOfRange(a, left, right + 1);
        int i = 0, j = mid - left + 1, k = left;
        while (i <= mid - left && j < tmp.length) {
            if (tmp[i] <= tmp[j]) a[k++] = tmp[i++];
            else                  a[k++] = tmp[j++];
        }
        while (i <= mid - left) a[k++] = tmp[i++];
        while (j < tmp.length)  a[k++] = tmp[j++];
    }

    public static int[] quickSort(int[] arr) {
        int[] a = arr.clone();
        quickSortHelper(a, 0, a.length - 1);
        return a;
    }

    private static void quickSortHelper(int[] a, int low, int high) {
        if (low < high) {
            int p = partition(a, low, high);
            quickSortHelper(a, low, p - 1);
            quickSortHelper(a, p + 1, high);
        }
    }

    private static int partition(int[] a, int low, int high) {
        int mid = low + (high - low) / 2;
        if (a[mid] < a[low]) swap(a, low, mid);
        if (a[high] < a[low]) swap(a, low, high);
        if (a[mid] < a[high]) swap(a, mid, high);
        int pivot = a[high];
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (a[j] <= pivot) { i++; swap(a, i, j); }
        }
        swap(a, i + 1, high);
        return i + 1;
    }

    public static int[] heapSort(int[] arr) {
        int[] a = arr.clone();
        int n = a.length;
        for (int i = n / 2 - 1; i >= 0; i--) heapify(a, n, i);
        for (int i = n - 1; i > 0; i--) {
            swap(a, 0, i);
            heapify(a, i, 0);
        }
        return a;
    }

    private static void heapify(int[] a, int n, int i) {
        int largest = i, l = 2*i+1, r = 2*i+2;
        if (l < n && a[l] > a[largest]) largest = l;
        if (r < n && a[r] > a[largest]) largest = r;
        if (largest != i) { swap(a, i, largest); heapify(a, n, largest); }
    }

    public static int[] countingSort(int[] arr, int maxVal) {
        int[] count = new int[maxVal + 1];
        for (int x : arr) count[x]++;
        int[] result = new int[arr.length];
        int idx = 0;
        for (int i = 0; i <= maxVal; i++)
            while (count[i]-- > 0) result[idx++] = i;
        return result;
    }

    public static int[] radixSort(int[] arr) {
        int[] a = arr.clone();
        int max = Arrays.stream(a).max().orElse(0);
        for (int exp = 1; max / exp > 0; exp *= 10) {
            countingSortByDigit(a, exp);
        }
        return a;
    }

    private static void countingSortByDigit(int[] a, int exp) {
        int n = a.length;
        int[] output = new int[n];
        int[] count = new int[10];
        for (int x : a) count[(x / exp) % 10]++;
        for (int i = 1; i < 10; i++) count[i] += count[i - 1];
        for (int i = n - 1; i >= 0; i--) {
            int digit = (a[i] / exp) % 10;
            output[--count[digit]] = a[i];
        }
        System.arraycopy(output, 0, a, 0, n);
    }

    public static double[] bucketSort(double[] arr) {
        int n = arr.length;
        @SuppressWarnings("unchecked")
        List<Double>[] buckets = new List[n];
        for (int i = 0; i < n; i++) buckets[i] = new ArrayList<>();
        for (double x : arr) {
            int idx = (int) (x * n);
            if (idx >= n) idx = n - 1;
            buckets[idx].add(x);
        }
        double[] result = new double[n];
        int k = 0;
        for (List<Double> bucket : buckets) {
            Collections.sort(bucket);
            for (double x : bucket) result[k++] = x;
        }
        return result;
    }

    public static int[] timSort(int[] arr) {
        int[] a = arr.clone();
        int RUN = 32;
        int n = a.length;
        for (int i = 0; i < n; i += RUN)
            insertionSortRange(a, i, Math.min(i + RUN - 1, n - 1));
        for (int size = RUN; size < n; size *= 2) {
            for (int left = 0; left < n; left += 2 * size) {
                int mid = Math.min(left + size - 1, n - 1);
                int right = Math.min(left + 2 * size - 1, n - 1);
                if (mid < right) merge(a, left, mid, right);
            }
        }
        return a;
    }

    private static void insertionSortRange(int[] a, int left, int right) {
        for (int i = left + 1; i <= right; i++) {
            int key = a[i], j = i - 1;
            while (j >= left && a[j] > key) { a[j + 1] = a[j]; j--; }
            a[j + 1] = key;
        }
    }

    public record ComplexityInfo(String name, String timeAvg, String timeWorst,
                                  String timeBest, String space, boolean stable) {}

    public static Map<String, ComplexityInfo> complexityTable() {
        return Map.ofEntries(
            Map.entry("Bubble",    new ComplexityInfo("Bubble Sort",    "O(n²)",      "O(n²)",      "O(n)",      "O(1)", true)),
            Map.entry("Selection", new ComplexityInfo("Selection Sort", "O(n²)",      "O(n²)",      "O(n²)",     "O(1)", false)),
            Map.entry("Insertion", new ComplexityInfo("Insertion Sort", "O(n²)",      "O(n²)",      "O(n)",      "O(1)", true)),
            Map.entry("Shell",     new ComplexityInfo("Shell Sort",     "O(n log²n)", "O(n²)",      "O(n log n)","O(1)", false)),
            Map.entry("Merge",     new ComplexityInfo("Merge Sort",     "O(n log n)", "O(n log n)", "O(n log n)","O(n)", true)),
            Map.entry("Quick",     new ComplexityInfo("Quick Sort",     "O(n log n)", "O(n²)",      "O(n log n)","O(log n)", false)),
            Map.entry("Heap",      new ComplexityInfo("Heap Sort",      "O(n log n)", "O(n log n)", "O(n log n)","O(1)", false)),
            Map.entry("Counting",  new ComplexityInfo("Counting Sort",  "O(n+k)",     "O(n+k)",     "O(n+k)",    "O(k)", true)),
            Map.entry("Radix",     new ComplexityInfo("Radix Sort",     "O(d*(n+k))", "O(d*(n+k))", "O(d*(n+k))","O(n+k)", true)),
            Map.entry("Tim",       new ComplexityInfo("Tim Sort",       "O(n log n)", "O(n log n)", "O(n)",      "O(n)", true))
        );
    }

    public static final int MAX_STEP_VISUALIZATION_SIZE = 100;

    private static void requireVisualizableSize(int[] arr) {
        if (arr.length > MAX_STEP_VISUALIZATION_SIZE) {
            throw new IllegalArgumentException(
                "Visualização passo a passo limitada a " + MAX_STEP_VISUALIZATION_SIZE
                    + " elementos (recebido " + arr.length + "): o número de passos cresce com n² e "
                    + "cada passo copia o array inteiro.");
        }
    }

    public static List<SortStep> bubbleSortWithSteps(int[] arr) {
        requireVisualizableSize(arr);
        List<SortStep> steps = new ArrayList<>();
        int[] a = arr.clone();
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - i - 1; j++) {
                steps.add(new SortStep(
                    new int[]{j, j+1}, new int[]{}, a.clone()));
                if (a[j] > a[j + 1]) {
                    swap(a, j, j + 1); swapped = true;
                    steps.add(new SortStep(
                        new int[]{}, new int[]{j, j+1}, a.clone()));
                }
            }
            if (!swapped) break;
        }
        return steps;
    }

    public static List<SortStep> insertionSortWithSteps(int[] arr) {
        requireVisualizableSize(arr);
        List<SortStep> steps = new ArrayList<>();
        int[] a = arr.clone();
        for (int i = 1; i < a.length; i++) {
            int key = a[i], j = i - 1;
            while (j >= 0 && a[j] > key) {
                steps.add(new SortStep(
                    new int[]{j, j+1}, new int[]{j, j+1}, a.clone()));
                a[j + 1] = a[j--];
            }
            a[j + 1] = key;
            steps.add(new SortStep(
                new int[]{}, new int[]{j+1}, a.clone()));
        }
        return steps;
    }

    private static void swap(int[] a, int i, int j) {
        int tmp = a[i]; a[i] = a[j]; a[j] = tmp;
    }
}
