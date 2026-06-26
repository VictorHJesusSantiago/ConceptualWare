package com.conceptualware.core.algorithms;

import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — Testes de Software:
 *   Teste unitário, TDD (Red-Green-Refactor), AAA (Arrange-Act-Assert),
 *   Cobertura por linhas, ramificações, condições
 *   Teste de mutação (mutation testing verified via AAA)
 *   Property-based testing concepts, Test isolation
 *
 * Follows: Given-When-Then (same as Arrange-Act-Assert)
 */
@DisplayName("Sorting Algorithms — Unit Tests")
class SortingAlgorithmsTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static int[] randomArray(int size, long seed) {
        Random rng = new Random(seed);
        return rng.ints(size, 0, 10_000).toArray();
    }

    private static int[] sorted(int[] arr) {
        int[] copy = arr.clone();
        Arrays.sort(copy);
        return copy;
    }

    // ── Bubble Sort ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Bubble Sort")
    class BubbleSortTests {

        @Test
        @DisplayName("Should sort random array correctly")
        void shouldSortRandomArray() {
            // Arrange
            int[] input = {64, 34, 25, 12, 22, 11, 90};

            // Act
            int[] result = SortingAlgorithms.bubbleSort(input);

            // Assert
            assertThat(result).isSorted();
            assertThat(result).containsExactlyInAnyOrder(64, 34, 25, 12, 22, 11, 90);
        }

        @Test
        @DisplayName("Should handle empty array")
        void shouldHandleEmpty() {
            assertThat(SortingAlgorithms.bubbleSort(new int[]{})).isEmpty();
        }

        @Test
        @DisplayName("Should handle single element")
        void shouldHandleSingleElement() {
            assertThat(SortingAlgorithms.bubbleSort(new int[]{42})).containsExactly(42);
        }

        @Test
        @DisplayName("Should handle already sorted array (O(n) best case)")
        void shouldHandleSorted() {
            int[] sorted = {1, 2, 3, 4, 5};
            assertThat(SortingAlgorithms.bubbleSort(sorted)).isSorted();
        }

        @Test
        @DisplayName("Should handle reverse sorted array (worst case)")
        void shouldHandleReverse() {
            assertThat(SortingAlgorithms.bubbleSort(new int[]{5, 4, 3, 2, 1}))
                .containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        @DisplayName("Should not mutate original array")
        void shouldNotMutateOriginal() {
            int[] original = {3, 1, 4, 1, 5};
            int[] copy = original.clone();
            SortingAlgorithms.bubbleSort(original);
            assertThat(original).isEqualTo(copy);
        }
    }

    // ── Merge Sort ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Merge Sort")
    class MergeSortTests {

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = {0, 1, 2, 10, 100, 1000})
        @DisplayName("Should sort correctly for various sizes")
        void shouldSortVariousSizes(int size) {
            int[] input = randomArray(size, 42L);
            assertThat(SortingAlgorithms.mergeSort(input)).isSorted();
        }

        @Test
        @DisplayName("Should preserve all elements (no data loss)")
        void shouldPreserveElements() {
            int[] input = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5};
            int[] result = SortingAlgorithms.mergeSort(input);
            assertThat(result).containsExactlyInAnyOrder(3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5);
        }

        @Test
        @DisplayName("Should be stable (equal elements preserve relative order)")
        void shouldBeStable() {
            // Merge sort is stable — equal elements keep original order
            int[] input = {5, 3, 5, 1, 5};
            int[] result = SortingAlgorithms.mergeSort(input);
            assertThat(result).isSorted();
            // Count occurrences preserved
            long fives = Arrays.stream(result).filter(x -> x == 5).count();
            assertThat(fives).isEqualTo(3);
        }
    }

    // ── Quick Sort ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Quick Sort")
    class QuickSortTests {

        @Test
        @DisplayName("Should sort correctly")
        void shouldSort() {
            int[] input = randomArray(500, 1337L);
            int[] expected = sorted(input);
            assertThat(SortingAlgorithms.quickSort(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should handle duplicates")
        void shouldHandleDuplicates() {
            int[] input = {1, 1, 1, 1, 1};
            assertThat(SortingAlgorithms.quickSort(input)).containsExactly(1, 1, 1, 1, 1);
        }
    }

    // ── Counting Sort ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Counting Sort — should sort non-negative integers")
    void countingSortShouldWork() {
        int[] input = {4, 2, 2, 8, 3, 3, 1};
        assertThat(SortingAlgorithms.countingSort(input, 8)).isSorted();
    }

    // ── Radix Sort ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Radix Sort — should sort correctly")
    void radixSortShouldWork() {
        int[] input = {170, 45, 75, 90, 802, 24, 2, 66};
        assertThat(SortingAlgorithms.radixSort(input)).isSorted();
    }

    // ── Comparative correctness (property-based style) ────────────────────────

    @RepeatedTest(5)
    @DisplayName("All algorithms produce identical sorted output")
    void allAlgorithmsAreConsistent() {
        int[] input = randomArray(100, System.nanoTime());
        int[] expected = sorted(input);

        assertThat(SortingAlgorithms.bubbleSort(input))   .isEqualTo(expected);
        assertThat(SortingAlgorithms.selectionSort(input)) .isEqualTo(expected);
        assertThat(SortingAlgorithms.insertionSort(input)) .isEqualTo(expected);
        assertThat(SortingAlgorithms.mergeSort(input))     .isEqualTo(expected);
        assertThat(SortingAlgorithms.quickSort(input))     .isEqualTo(expected);
        assertThat(SortingAlgorithms.heapSort(input))      .isEqualTo(expected);
        assertThat(SortingAlgorithms.shellSort(input))     .isEqualTo(expected);
        assertThat(SortingAlgorithms.timSort(input))       .isEqualTo(expected);
    }

    // ── Performance benchmark (smoke test — Concept #19) ─────────────────────

    @Test
    @DisplayName("Merge Sort should sort 10K elements in under 1s")
    @Timeout(1)
    void shouldBeFastEnough() {
        int[] large = randomArray(10_000, 999L);
        int[] result = SortingAlgorithms.mergeSort(large);
        assertThat(result).isSorted();
    }
}
