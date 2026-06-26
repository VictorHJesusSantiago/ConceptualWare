package com.conceptualware.core.algorithms;

import org.junit.jupiter.api.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;

/**
 * Concept #5  — Algorithms: Search, IntroSort, Branch & Bound,
 *               Compression, Cryptography, Consensus, Amortized Analysis
 * Concept #19 — TDD: AAA, property-based invariants
 */
@DisplayName("Category 5 — Algorithms: Complete Test Suite")
class AlgorithmsTest {

    // ── Search Algorithms ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Search Algorithms")
    class SearchTests {

        private final int[] SORTED = {1, 3, 5, 7, 9, 11, 13, 15, 17, 19};

        @Test void linearSearchFound()    { assertThat(SearchAlgorithms.linearSearch(SORTED, 7)).isEqualTo(3); }
        @Test void linearSearchNotFound() { assertThat(SearchAlgorithms.linearSearch(SORTED, 6)).isEqualTo(-1); }

        @Test void binarySearchFound()    { assertThat(SearchAlgorithms.binarySearch(SORTED, 11)).isEqualTo(5); }
        @Test void binarySearchNotFound() { assertThat(SearchAlgorithms.binarySearch(SORTED, 6)).isEqualTo(-1); }
        @Test void binarySearchFirst()    { assertThat(SearchAlgorithms.binarySearch(SORTED, 1)).isEqualTo(0); }
        @Test void binarySearchLast()     { assertThat(SearchAlgorithms.binarySearch(SORTED, 19)).isEqualTo(9); }

        @Test
        @DisplayName("Binary search insertion point")
        void binarySearchInsertionPoint() {
            assertThat(SearchAlgorithms.binarySearchInsertionPoint(SORTED, 6)).isEqualTo(3);
            assertThat(SearchAlgorithms.binarySearchInsertionPoint(SORTED, 1)).isEqualTo(0);
            assertThat(SearchAlgorithms.binarySearchInsertionPoint(SORTED, 20)).isEqualTo(10);
        }

        @Test void exponentialSearchFound()    { assertThat(SearchAlgorithms.exponentialSearch(SORTED, 13)).isEqualTo(6); }
        @Test void exponentialSearchNotFound() { assertThat(SearchAlgorithms.exponentialSearch(SORTED, 2)).isEqualTo(-1); }

        @Test void jumpSearchFound()    { assertThat(SearchAlgorithms.jumpSearch(SORTED, 15)).isEqualTo(7); }
        @Test void jumpSearchNotFound() { assertThat(SearchAlgorithms.jumpSearch(SORTED, 4)).isEqualTo(-1); }

        @Test void interpolationSearchFound()    { assertThat(SearchAlgorithms.interpolationSearch(SORTED, 9)).isEqualTo(4); }
        @Test void interpolationSearchNotFound() { assertThat(SearchAlgorithms.interpolationSearch(SORTED, 10)).isEqualTo(-1); }

        @Test
        @DisplayName("Ternary search finds peak of unimodal function f(x)=-(x-5)^2")
        void ternarySearchPeak() {
            double peak = SearchAlgorithms.ternarySearchPeak(0, 10, x -> -(x - 5) * (x - 5));
            assertThat(peak).isCloseTo(5.0, offset(1e-9));
        }

        @Test
        @DisplayName("BFS finds shortest path")
        void bfsShortestPath() {
            Map<Integer, List<Integer>> graph = Map.of(
                0, List.of(1, 2),
                1, List.of(0, 3),
                2, List.of(0, 4),
                3, List.of(1),
                4, List.of(2)
            );
            List<Integer> visited = SearchAlgorithms.bfs(graph, 0);
            assertThat(visited).containsExactly(0, 1, 2, 3, 4);
        }

