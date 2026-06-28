package com.conceptualware.testing;

import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import com.conceptualware.core.algorithms.string.StringAlgorithms;
import com.conceptualware.core.algorithms.dp.DynamicProgramming;
import com.conceptualware.core.datastructures.hash.HashTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — Fuzzing / Fuzz Testing:
 *   Generates random, boundary, and adversarial inputs to uncover edge cases.
 *   Property-based testing: define INVARIANTS that must hold for ALL inputs,
 *   then test against many random inputs (poor man's QuickCheck / jqwik).
 *
 * Properties tested:
 *   1. Sorting is idempotent: sort(sort(a)) == sort(a)
 *   2. Sorting is a permutation: output has same elements as input
 *   3. Sorting produces a sorted sequence
 *   4. KMP finds all matches that naive search also finds
 *   5. HashTable get(put(k,v)) == v for random keys
 */
@DisplayName("Fuzz Testing — Property-Based Invariant Verification")
class FuzzingTest {

    private static final int FUZZ_ROUNDS = 200;
    private static final Random RNG = new Random(42L);

    // ── Fuzz input generators ─────────────────────────────────────────────────

    static Stream<int[]> randomArrays() {
        return Stream.generate(() -> {
            int size = RNG.nextInt(200);      // 0..199 elements
            return RNG.ints(size, -10_000, 10_000).toArray();
        }).limit(FUZZ_ROUNDS);
    }

    static Stream<int[]> edgeCaseArrays() {
        return Stream.of(
            new int[]{},
            new int[]{0},
            new int[]{Integer.MAX_VALUE},
            new int[]{Integer.MIN_VALUE},
            new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE},
            new int[]{1, 1, 1, 1, 1},          // all equal
            new int[]{5, 4, 3, 2, 1},           // reverse sorted
            new int[]{1, 2, 3, 4, 5},           // already sorted
            new int[]{-5, -4, -3, -2, -1},      // all negative
            RNG.ints(1000, 0, 3).toArray()      // high duplicates, large
        );
    }

    // ── Property: sorting is correct (isSorted) ───────────────────────────────

    @ParameterizedTest(name = "random array #{index}")
    @MethodSource("randomArrays")
    @DisplayName("Merge Sort — sorted output for random input")
    void mergeSortProducesSortedOutput(int[] arr) {
        // Property: result is sorted
        assertThat(SortingAlgorithms.mergeSort(arr)).isSorted();
    }

    @ParameterizedTest(name = "edge case #{index}")
    @MethodSource("edgeCaseArrays")
    @DisplayName("All algorithms — sorted output for edge case inputs")
    void allAlgorithmsHandleEdgeCases(int[] arr) {
        assertThat(SortingAlgorithms.bubbleSort(arr)).isSorted();
        assertThat(SortingAlgorithms.mergeSort(arr)).isSorted();
        assertThat(SortingAlgorithms.quickSort(arr)).isSorted();
        assertThat(SortingAlgorithms.heapSort(arr)).isSorted();
    }

    // ── Property: sorting is a permutation ────────────────────────────────────

    @RepeatedTest(50)
    @DisplayName("Sort output is a permutation of input (no data loss or corruption)")
    void sortingIsPermutation() {
        int[] arr = RNG.ints(RNG.nextInt(150), -500, 500).toArray();
        int[] sorted = SortingAlgorithms.mergeSort(arr);

        // Same length
        assertThat(sorted).hasSameSizeAs(arr);

        // Same elements (order-independent)
        int[] arrCopy = arr.clone(); Arrays.sort(arrCopy);
        int[] sortedCopy = sorted.clone(); Arrays.sort(sortedCopy);
        assertThat(sortedCopy).isEqualTo(arrCopy);
    }

    // ── Property: sort is idempotent: sort(sort(x)) == sort(x) ───────────────

    @RepeatedTest(30)
    @DisplayName("Merge Sort is idempotent: sort(sort(x)) == sort(x)")
    void sortingIsIdempotent() {
        int[] arr = RNG.ints(RNG.nextInt(100)).toArray();
        int[] sorted    = SortingAlgorithms.mergeSort(arr);
        int[] reSorted  = SortingAlgorithms.mergeSort(sorted);
        assertThat(reSorted).isEqualTo(sorted);
    }

    // ── Property: KMP finds same matches as naive search ─────────────────────

    @RepeatedTest(30)
    @DisplayName("KMP pattern match count equals naive search count")
    void kmpMatchesNaiveSearch() {
        String alphabet = "abcde";
        String text    = randomString(alphabet, 50 + RNG.nextInt(100));
        String pattern = randomString(alphabet, 1 + RNG.nextInt(5));

        int kmpCount    = StringAlgorithms.kmpSearch(text, pattern).size();
        int naiveCount  = naiveCount(text, pattern);

        assertThat(kmpCount).isEqualTo(naiveCount);
    }

    // ── Property: HashTable get(put(k, v)) == v ───────────────────────────────

    @RepeatedTest(20)
    @DisplayName("HashTable — fuzz put/get with random String keys")
    void hashTableGetAfterPut() {
        HashTable<String, Integer> table = new HashTable<>();
        Map<String, Integer> reference = new HashMap<>();

        for (int i = 0; i < 100; i++) {
            String key = randomString("abcdefghij", 1 + RNG.nextInt(8));
            int value  = RNG.nextInt(1000);
            table.put(key, value);
            reference.put(key, value);
        }

        // Property: every key we inserted should be retrievable
        for (Map.Entry<String, Integer> entry : reference.entrySet()) {
            assertThat(table.get(entry.getKey())).isEqualTo(entry.getValue());
        }
    }

    // ── Property: Fibonacci is always non-negative ────────────────────────────

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0,1,2,3,5,8,13,21,34,50})
    @DisplayName("Fibonacci — always returns non-negative value")
    void fibonacciNonNegative(int n) {
        assertThat(DynamicProgramming.fibTabulation(n)).isGreaterThanOrEqualTo(0);
    }

    // ── Adversarial inputs — known tricky cases ───────────────────────────────

    @Test
    @DisplayName("Fuzzing — adversarial: Quick Sort with all identical elements")
    void quickSortAllIdentical() {
        int[] arr = new int[1000];
        Arrays.fill(arr, 7);
        assertThat(SortingAlgorithms.quickSort(arr)).isSorted();
    }

    @Test
    @DisplayName("Fuzzing — adversarial: KMP with pattern longer than text")
    void kmpPatternLongerThanText() {
        assertThat(StringAlgorithms.kmpSearch("ab", "abcdef")).isEmpty();
    }

    @Test
    @DisplayName("Fuzzing — adversarial: KMP empty pattern")
    void kmpEmptyPattern() {
        // Should find nothing, not throw
        assertThatCode(() -> StringAlgorithms.kmpSearch("hello", ""))
            .doesNotThrowAnyException();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String randomString(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        return sb.toString();
    }

    private int naiveCount(String text, String pattern) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) { count++; idx++; }
        return count;
    }
}
