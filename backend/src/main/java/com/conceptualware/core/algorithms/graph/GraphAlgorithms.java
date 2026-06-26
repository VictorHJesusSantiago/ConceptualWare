package com.conceptualware.core.algorithms.graph;

import com.conceptualware.core.datastructures.graph.Graph;
import java.util.*;

/**
 * Concept #5 — Algoritmos de Grafos:
 *   Dijkstra, Bellman-Ford, Floyd-Warshall, Kruskal (MST), Prim (MST),
 *   A*, Fluxo Máximo (Ford-Fulkerson/Edmonds-Karp), Caixeiro Viajante (TSP)
 *
 * Concept #5 — Busca bidirecional, Detecção de ciclos
 * Concept #28 — Teoria dos grafos: caminho mais curto, MST
 */
public class GraphAlgorithms {

    private static final double INF = Double.MAX_VALUE / 2;

    // ── Dijkstra's Algorithm — O((V+E) log V) ─────────────────────────────────

    public static double[] dijkstra(Graph graph, int source) {
        int n = graph.vertices();
        double[] dist = new double[n];
        Arrays.fill(dist, INF);
        dist[source] = 0;

        // Min-heap: (distance, vertex)
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        pq.offer(new int[]{source, 0});

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int u = curr[0];
            double d = curr[1];
            if (d > dist[u]) continue; // stale entry

            for (Graph.Edge e : graph.neighbors(u)) {
                double newDist = dist[u] + e.weight();
                if (newDist < dist[e.to()]) {
                    dist[e.to()] = newDist;
                    pq.offer(new int[]{e.to(), (int) newDist});
                }
            }
        }
        return dist;
    }

    /** Dijkstra with path reconstruction. */
    public record ShortestPath(double[] distances, int[] predecessors) {}

    public static ShortestPath dijkstraWithPath(Graph graph, int source) {
        int n = graph.vertices();
        double[] dist = new double[n];
        int[] pred = new int[n];
        Arrays.fill(dist, INF);
        Arrays.fill(pred, -1);
        dist[source] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> dist[a[0]]));
        pq.offer(new int[]{source});

        while (!pq.isEmpty()) {
            int u = pq.poll()[0];
            for (Graph.Edge e : graph.neighbors(u)) {
                double newDist = dist[u] + e.weight();
                if (newDist < dist[e.to()]) {
                    dist[e.to()] = newDist;
                    pred[e.to()] = u;
                    pq.offer(new int[]{e.to()});
                }
            }
        }
        return new ShortestPath(dist, pred);
    }

    public static List<Integer> reconstructPath(int[] predecessors, int source, int target) {
        List<Integer> path = new ArrayList<>();
        for (int v = target; v != -1; v = predecessors[v]) {
            path.add(0, v);
            if (v == source) break;
        }
        return path;
    }

    // ── Bellman-Ford — O(VE), handles negative weights ────────────────────────

    public static double[] bellmanFord(int n, List<int[]> edges, int source) {
        double[] dist = new double[n];
        Arrays.fill(dist, INF);
        dist[source] = 0;

        // Relax all edges V-1 times
        for (int i = 0; i < n - 1; i++) {
            for (int[] edge : edges) { // edge = [from, to, weight]
                if (dist[edge[0]] != INF) {
                    double newDist = dist[edge[0]] + edge[2];
                    if (newDist < dist[edge[1]]) dist[edge[1]] = newDist;
                }
            }
        }
        // Check for negative cycles
        for (int[] edge : edges) {
            if (dist[edge[0]] != INF && dist[edge[0]] + edge[2] < dist[edge[1]])
                throw new IllegalStateException("Negative cycle detected");
        }
        return dist;
    }

    // ── Floyd-Warshall — O(V³), all-pairs shortest paths ─────────────────────

    public static double[][] floydWarshall(double[][] adjMatrix) {
        int n = adjMatrix.length;
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) dist[i] = adjMatrix[i].clone();

        for (int k = 0; k < n; k++)
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (dist[i][k] + dist[k][j] < dist[i][j])
                        dist[i][j] = dist[i][k] + dist[k][j];

        return dist;
    }

    // ── Kruskal's MST — O(E log E), uses Union-Find ───────────────────────────

    public record MST(List<int[]> edges, double totalWeight) {}

    public static MST kruskal(int n, List<int[]> edges) { // edge = [from, to, weight]
        List<int[]> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparingInt(e -> e[2]));

        Graph.DisjointSet uf = new Graph.DisjointSet(n);
        List<int[]> mstEdges = new ArrayList<>();
        double totalWeight = 0;

        for (int[] edge : sortedEdges) {
            if (uf.union(edge[0], edge[1])) {
                mstEdges.add(edge);
                totalWeight += edge[2];
                if (mstEdges.size() == n - 1) break;
            }
        }
        return new MST(mstEdges, totalWeight);
    }

    // ── Prim's MST — O((V+E) log V) ──────────────────────────────────────────

    public static MST prim(Graph graph) {
        int n = graph.vertices();
        boolean[] inMST = new boolean[n];
        double[] key = new double[n];
        int[] parent = new int[n];
        Arrays.fill(key, INF);
        Arrays.fill(parent, -1);
        key[0] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> key[a[0]]));
        pq.offer(new int[]{0});
        List<int[]> mstEdges = new ArrayList<>();
        double totalWeight = 0;

        while (!pq.isEmpty()) {
            int u = pq.poll()[0];
            if (inMST[u]) continue;
            inMST[u] = true;
            if (parent[u] != -1) {
                mstEdges.add(new int[]{parent[u], u, (int) key[u]});
                totalWeight += key[u];
            }
            for (Graph.Edge e : graph.neighbors(u)) {
                if (!inMST[e.to()] && e.weight() < key[e.to()]) {
                    key[e.to()] = e.weight();
                    parent[e.to()] = u;
                    pq.offer(new int[]{e.to()});
                }
            }
        }
        return new MST(mstEdges, totalWeight);
    }

    // ── A* Search — O(E log V), heuristic-guided ─────────────────────────────

    @FunctionalInterface
    public interface Heuristic { double estimate(int from, int to); }

    public static List<Integer> aStar(Graph graph, int start, int goal, Heuristic h) {
        int n = graph.vertices();
        double[] gScore = new double[n];
        double[] fScore = new double[n];
        int[] came = new int[n];
        Arrays.fill(gScore, INF);
        Arrays.fill(fScore, INF);
        Arrays.fill(came, -1);
        gScore[start] = 0;
        fScore[start] = h.estimate(start, goal);

        PriorityQueue<Integer> open = new PriorityQueue<>(Comparator.comparingDouble(v -> fScore[v]));
        open.offer(start);

        while (!open.isEmpty()) {
            int curr = open.poll();
            if (curr == goal) return reconstructPath(came, start, goal);
            for (Graph.Edge e : graph.neighbors(curr)) {
                double tentative = gScore[curr] + e.weight();
                if (tentative < gScore[e.to()]) {
                    came[e.to()] = curr;
                    gScore[e.to()] = tentative;
                    fScore[e.to()] = tentative + h.estimate(e.to(), goal);
                    open.offer(e.to());
                }
            }
        }
        return Collections.emptyList();
    }

    // ── Ford-Fulkerson Max Flow (Edmonds-Karp BFS) — O(VE²) ──────────────────

    public static int maxFlow(int[][] capacity, int source, int sink) {
        int n = capacity.length;
        int[][] residual = new int[n][n];
        for (int i = 0; i < n; i++) residual[i] = capacity[i].clone();
        int totalFlow = 0;

        while (true) {
            int[] parent = new int[n];
            Arrays.fill(parent, -1);
            Queue<Integer> queue = new LinkedList<>();
            queue.offer(source);
            parent[source] = source;

            while (!queue.isEmpty() && parent[sink] == -1) {
                int u = queue.poll();
                for (int v = 0; v < n; v++) {
                    if (parent[v] == -1 && residual[u][v] > 0) {
                        parent[v] = u;
                        queue.offer(v);
                    }
                }
            }
            if (parent[sink] == -1) break;

            int pathFlow = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = parent[v])
                pathFlow = Math.min(pathFlow, residual[parent[v]][v]);

            for (int v = sink; v != source; v = parent[v]) {
                residual[parent[v]][v] -= pathFlow;
                residual[v][parent[v]] += pathFlow;
            }
            totalFlow += pathFlow;
        }
        return totalFlow;
    }

    // ── TSP — Held-Karp DP — O(n² 2ⁿ) ──────────────────────────────────────

    public static double tspHeldKarp(double[][] dist) {
        int n = dist.length;
        int FULL = (1 << n) - 1;
        double[][] dp = new double[1 << n][n];
        for (double[] row : dp) Arrays.fill(row, INF);
        dp[1][0] = 0;

        for (int mask = 1; mask <= FULL; mask++) {
            for (int u = 0; u < n; u++) {
                if ((mask & (1 << u)) == 0 || dp[mask][u] == INF) continue;
                for (int v = 0; v < n; v++) {
                    if ((mask & (1 << v)) != 0) continue;
                    int next = mask | (1 << v);
                    dp[next][v] = Math.min(dp[next][v], dp[mask][u] + dist[u][v]);
                }
            }
        }
        double best = INF;
        for (int u = 1; u < n; u++) best = Math.min(best, dp[FULL][u] + dist[u][0]);
        return best;
    }
}
