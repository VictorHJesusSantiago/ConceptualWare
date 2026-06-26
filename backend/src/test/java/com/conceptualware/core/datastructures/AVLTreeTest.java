package com.conceptualware.core.datastructures;

import com.conceptualware.core.datastructures.tree.AVLTree;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Concept #4  — Data Structures: AVL Tree self-balancing invariant
 * Concept #19 — Testing: property-based style (invariants hold after every mutation)
 */
@DisplayName("AVL Tree — Self-Balancing Invariant Tests")
class AVLTreeTest {

    private AVLTree<Integer> tree;

    @BeforeEach
    void setUp() {
        tree = new AVLTree<>();
    }

    @Test
    @DisplayName("Empty tree is balanced")
    void emptyTreeIsBalanced() {
        assertThat(tree.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("Single insertion — tree is balanced")
    void singleInsert() {
        tree.insert(10);
        assertThat(tree.isBalanced()).isTrue();
        assertThat(tree.contains(10)).isTrue();
    }

    @Test
    @DisplayName("Left-Left case — triggers right rotation")
    void leftLeftRotation() {
        // Inserting 30→20→10 causes LL imbalance → right rotation
        tree.insert(30);
        tree.insert(20);
        tree.insert(10);

        assertThat(tree.isBalanced()).isTrue();
        assertThat(tree.contains(10)).isTrue();
        assertThat(tree.contains(20)).isTrue();
        assertThat(tree.contains(30)).isTrue();
    }

    @Test
    @DisplayName("Right-Right case — triggers left rotation")
    void rightRightRotation() {
        tree.insert(10);
        tree.insert(20);
        tree.insert(30);

        assertThat(tree.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("Left-Right case — triggers LR rotation")
    void leftRightRotation() {
        tree.insert(30);
        tree.insert(10);
        tree.insert(20);

        assertThat(tree.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("Right-Left case — triggers RL rotation")
    void rightLeftRotation() {
        tree.insert(10);
        tree.insert(30);
        tree.insert(20);

        assertThat(tree.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("Sequential insertions maintain balance invariant")
    void sequentialInsertsMaintainBalance() {
        for (int i = 1; i <= 100; i++) {
            tree.insert(i);
            assertThat(tree.isBalanced())
                .as("Tree should remain balanced after inserting %d", i)
                .isTrue();
        }
    }

    @Test
    @DisplayName("In-order traversal after insertions is sorted")
    void inOrderTraversalIsSorted() {
        int[] values = {5, 3, 7, 1, 4, 6, 8, 2};
        for (int v : values) tree.insert(v);

        var inOrder = tree.inOrder();
        assertThat(inOrder).isSorted();
        assertThat(inOrder).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    @DisplayName("Contains returns false for absent element")
    void containsAbsent() {
        tree.insert(5);
        tree.insert(3);
        assertThat(tree.contains(99)).isFalse();
    }

    @Test
    @DisplayName("Delete maintains balance")
    void deleteMaintainsBalance() {
        for (int i = 1; i <= 15; i++) tree.insert(i);

        tree.delete(7);
        tree.delete(3);
        tree.delete(11);

        assertThat(tree.isBalanced()).isTrue();
        assertThat(tree.contains(7)).isFalse();
        assertThat(tree.contains(3)).isFalse();
    }
}
