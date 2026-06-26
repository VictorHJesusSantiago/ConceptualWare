package com.conceptualware.core.algorithms;

import com.conceptualware.core.algorithms.graph.GraphAlgorithms;
import com.conceptualware.core.datastructures.graph.Graph;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — Testing: unit tests, AAA, Given-When-Then
 * Concept #5  — Graph algorithms: Dijkstra, Bellman-Ford, Floyd-Warshall, Kruskal, A*
 */
@DisplayName("Graph Algorithms — Unit Tests")
class GraphAlgorithmsTest {

    // ── Dijkstra ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Dijkstra — shortest path in simple 5-node graph")
    void dijkstraSimpleGraph() {
        // Given: 0→1 (4), 0→2 (1), 2→1 (2), 1→3 (1), 2→3 (5)
        Graph.WeightedGraph g = new Graph.WeightedGraph(4);
        g.addEdge(0, 1, 4);
        g.addEdge(0, 2, 1);
        g.addEdge(2, 1, 2);
        g.addEdge(1, 3, 1);
        g.addEdge(2, 3, 5);

        // When
        int[] distances = GraphAlgorithms.dijkstra(g, 0);

        // Then: shortest path 0→2→1→3 = 1+2+1 = 4
        assertThat(distances[0]).isEqualTo(0);
        assertThat(distances[1]).isEqualTo(3);  // 0→2→1
        assertThat(distances[2]).isEqualTo(1);  // 0→2
        assertThat(distances[3]).isEqualTo(4);  // 0→2→1→3
    }

    @Test
    @DisplayName("Dijkstra — single node graph")
    void dijkstraSingleNode() {
        Graph.WeightedGraph g = new Graph.WeightedGraph(1);
        int[] distances = GraphAlgorithms.dijkstra(g, 0);
        assertThat(distances[0]).isEqualTo(0);
    }

    // ── Bellman-Ford ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bellman-Ford — handles negative edges (no negative cycle)")
    void bellmanFordNegativeEdges() {
        // Given: graph with negative edge
        Graph.WeightedGraph g = new Graph.WeightedGraph(4);
        g.addEdge(0, 1, 5);
        g.addEdge(0, 2, 4);
        g.addEdge(1, 3, 3);
        g.addEdge(2, 1, -4);  // negative edge
        g.addEdge(3, 2, 2);

        // When
        GraphAlgorithms.BellmanFordResult result = GraphAlgorithms.bellmanFord(g, 0);

        // Then: no negative cycle, valid shortest paths
        assertThat(result.hasNegativeCycle()).isFalse();
        assertThat(result.distances()[0]).isEqualTo(0);
    }

    @Test
    @DisplayName("Bellman-Ford — detects negative cycle")
    void bellmanFordNegativeCycle() {
        // Given: negative cycle 0→1→2→0 with total weight -1
        Graph.WeightedGraph g = new Graph.WeightedGraph(3);
        g.addEdge(0, 1, 1);
        g.addEdge(1, 2, -2);
        g.addEdge(2, 0, 0);  // total: 1 + (-2) + 0 = -1 (negative cycle)

        // When
        GraphAlgorithms.BellmanFordResult result = GraphAlgorithms.bellmanFord(g, 0);

        // Then
        assertThat(result.hasNegativeCycle()).isTrue();
    }

    // ── Floyd-Warshall ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Floyd-Warshall — all-pairs shortest paths")
    void floydWarshallAllPairs() {
        // 4-node graph
        int INF = Integer.MAX_VALUE / 2;
        int[][] dist = {
            {0,   3,   INF, 7  },
            {8,   0,   2,   INF},
            {5,   INF, 0,   1  },
            {2,   INF, INF, 0  },
        };

        int[][] result = GraphAlgorithms.floydWarshall(dist, 4);

        assertThat(result[0][2]).isEqualTo(5);  // 0→1→2
        assertThat(result[3][2]).isEqualTo(7);  // 3→0→1→2 = 2+3+2
    }

    // ── Kruskal ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Kruskal — MST has V-1 edges")
    void kruskalMstEdgeCount() {
        // 4-node fully connected graph
        Graph.WeightedGraph g = new Graph.WeightedGraph(4);
        g.addEdge(0, 1, 10);
        g.addEdge(0, 2, 6);
        g.addEdge(0, 3, 5);
        g.addEdge(1, 3, 15);
        g.addEdge(2, 3, 4);

        var mst = GraphAlgorithms.kruskalMST(g);

        assertThat(mst).hasSize(3);  // V-1 edges for 4 vertices
    }

    @Test
    @DisplayName("Kruskal — MST total weight is minimal")
    void kruskalMstWeight() {
        Graph.WeightedGraph g = new Graph.WeightedGraph(4);
        g.addEdge(0, 1, 10);
        g.addEdge(0, 2, 6);
        g.addEdge(0, 3, 5);
        g.addEdge(1, 3, 15);
        g.addEdge(2, 3, 4);

        var mst = GraphAlgorithms.kruskalMST(g);
        int totalWeight = mst.stream().mapToInt(e -> e.weight()).sum();

        assertThat(totalWeight).isEqualTo(19);  // 4+5+10 = 19
    }
}
