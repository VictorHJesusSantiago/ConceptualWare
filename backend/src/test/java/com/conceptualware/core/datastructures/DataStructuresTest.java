package com.conceptualware.core.datastructures;

import com.conceptualware.core.datastructures.tree.*;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #4  — Data Structures: Red-Black Tree, B-Tree/B+Tree, Skip List,
 *               Sparse Table, Treap, Splay Tree, K-D Tree
 * Concept #19 — TDD: property-based testing, invariant verification
 */
@DisplayName("Advanced Data Structures — Complete Test Suite")
class DataStructuresTest {

    // ── Red-Black Tree ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Red-Black Tree")
    class RedBlackTreeTests {

        @Test
        @DisplayName("Empty tree is a valid RB tree")
        void emptyTreeIsValid() {
            RedBlackTree<Integer, String> tree = new RedBlackTree<>();
            assertThat(tree.isValidRedBlackTree()).isTrue();
        }

        @Test
        @DisplayName("Insert maintains RB invariants")
        void insertMaintainsInvariants() {
            RedBlackTree<Integer, String> tree = new RedBlackTree<>();
            int[] values = {10, 20, 30, 15, 25, 5, 1, 7};
            for (int v : values) tree.insert(v, "v" + v);
            assertThat(tree.isValidRedBlackTree()).isTrue();
        }

        @Test
        @DisplayName("In-order traversal is sorted")
        void inOrderIsSorted() {
            RedBlackTree<Integer, String> tree = new RedBlackTree<>();
            List<Integer> inputs = List.of(5, 3, 8, 1, 4, 7, 9, 2, 6);
            inputs.forEach(i -> tree.insert(i, "v" + i));
            List<Integer> inOrder = tree.inOrder();
            assertThat(inOrder).isSortedAccordingTo(Integer::compareTo);
        }

        @Test
        @DisplayName("Search finds inserted values")
        void searchFindsValues() {
            RedBlackTree<Integer, String> tree = new RedBlackTree<>();
            tree.insert(42, "answer");
            assertThat(tree.get(42)).contains("answer");
            assertThat(tree.get(99)).isEmpty();
        }

        @Test
        @DisplayName("Delete maintains RB invariants")
        void deleteMaintainsInvariants() {
            RedBlackTree<Integer, String> tree = new RedBlackTree<>();
            List.of(10, 20, 30, 15, 25, 5).forEach(i -> tree.insert(i, "v" + i));
            tree.delete(20);
            assertThat(tree.isValidRedBlackTree()).isTrue();
            assertThat(tree.contains(20)).isFalse();
        }

