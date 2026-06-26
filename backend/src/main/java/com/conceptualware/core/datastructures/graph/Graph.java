package com.conceptualware.core.datastructures.graph;

import java.util.*;

/**
 * Concept #4 — Grafo não-dirigido, Dígrafo, Grafo ponderado, DAG
 *   Lista de adjacência, Matriz de adjacência, Disjoint Set / Union-Find
 * Concept #5 — BFS, DFS, Dijkstra, Bellman-Ford, Floyd-Warshall, Kruskal, Prim,
 *   Topological Sort, Cycle Detection, A*, Max Flow
 * Concept #28 — Teoria dos grafos, Grafo euleriano/hamiltoniano, MST
 */
public class Graph {

    // ── Adjacency List Graph ───────────────────────────────────────────────────

    public record Edge(int to, double weight) implements Comparable<Edge> {
        @Override
        public int compareTo(Edge other) { return Double.compare(this.weight, other.weight); }
    }

    private final int vertices;
    private final List<List<Edge>> adjacencyList;
    private final boolean directed;

    public Graph(int vertices, boolean directed) {
        this.vertices = vertices;
        this.directed = directed;
        this.adjacencyList = new ArrayList<>(vertices);
        for (int i = 0; i < vertices; i++) adjacencyList.add(new ArrayList<>());
    }

    public void addEdge(int from, int to)               { addEdge(from, to, 1.0); }
    public void addEdge(int from, int to, double weight) {
        adjacencyList.get(from).add(new Edge(to, weight));
        if (!directed) adjacencyList.get(to).add(new Edge(from, weight));
    }

    public List<Edge> neighbors(int v) { return adjacencyList.get(v); }
    public int vertices() { return vertices; }

    // ── BFS — Concept #5 ──────────────────────────────────────────────────────

    public List<Integer> bfs(int start) {
        List<Integer> order = new ArrayList<>();
        boolean[] visited = new boolean[vertices];
        Queue<Integer> queue = new LinkedList<>();
        visited[start] = true;
        queue.offer(start);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            order.add(v);
            for (Edge e : adjacencyList.get(v)) {
                if (!visited[e.to()]) {
                    visited[e.to()] = true;
                    queue.offer(e.to());
                }
            }
        }
        return order;
    }

    /** BFS shortest path (unweighted). */
    public int[] bfsShortestPath(int start) {
        int[] dist = new int[vertices];
        Arrays.fill(dist, -1);
        dist[start] = 0;
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(start);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (Edge e : adjacencyList.get(v)) {
                if (dist[e.to()] == -1) {
                    dist[e.to()] = dist[v] + 1;
                    queue.offer(e.to());
                }
            }
        }
        return dist;
    }

    // ── DFS — Concept #5 ──────────────────────────────────────────────────────

    public List<Integer> dfs(int start) {
        List<Integer> order = new ArrayList<>();
        boolean[] visited = new boolean[vertices];
        dfsHelper(start, visited, order);
        return order;
    }

    private void dfsHelper(int v, boolean[] visited, List<Integer> order) {
        visited[v] = true;
        order.add(v);
        for (Edge e : adjacencyList.get(v)) {
            if (!visited[e.to()]) dfsHelper(e.to(), visited, order);
        }
    }

    // ── Cycle Detection — Floyd's algorithm is in algorithms; here use DFS ─────

    public boolean hasCycle() {
        boolean[] visited = new boolean[vertices];
        boolean[] inStack = new boolean[vertices];
        for (int i = 0; i < vertices; i++) {
            if (!visited[i] && dfsCycle(i, visited, inStack)) return true;
        }
        return false;
    }

    private boolean dfsCycle(int v, boolean[] visited, boolean[] inStack) {
        visited[v] = inStack[v] = true;
        for (Edge e : adjacencyList.get(v)) {
            if (!visited[e.to()] && dfsCycle(e.to(), visited, inStack)) return true;
            if (inStack[e.to()]) return true;
        }
        inStack[v] = false;
        return false;
    }

    // ── Topological Sort (Kahn's BFS algorithm) — Concept #5 ─────────────────

    public List<Integer> topologicalSort() {
        int[] inDegree = new int[vertices];
        for (int v = 0; v < vertices; v++)
            for (Edge e : adjacencyList.get(v)) inDegree[e.to()]++;

        Queue<Integer> queue = new LinkedList<>();
        for (int v = 0; v < vertices; v++) if (inDegree[v] == 0) queue.offer(v);

        List<Integer> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            int v = queue.poll();
            order.add(v);
            for (Edge e : adjacencyList.get(v)) {
                if (--inDegree[e.to()] == 0) queue.offer(e.to());
            }
        }
        if (order.size() != vertices) throw new IllegalStateException("Graph has a cycle — no topological order");
        return order;
    }

    // ── Disjoint Set / Union-Find — Concept #4 ────────────────────────────────

    public static class DisjointSet {
        private final int[] parent, rank;

        public DisjointSet(int n) {
            parent = new int[n]; rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        public int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]); // Path compression
            return parent[x];
        }

        public boolean union(int x, int y) {
            int px = find(x), py = find(y);
            if (px == py) return false;
            // Union by rank
            if (rank[px] < rank[py]) { int t = px; px = py; py = t; }
            parent[py] = px;
            if (rank[px] == rank[py]) rank[px]++;
            return true;
        }

        public boolean connected(int x, int y) { return find(x) == find(y); }
    }

    // ── Adjacency Matrix representation ───────────────────────────────────────

    public static class AdjacencyMatrix {
        private final double[][] matrix;
        private final int n;
        private static final double INF = Double.MAX_VALUE / 2;

        public AdjacencyMatrix(int n) {
            this.n = n;
            this.matrix = new double[n][n];
            for (double[] row : matrix) Arrays.fill(row, INF);
            for (int i = 0; i < n; i++) matrix[i][i] = 0;
        }

        public void addEdge(int from, int to, double weight) {
            matrix[from][to] = weight;
        }

        public double[][] getMatrix() { return matrix; }
        public int size() { return n; }
    }
}
