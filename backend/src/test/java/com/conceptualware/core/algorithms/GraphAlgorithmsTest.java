package com.conceptualware.core.algorithms;

import com.conceptualware.core.algorithms.graph.GraphAlgorithms;
import com.conceptualware.core.datastructures.graph.Graph;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Graph Algorithms — Unit Tests")
class GraphAlgorithmsTest {

    private static final double INF = Double.MAX_VALUE / 2;

    @Test
    @DisplayName("Dijkstra — shortest path in simple 5-node graph")
    void dijkstraSimpleGraph() {
        Graph g = new Graph(4, true);
        g.addEdge(0, 1, 4);
        g.addEdge(0, 2, 1);
        g.addEdge(2, 1, 2);
        g.addEdge(1, 3, 1);
        g.addEdge(2, 3, 5);

        double[] distances = GraphAlgorithms.dijkstra(g, 0);

        assertThat(distances[0]).isEqualTo(0);
        assertThat(distances[1]).isEqualTo(3);
        assertThat(distances[2]).isEqualTo(1);
        assertThat(distances[3]).isEqualTo(4);
    }

    @Test
    @DisplayName("Dijkstra — single node graph")
    void dijkstraSingleNode() {
        Graph g = new Graph(1, true);
        double[] distances = GraphAlgorithms.dijkstra(g, 0);
        assertThat(distances[0]).isEqualTo(0);
    }

    @Test
    @DisplayName("Bellman-Ford — handles negative edges (no negative cycle)")
    void bellmanFordNegativeEdges() {
        List<int[]> edges = List.of(
            new int[]{0, 1, 5},
            new int[]{0, 2, 4},
            new int[]{1, 3, 3},
            new int[]{2, 1, -4},
            new int[]{3, 2, 2}
        );

        double[] distances = GraphAlgorithms.bellmanFord(4, edges, 0);
        assertThat(distances[0]).isEqualTo(0);
    }

    @Test
    @DisplayName("Bellman-Ford — detects negative cycle")
    void bellmanFordNegativeCycle() {
        List<int[]> edges = List.of(
            new int[]{0, 1, 1},
            new int[]{1, 2, -2},
            new int[]{2, 0, 0}
        );

        assertThatThrownBy(() -> GraphAlgorithms.bellmanFord(3, edges, 0))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Floyd-Warshall — all-pairs shortest paths")
    void floydWarshallAllPairs() {
        double[][] dist = {
            {0,   3,   INF, 7  },
            {8,   0,   2,   INF},
            {5,   INF, 0,   1  },
            {2,   INF, INF, 0  },
        };

        double[][] result = GraphAlgorithms.floydWarshall(dist);

        assertThat(result[0][2]).isEqualTo(5);
        assertThat(result[3][2]).isEqualTo(7);
    }

    @Test
    @DisplayName("Kruskal — MST has V-1 edges")
    void kruskalMstEdgeCount() {
        List<int[]> edges = new ArrayList<>(List.of(
            new int[]{0, 1, 10},
            new int[]{0, 2, 6},
            new int[]{0, 3, 5},
            new int[]{1, 3, 15},
            new int[]{2, 3, 4}
        ));

        GraphAlgorithms.MST mst = GraphAlgorithms.kruskal(4, edges);

        assertThat(mst.edges()).hasSize(3);
    }

    @Test
    @DisplayName("Kruskal — MST total weight is minimal")
    void kruskalMstWeight() {
        List<int[]> edges = new ArrayList<>(List.of(
            new int[]{0, 1, 10},
            new int[]{0, 2, 6},
            new int[]{0, 3, 5},
            new int[]{1, 3, 15},
            new int[]{2, 3, 4}
        ));

        GraphAlgorithms.MST mst = GraphAlgorithms.kruskal(4, edges);

        assertThat(mst.totalWeight()).isEqualTo(19);
    }
}
