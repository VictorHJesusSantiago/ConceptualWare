package com.conceptualware.testing;

import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import com.conceptualware.core.algorithms.dp.DynamicProgramming;
import com.conceptualware.core.math.MathEngine;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — Golden File Testing:
 *   A golden file (also: "approval test" or "snapshot file") stores the
 *   expected output of a complex function in a file. The test compares
 *   actual output against the file. If output changes, the diff is obvious.
 *
 *   Used for: algorithm output tables, serialized data structures,
 *   complexity reports, HTML/JSON output that must not regress.
 *
 * UPDATE MODE: set system property -DupdateGoldenFiles=true to regenerate.
 */
@DisplayName("Golden File Tests — Output Regression Prevention")
class GoldenFileTest {

    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");
    private static final boolean UPDATE  = Boolean.getBoolean("updateGoldenFiles");

    @BeforeAll
    static void ensureGoldenDir() throws IOException {
        Files.createDirectories(GOLDEN_DIR);
    }

    // ── §1  Sorting complexity table ─────────────────────────────────────────

    @Test
    @DisplayName("Complexity table output matches golden file")
    void sortingComplexityTableMatchesGolden() throws IOException {
        String actual = renderComplexityTable();
        assertMatchesGolden("sorting-complexity-table.txt", actual);
    }

    private String renderComplexityTable() {
        var table = SortingAlgorithms.complexityTable();
        var sb = new StringBuilder();
        sb.append(String.format("%-20s %-15s %-15s %-15s %-10s %-8s%n",
            "Algorithm", "Best", "Average", "Worst", "Space", "Stable"));
        sb.append("-".repeat(85)).append("\n");

        new TreeMap<>(table).forEach((name, info) ->
            sb.append(String.format("%-20s %-15s %-15s %-15s %-10s %-8s%n",
                info.name(), info.best(), info.average(), info.worst(),
                info.spaceComplexity(), info.stable()))
        );
        return sb.toString();
    }

    // ── §2  Fibonacci sequence ────────────────────────────────────────────────

    @Test
    @DisplayName("Fibonacci(0..20) sequence matches golden file")
    void fibonacciSequenceMatchesGolden() throws IOException {
        var sb = new StringBuilder("n,fib(n)\n");
        for (int i = 0; i <= 20; i++) {
            sb.append(i).append(",").append(DynamicProgramming.fibTabulation(i)).append("\n");
        }
        assertMatchesGolden("fibonacci-sequence.csv", sb.toString());
    }

    // ── §3  Math engine: prime sieve output ──────────────────────────────────

    @Test
    @DisplayName("Sieve of Eratosthenes primes up to 100 match golden file")
    void primesSieveMatchesGolden() throws IOException {
        MathEngine engine = new MathEngine();
        List<Integer> primes = engine.sieveOfEratosthenes(100);
        String actual = "Primes up to 100:\n" + primes.toString() + "\nCount: " + primes.size() + "\n";
        assertMatchesGolden("sieve-100.txt", actual);
    }

    // ── §4  Sorting output on fixed dataset ───────────────────────────────────

    @Test
    @DisplayName("Merge sort of fixed input matches golden file")
    void mergeSortFixedInputMatchesGolden() throws IOException {
        int[] input = {64, 34, 25, 12, 22, 11, 90, 55, 1, 100, 3};
        int[] sorted = SortingAlgorithms.mergeSort(input);
        String actual = "Input:  " + Arrays.toString(input) + "\n"
                      + "Sorted: " + Arrays.toString(sorted) + "\n";
        assertMatchesGolden("merge-sort-fixed.txt", actual);
    }

    // ── Golden file assertion engine ─────────────────────────────────────────

    private void assertMatchesGolden(String filename, String actual) throws IOException {
        Path goldenFile = GOLDEN_DIR.resolve(filename);

        if (UPDATE || !Files.exists(goldenFile)) {
            Files.writeString(goldenFile, actual);
            if (!UPDATE) {
                // First run: golden file created, test passes
                return;
            }
        }

        String expected = Files.readString(goldenFile);
        assertThat(actual)
            .as("Output changed vs golden file '%s'. Run with -DupdateGoldenFiles=true to update.", filename)
            .isEqualTo(expected);
    }
}
