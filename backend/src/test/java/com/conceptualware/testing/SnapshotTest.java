package com.conceptualware.testing;

import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import com.conceptualware.core.algorithms.dp.DynamicProgramming;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — Snapshot Testing:
 *   On first run, the test CREATES the snapshot.
 *   On subsequent runs, it COMPARES actual output to the snapshot.
 *   If output changes, the test FAILS with a clear diff — the developer
 *   must explicitly approve the change by deleting/updating the snapshot.
 *
 *   This is the pattern used by Jest (frontend) and ApprovalTests (Java).
 *   Here we implement the pattern manually so the concept is fully demonstrated.
 *
 * DELETE SNAPSHOT FILES to force regeneration (equivalent to jest --updateSnapshot).
 */
@DisplayName("Snapshot Tests — Output Stabilization")
class SnapshotTest {

    private static final Path SNAPSHOT_DIR = Path.of("src/test/resources/snapshots");
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @BeforeAll
    static void ensureSnapshotDir() throws IOException {
        Files.createDirectories(SNAPSHOT_DIR);
    }

    // ── §1  Snapshot: algorithm execution result ──────────────────────────────

    @Test
    @DisplayName("Bubble sort result snapshot")
    void bubbleSortResultSnapshot() throws Exception {
        int[] input  = {5, 3, 8, 1, 9, 2, 7, 4, 6};
        int[] output = SortingAlgorithms.bubbleSort(input);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("algorithm", "bubble-sort");
        snapshot.put("input", Arrays.stream(input).boxed().toList());
        snapshot.put("output", Arrays.stream(output).boxed().toList());
        snapshot.put("inputSize", input.length);
        snapshot.put("timeComplexity", "O(n²)");
        snapshot.put("spaceComplexity", "O(1)");

        assertMatchesSnapshot("bubble-sort-result.json", snapshot);
    }

    // ── §2  Snapshot: DP results table ────────────────────────────────────────

    @Test
    @DisplayName("Fibonacci sequence snapshot (indices 0-15)")
    void fibonacciSnapshotTable() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i <= 15; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("n", i);
            row.put("fib", DynamicProgramming.fibTabulation(i));
            rows.add(row);
        }
        snapshot.put("algorithm", "fibonacci-tabulation");
        snapshot.put("sequence", rows);
        assertMatchesSnapshot("fibonacci-table.json", snapshot);
    }

    // ── §3  Snapshot: N-Queens solution count ─────────────────────────────────

    @Test
    @DisplayName("N-Queens solution counts snapshot (n=1..8)")
    void nQueensCountSnapshot() throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (int n = 1; n <= 8; n++) {
            snapshot.put("n=" + n, DynamicProgramming.solveNQueens(n).size());
        }
        assertMatchesSnapshot("n-queens-counts.json", snapshot);
    }

    // ── §4  Snapshot: complexity table ────────────────────────────────────────

    @Test
    @DisplayName("Sorting complexity table snapshot")
    void complexityTableSnapshot() throws Exception {
        var table = SortingAlgorithms.complexityTable();
        Map<String, Object> snapshot = new TreeMap<>(
            Map.of("algorithms", new TreeMap<>(table))
        );
        assertMatchesSnapshot("complexity-table.json", snapshot);
    }

    // ── Snapshot engine ───────────────────────────────────────────────────────

    private void assertMatchesSnapshot(String filename, Object actual) throws IOException {
        Path snapshotFile = SNAPSHOT_DIR.resolve(filename);
        String actualJson = MAPPER.writeValueAsString(actual);

        if (!Files.exists(snapshotFile)) {
            // First run: create snapshot and pass
            Files.writeString(snapshotFile, actualJson);
            return;
        }

        String storedJson = Files.readString(snapshotFile);
        assertThat(actualJson)
            .as("""
                Snapshot '%s' differs from stored snapshot.
                To approve the change, delete the snapshot file and re-run.
                Stored: %s
                Actual: %s
                """, filename, storedJson, actualJson)
            .isEqualTo(storedJson);
    }
}