        @Test
        @DisplayName("DFS traverses all nodes")
        void dfsTraversal() {
            Map<Integer, List<Integer>> graph = Map.of(
                0, List.of(1, 2),
                1, List.of(3),
                2, List.of(4),
                3, List.of(),
                4, List.of()
            );
            List<Integer> visited = SearchAlgorithms.dfs(graph, 0);
            assertThat(visited).containsExactlyInAnyOrder(0, 1, 2, 3, 4);
        }

        @Test
        @DisplayName("Bidirectional BFS finds shortest path")
        void bidirectionalBFS() {
            Map<Integer, List<Integer>> graph = new HashMap<>();
            for (int i = 0; i < 6; i++) graph.put(i, new ArrayList<>());
            // Linear chain: 0-1-2-3-4-5
            int[][] edges = {{0,1},{1,2},{2,3},{3,4},{4,5}};
            for (int[] e : edges) {
                graph.get(e[0]).add(e[1]);
                graph.get(e[1]).add(e[0]);
            }
            assertThat(SearchAlgorithms.bidirectionalBFS(graph, 0, 5)).isEqualTo(5);
            assertThat(SearchAlgorithms.bidirectionalBFS(graph, 0, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("A* finds optimal path with admissible heuristic")
        void aStarPathfinding() {
            // Simple grid: nodes 0-4 in a line with weight 1
            Map<Integer, List<int[]>> graph = new HashMap<>();
            for (int i = 0; i < 4; i++) {
                graph.put(i, new ArrayList<>());
                graph.get(i).add(new int[]{i + 1, 1});
            }
            graph.put(4, List.of());

            // h(n) = distance to goal (admissible)
            Optional<List<Integer>> path = SearchAlgorithms.aStar(graph, 0, 4, n -> 4 - n);
            assertThat(path).isPresent();
            assertThat(path.get()).containsExactly(0, 1, 2, 3, 4);
        }
    }

    // ── IntroSort ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IntroSort (Hybrid QuickSort + HeapSort + InsertionSort)")
    class IntroSortTests {

        @Test
        @DisplayName("Sorts random array")
        void sortsRandom() {
            int[] arr = {5, 3, 8, 1, 9, 2, 7, 4, 6};
            IntroSort.sort(arr);
            assertThat(arr).isSorted();
        }

        @Test
        @DisplayName("Sorts already-sorted array")
        void sortsSorted() {
            int[] arr = {1, 2, 3, 4, 5};
            IntroSort.sort(arr);
            assertThat(arr).isSorted();
        }

        @Test
        @DisplayName("Sorts reverse-sorted array (worst case for naive QuickSort)")
        void sortsReverse() {
            int[] arr = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
            IntroSort.sort(arr);
            assertThat(arr).isSorted();
        }

        @Test
        @DisplayName("Sorts duplicates")
        void sortsDuplicates() {
            int[] arr = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5};
            IntroSort.sort(arr);
            assertThat(arr).isSorted();
        }

        @Test
        @DisplayName("Empty and single-element arrays are no-ops")
        void emptyAndSingle() {
            int[] empty = {};
            int[] single = {42};
            IntroSort.sort(empty);
            IntroSort.sort(single);
            assertThat(empty).isEmpty();
            assertThat(single).containsExactly(42);
        }

        @Test
        @DisplayName("HeapSort standalone sorts correctly")
        void heapSort() {
            int[] arr = {7, 3, 5, 1, 8, 2, 4, 6};
            IntroSort.heapSort(arr);
            assertThat(arr).isSorted();
        }

        @Test
        @DisplayName("Complexity table contains all 6 algorithms")
        void complexityTable() {
            assertThat(IntroSort.SortComplexity.all()).hasSize(6);
        }
    }

    // ── Branch and Bound ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Branch and Bound")
    class BranchAndBoundTests {

        @Test
        @DisplayName("Knapsack — classic 4-item problem")
        void knapsackClassic() {
            int[] weights = {2, 3, 4, 5};
            int[] values  = {3, 4, 5, 6};
            int result = BranchAndBound.knapsack(weights, values, 8);
            assertThat(result).isEqualTo(10); // items 0+1+3: 3+4+... or items 0+1+2: 3+4+5=12? check
            // With capacity 8: best is items 0(w2,v3)+1(w3,v4)+3(w5,v6) = w10 > cap
            // items 0+1: w5,v7 | items 0+2: w6,v8 | items 0+1+no: w5,v7
            // items 0+3: w7,v9 | items 1+2: w7,v9 | items 0+1+? 5+3=8 ok: v7+item2? no 5+4>8
            // Actually: 0+3: w2+5=7,v3+6=9 | 1+2: w3+4=7,v4+5=9 | items 0+1+2: w9>8
            // items 2+0: w6,v8 items 0+1: w5,v7 ... max should be 10 (items 1+3: w8,v10)
            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("Knapsack — zero capacity returns 0")
        void knapsackZeroCapacity() {
            int[] weights = {1, 2, 3};
            int[] values  = {10, 20, 30};
            assertThat(BranchAndBound.knapsack(weights, values, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("TSP — 3-city symmetric graph")
        void tspThreeCities() {
            int[][] dist = {
                {0, 10, 15},
                {10, 0, 20},
                {15, 20, 0}
            };
            // Only one tour: 0→1→2→0 = 10+20+15 = 45
            assertThat(BranchAndBound.tspBranchAndBound(dist)).isEqualTo(45);
        }

        @Test
        @DisplayName("N-Queens — 4x4 board has 2 solutions")
        void nQueens4x4() {
            List<int[]> solutions = BranchAndBound.nQueens(4);
            assertThat(solutions).hasSize(2);
        }

        @Test
        @DisplayName("N-Queens — 8x8 board has 92 solutions")
        void nQueens8x8() {
            List<int[]> solutions = BranchAndBound.nQueens(8);
            assertThat(solutions).hasSize(92);
        }
    }

    // ── Compression Algorithms ────────────────────────────────────────────────

    @Nested
    @DisplayName("Compression Algorithms")
    class CompressionTests {

        @Test
        @DisplayName("LZ77 compress then decompress returns original")
        void lz77RoundTrip() {
            String original = "abracadabrabrabra";
            var tokens = CompressionAlgorithms.lz77Compress(original, 12, 8);
            String restored = CompressionAlgorithms.lz77Decompress(tokens);
            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("LZ77 compresses repetitive text (fewer tokens than chars)")
        void lz77CompressesRepetitive() {
            String input = "aaaaaaaaaa"; // 10 'a's
            var tokens = CompressionAlgorithms.lz77Compress(input, 8, 8);
            assertThat(tokens.size()).isLessThan(input.length());
        }

        @Test
        @DisplayName("LZW compress then decompress returns original")
        void lzwRoundTrip() {
            String original = "TOBEORNOTTOBE";
            List<Integer> codes = CompressionAlgorithms.lzwCompress(original);
            String restored = CompressionAlgorithms.lzwDecompress(codes);
            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("LZW codes are fewer than input chars for repetitive text")
        void lzwCompressesRepetitive() {
            String input = "AAABBBAAABBBAAABBB";
            List<Integer> codes = CompressionAlgorithms.lzwCompress(input);
            assertThat(codes.size()).isLessThan(input.length());
        }

        @Test
        @DisplayName("DEFLATE compress then decompress returns original")
        void deflateRoundTrip() throws Exception {
            byte[] original = "ConceptualWare — 800+ software engineering concepts".getBytes();
            byte[] compressed = CompressionAlgorithms.deflateCompress(original);
            byte[] restored   = CompressionAlgorithms.deflateDecompress(compressed);
            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("DEFLATE compresses repetitive data")
        void deflateCompresses() throws Exception {
            byte[] repetitive = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes();
            byte[] compressed = CompressionAlgorithms.deflateCompress(repetitive);
            assertThat(compressed.length).isLessThan(repetitive.length);
        }

        @Test
        @DisplayName("Huffman codes — most frequent char gets shortest code")
        void huffmanFrequencyProperty() {
            String input = "aaaaabbbcc"; // a=5, b=3, c=2
            Map<Character, String> codes = CompressionAlgorithms.buildHuffmanCodes(input);
            assertThat(codes.get('a').length()).isLessThanOrEqualTo(codes.get('b').length());
            assertThat(codes.get('b').length()).isLessThanOrEqualTo(codes.get('c').length());
        }

        @Test
        @DisplayName("Huffman encode produces bit string")
        void huffmanEncode() {
            String input = "hello";
            Map<Character, String> codes = CompressionAlgorithms.buildHuffmanCodes(input);
            String encoded = CompressionAlgorithms.huffmanEncode(input, codes);
            assertThat(encoded).matches("[01]+");
        }

        @Test
        @DisplayName("RLE encodes repeated characters")
        void rleEncode() {
            assertThat(CompressionAlgorithms.rleEncode("aaabbbcc")).isEqualTo("a3b3c2");
            assertThat(CompressionAlgorithms.rleEncode("abc")).isEqualTo("abc");
        }
    }

    // ── Cryptographic Algorithms ──────────────────────────────────────────────

    @Nested
    @DisplayName("Cryptographic Algorithms")
    class CryptographicTests {

        @Test
        @DisplayName("SHA-256 produces 64-char hex string")
        void sha256Length() throws Exception {
            String hash = CryptographicAlgorithms.sha256Hex("hello");
            assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("SHA-256 is deterministic")
        void sha256Deterministic() throws Exception {
            assertThat(CryptographicAlgorithms.sha256Hex("test"))
                .isEqualTo(CryptographicAlgorithms.sha256Hex("test"));
        }

        @Test
        @DisplayName("SHA-256 avalanche effect: 1-bit change alters ~50% output bits")
        void sha256Avalanche() throws Exception {
            var result = CryptographicAlgorithms.demonstrateAvalanche("hello");
            assertThat(result.bitsChanged()).isBetween(80, 180); // roughly 50% of 256 bits
        }

        @Test
        @DisplayName("AES-GCM encrypt then decrypt recovers plaintext")
        void aesGcmRoundTrip() throws Exception {
            var key       = CryptographicAlgorithms.generateAESKey();
            var plaintext = "ConceptualWare secret".getBytes();
            var encrypted = CryptographicAlgorithms.aesGcmEncrypt(plaintext, key);
            var decrypted = CryptographicAlgorithms.aesGcmDecrypt(encrypted, key);
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("AES-GCM: different encryptions of same plaintext produce different ciphertext (IV randomness)")
        void aesGcmIVRandomness() throws Exception {
            var key = CryptographicAlgorithms.generateAESKey();
            var pt  = "same plaintext".getBytes();
            var c1  = CryptographicAlgorithms.aesGcmEncrypt(pt, key);
            var c2  = CryptographicAlgorithms.aesGcmEncrypt(pt, key);
            assertThat(c1).isNotEqualTo(c2); // different random IVs
        }

        @Test
        @DisplayName("RSA encrypt then decrypt recovers plaintext")
        void rsaRoundTrip() throws Exception {
            var kp        = CryptographicAlgorithms.generateRSAKeyPair();
            var plaintext = "RSA OAEP test".getBytes();
            var encrypted = CryptographicAlgorithms.rsaEncrypt(plaintext, kp.getPublic());
            var decrypted = CryptographicAlgorithms.rsaDecrypt(encrypted, kp.getPrivate());
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("RSA sign and verify")
        void rsaSignVerify() throws Exception {
            var kp   = CryptographicAlgorithms.generateRSAKeyPair();
            var data = "important message".getBytes();
            var sig  = CryptographicAlgorithms.rsaSign(data, kp.getPrivate());
            assertThat(CryptographicAlgorithms.rsaVerify(data, sig, kp.getPublic())).isTrue();
            // Tamper with data
            data[0] ^= 1;
            assertThat(CryptographicAlgorithms.rsaVerify(data, sig, kp.getPublic())).isFalse();
        }

        @Test
        @DisplayName("HMAC-SHA256 produces consistent MAC")
        void hmacConsistent() throws Exception {
            byte[] key = new byte[32];
            new java.security.SecureRandom().nextBytes(key);
            String mac1 = CryptographicAlgorithms.hmacSha256("message", key);
            String mac2 = CryptographicAlgorithms.hmacSha256("message", key);
            assertThat(mac1).isEqualTo(mac2).hasSize(64);
        }

        @Test
        @DisplayName("PBKDF2 produces 32-byte key")
        void pbkdf2KeyLength() throws Exception {
            byte[] key = CryptographicAlgorithms.pbkdf2(
                "password".toCharArray(), CryptographicAlgorithms.generateSalt(), 100_000, 256);
            assertThat(key).hasSize(32);
        }

        @Test
        @DisplayName("Amortized analysis: avg cost per insertion ≤ 3")
        void amortizedAnalysis() {
            var result = CryptographicAlgorithms.analyzeArrayResizing(1000);
            assertThat(result.amortizedCostPerOp()).isLessThan(3.0);
            assertThat(result.operations()).isEqualTo(1000);
        }
    }

    // ── Consensus Algorithms ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Consensus Algorithms — Raft and Paxos")
    class ConsensusTests {

        @Test
        @DisplayName("Raft — leader election succeeds with 3-node cluster")
        void raftLeaderElection() {
            List<ConsensusAlgorithms.RaftNode> cluster = ConsensusAlgorithms.createRaftCluster(3);
            boolean won = cluster.get(0).startElection();
            assertThat(won).isTrue();
            assertThat(cluster.get(0).getState()).isEqualTo(ConsensusAlgorithms.RaftState.LEADER);
        }

        @Test
        @DisplayName("Raft — leader replicates log entry to majority")
        void raftLogReplication() {
            List<ConsensusAlgorithms.RaftNode> cluster = ConsensusAlgorithms.createRaftCluster(3);
            cluster.get(0).startElection();
            boolean committed = cluster.get(0).replicateCommand("SET x=1");
            assertThat(committed).isTrue();
            assertThat(cluster.get(0).getLog()).contains("SET x=1");
        }

        @Test
        @DisplayName("Raft — non-leader cannot replicate commands")
        void raftNonLeaderRejects() {
            List<ConsensusAlgorithms.RaftNode> cluster = ConsensusAlgorithms.createRaftCluster(3);
            // node(1) is FOLLOWER — should reject replicate
            assertThat(cluster.get(1).replicateCommand("SET y=2")).isFalse();
        }

        @Test
        @DisplayName("Paxos — single proposer reaches consensus")
        void paxosSingleProposer() {
            ConsensusAlgorithms.PaxosCluster cluster =
                new ConsensusAlgorithms.PaxosCluster(3, 1);
            Optional<String> result = cluster.runConsensus("value-A", 0);
            assertThat(result).contains("value-A");
        }

        @Test
        @DisplayName("Paxos — accepted value matches consensus value")
        void paxosAcceptorsAgree() {
            ConsensusAlgorithms.PaxosCluster cluster =
                new ConsensusAlgorithms.PaxosCluster(5, 1);
            cluster.runConsensus("agreed-value", 0);
            List<Optional<String>> accepted = cluster.getAcceptedValues();
            long agreeing = accepted.stream().filter(v -> v.filter("agreed-value"::equals).isPresent()).count();
            assertThat(agreeing).isGreaterThanOrEqualTo(3); // majority
        }

        @Test
        @DisplayName("CAP classifications cover all major systems")
        void capClassifications() {
            assertThat(ConsensusAlgorithms.CAPClassification.all())
                .hasSizeGreaterThanOrEqualTo(4)
                .extracting(ConsensusAlgorithms.CAPClassification::system)
                .contains("Raft", "Paxos", "Cassandra");
        }
    }
}