        @Test
        @DisplayName("Update existing key")
        void updateExistingKey() {
            RedBlackTree<Integer, String> tree = new RedBlackTree<>();
            tree.insert(1, "old");
            tree.insert(1, "new");
            assertThat(tree.get(1)).contains("new");
            assertThat(tree.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Size increments correctly")
        void sizeIncrements() {
            RedBlackTree<Integer, String> tree = new RedBlackTree<>();
            assertThat(tree.size()).isEqualTo(0);
            for (int i = 1; i <= 10; i++) tree.insert(i, "v");
            assertThat(tree.size()).isEqualTo(10);
        }
    }

    // ── B-Tree ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("B-Tree (min degree 3)")
    class BTreeTests {

        @Test
        @DisplayName("Search finds inserted keys")
        void searchFindsKeys() {
            BTree<Integer> tree = new BTree<>(3);
            for (int i = 1; i <= 20; i++) tree.insert(i);
            assertThat(tree.search(10)).isTrue();
            assertThat(tree.search(21)).isFalse();
        }

        @Test
        @DisplayName("In-order traversal is sorted after many inserts")
        void inOrderSorted() {
            BTree<Integer> tree = new BTree<>(2);
            List<Integer> inputs = List.of(5, 3, 8, 1, 4, 7, 9, 2, 6, 10);
            inputs.forEach(tree::insert);
            assertThat(tree.inOrder()).isSortedAccordingTo(Integer::compareTo);
        }

        @Test
        @DisplayName("Delete removes key correctly")
        void deleteRemovesKey() {
            BTree<Integer> tree = new BTree<>(3);
            for (int i = 1; i <= 10; i++) tree.insert(i);
            tree.delete(5);
            assertThat(tree.search(5)).isFalse();
            assertThat(tree.search(4)).isTrue();
        }
    }

    // ── B+Tree ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("B+Tree")
    class BPlusTreeTests {

        @Test
        @DisplayName("Search finds inserted key-value pairs")
        void searchFindsValues() {
            BTree.BPlusTree<Integer, String> tree = new BTree.BPlusTree<>(4);
            tree.insert(1, "one");
            tree.insert(2, "two");
            tree.insert(3, "three");
            assertThat(tree.search(2)).contains("two");
            assertThat(tree.search(99)).isEmpty();
        }

        @Test
        @DisplayName("Range query returns correct subset")
        void rangeQuery() {
            BTree.BPlusTree<Integer, String> tree = new BTree.BPlusTree<>(4);
            for (int i = 1; i <= 10; i++) tree.insert(i, "v" + i);
            List<String> range = tree.rangeQuery(3, 7);
            assertThat(range).hasSize(5).containsExactly("v3", "v4", "v5", "v6", "v7");
        }
    }

    // ── Skip List ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Skip List")
    class SkipListTests {

        @Test
        @DisplayName("Put and get basic operations")
        void putAndGet() {
            SkipList<Integer, String> sl = new SkipList<>();
            sl.put(10, "ten");
            sl.put(5, "five");
            sl.put(15, "fifteen");
            assertThat(sl.get(10)).contains("ten");
            assertThat(sl.get(99)).isEmpty();
        }

        @Test
        @DisplayName("Keys are ordered")
        void keysOrdered() {
            SkipList<Integer, String> sl = new SkipList<>();
            List.of(5, 2, 8, 1, 9, 3, 7, 4, 6).forEach(i -> sl.put(i, "v" + i));
            assertThat(sl.keys()).isSortedAccordingTo(Integer::compareTo);
        }

        @Test
        @DisplayName("Remove eliminates key")
        void removeKey() {
            SkipList<Integer, String> sl = new SkipList<>();
            sl.put(1, "a"); sl.put(2, "b"); sl.put(3, "c");
            assertThat(sl.remove(2)).isTrue();
            assertThat(sl.contains(2)).isFalse();
            assertThat(sl.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("Range returns correct entries")
        void rangeQuery() {
            SkipList<Integer, String> sl = new SkipList<>();
            for (int i = 1; i <= 10; i++) sl.put(i, "v" + i);
            List<Map.Entry<Integer, String>> range = sl.range(3, 6);
            assertThat(range).hasSize(4);
            assertThat(range.get(0).getKey()).isEqualTo(3);
            assertThat(range.get(3).getKey()).isEqualTo(6);
        }
    }

    // ── Sparse Table ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sparse Table (RMQ)")
    class SparseTableTests {

        @Test
        @DisplayName("Single element range minimum")
        void singleElement() {
            SparseTable st = new SparseTable(new int[]{42});
            assertThat(st.queryMin(0, 0)).isEqualTo(42);
        }

        @Test
        @DisplayName("Full range minimum")
        void fullRange() {
            SparseTable st = new SparseTable(new int[]{3, 1, 4, 1, 5, 9, 2, 6});
            assertThat(st.queryMin(0, 7)).isEqualTo(1);
        }

        @Test
        @DisplayName("Subrange minimum")
        void subrange() {
            SparseTable st = new SparseTable(new int[]{5, 2, 8, 3, 7, 1, 4});
            assertThat(st.queryMin(2, 5)).isEqualTo(1); // min of [8,3,7,1]
        }

        @Test
        @DisplayName("O(1) query: same result as brute force scan")
        void matchesBruteForce() {
            int[] arr = {9, 3, 7, 1, 8, 12, 10, 20, 15, 18, 5};
            SparseTable st = new SparseTable(arr);
            for (int l = 0; l < arr.length; l++) {
                for (int r = l; r < arr.length; r++) {
                    int expected = Integer.MAX_VALUE;
                    for (int k = l; k <= r; k++) expected = Math.min(expected, arr[k]);
                    assertThat(st.queryMin(l, r))
                        .as("min[%d,%d]", l, r)
                        .isEqualTo(expected);
                }
            }
        }

        @Test
        @DisplayName("Range Max Table works correctly")
        void rangeMax() {
            SparseTable.RangeMaxTable rmt = new SparseTable.RangeMaxTable(new int[]{3, 1, 4, 1, 5, 9, 2, 6});
            assertThat(rmt.queryMax(0, 7)).isEqualTo(9);
            assertThat(rmt.queryMax(0, 4)).isEqualTo(5);
        }
    }

    // ── Treap ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Treap")
    class TreapTests {

        @Test
        @DisplayName("Insert and contains")
        void insertAndContains() {
            Treap<Integer> treap = new Treap<>();
            treap.insert(5); treap.insert(3); treap.insert(8);
            assertThat(treap.contains(5)).isTrue();
            assertThat(treap.contains(99)).isFalse();
        }

        @Test
        @DisplayName("In-order traversal is sorted")
        void inOrderSorted() {
            Treap<Integer> treap = new Treap<>();
            List.of(4, 2, 7, 1, 3, 6, 8).forEach(treap::insert);
            assertThat(treap.inOrder()).isSortedAccordingTo(Integer::compareTo);
        }

        @Test
        @DisplayName("Delete removes key")
        void deleteKey() {
            Treap<Integer> treap = new Treap<>();
            treap.insert(5); treap.insert(3); treap.insert(8);
            treap.delete(3);
            assertThat(treap.contains(3)).isFalse();
            assertThat(treap.contains(5)).isTrue();
        }
    }

    // ── Splay Tree ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Splay Tree")
    class SplayTreeTests {

        @Test
        @DisplayName("Insert and get")
        void insertAndGet() {
            SplayTree<Integer, String> st = new SplayTree<>();
            st.insert(10, "ten");
            st.insert(5, "five");
            assertThat(st.get(10)).contains("ten");
            assertThat(st.get(99)).isEmpty();
        }

        @Test
        @DisplayName("In-order traversal is sorted")
        void inOrderSorted() {
            SplayTree<Integer, String> st = new SplayTree<>();
            List.of(6, 2, 8, 1, 4, 7, 9, 3, 5).forEach(i -> st.insert(i, "v" + i));
            assertThat(st.inOrder()).isSortedAccordingTo(Integer::compareTo);
        }

        @Test
        @DisplayName("Delete removes key")
        void deleteKey() {
            SplayTree<Integer, String> st = new SplayTree<>();
            st.insert(5, "a"); st.insert(3, "b"); st.insert(8, "c");
            assertThat(st.delete(3)).isTrue();
            assertThat(st.contains(3)).isFalse();
        }
    }

    // ── K-D Tree ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("K-D Tree (2D)")
    class KDTreeTests {

        private KDTree buildTestTree() {
            KDTree tree = new KDTree(2);
            List<KDTree.Point> points = List.of(
                new KDTree.Point(new double[]{1, 2}, "A"),
                new KDTree.Point(new double[]{3, 4}, "B"),
                new KDTree.Point(new double[]{5, 1}, "C"),
                new KDTree.Point(new double[]{7, 3}, "D"),
                new KDTree.Point(new double[]{2, 8}, "E")
            );
            tree.buildFromPoints(new ArrayList<>(points));
            return tree;
        }

        @Test
        @DisplayName("Nearest neighbor — finds closest point")
        void nearestNeighbor() {
            KDTree tree = buildTestTree();
            KDTree.Point query = new KDTree.Point(new double[]{3.5, 3.5});
            Optional<KDTree.Point> nn = tree.nearestNeighbor(query);
            assertThat(nn).isPresent();
            assertThat(nn.get().label()).isEqualTo("B"); // (3,4) is closest to (3.5,3.5)
        }

        @Test
        @DisplayName("Range search returns points in box")
        void rangeSearch() {
            KDTree tree = buildTestTree();
            List<KDTree.Point> results = tree.rangeSearch(
                new double[]{0, 0}, new double[]{4, 5}); // box [0-4, 0-5]
            assertThat(results).extracting(KDTree.Point::label)
                .containsExactlyInAnyOrder("A", "B");
        }

        @Test
        @DisplayName("Size matches inserted points")
        void sizeMatches() {
            KDTree tree = buildTestTree();
            assertThat(tree.size()).isEqualTo(5);
        }

        @Test
        @DisplayName("Empty tree nearest neighbor returns empty")
        void emptyTree() {
            KDTree tree = new KDTree(2);
            assertThat(tree.nearestNeighbor(new KDTree.Point(new double[]{0, 0}))).isEmpty();
        }
    }
}
