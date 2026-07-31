package com.conceptualware.core.algorithms.graph;

import com.conceptualware.core.datastructures.graph.Graph;
import java.util.*;

public class GraphAlgorithms {

    private static final double INF = Double.MAX_VALUE / 2;

    public static double[] dijkstra(Graph graph, int source) {
        int n = graph.vertices();
        double[] dist = new double[n];
        Arrays.fill(dist, INF);
        dist[source] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        pq.offer(new int[]{source, 0});

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int u = curr[0];
            double d = curr[1];
            if (d > dist[u]) continue;

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

    public static double[] bellmanFord(int n, List<int[]> edges, int source) {
        double[] dist = new double[n];
        Arrays.fill(dist, INF);
        dist[source] = 0;

        for (int i = 0; i < n - 1; i++) {
            for (int[] edge : edges) {
                if (dist[edge[0]] != INF) {
                    double newDist = dist[edge[0]] + edge[2];
                    if (newDist < dist[edge[1]]) dist[edge[1]] = newDist;
                }
            }
        }
        for (int[] edge : edges) {
            if (dist[edge[0]] != INF && dist[edge[0]] + edge[2] < dist[edge[1]])
                throw new IllegalStateException("Negative cycle detected");
        }
        return dist;
    }

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

    public record MST(List<int[]> edges, double totalWeight) {}

    public static MST kruskal(int n, List<int[]> edges) {
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

    public static List<List<Integer>> tarjanSCC(Graph graph) {
        int n = graph.vertices();
        int[] index   = new int[n];
        int[] low     = new int[n];
        boolean[] onStack = new boolean[n];
        Arrays.fill(index, -1);

        Deque<Integer> sccStack = new ArrayDeque<>();
        List<List<Integer>> result = new ArrayList<>();
        int timer = 0;

        Deque<int[]> callStack = new ArrayDeque<>();
        List<List<Graph.Edge>> adjacency = new ArrayList<>(n);
        for (int v = 0; v < n; v++) adjacency.add(graph.neighbors(v));

        for (int root = 0; root < n; root++) {
            if (index[root] != -1) continue;

            callStack.push(new int[]{root, 0});
            index[root] = low[root] = timer++;
            sccStack.push(root);
            onStack[root] = true;

            while (!callStack.isEmpty()) {
                int[] frame = callStack.peek();
                int v = frame[0];
                List<Graph.Edge> neighbors = adjacency.get(v);

                if (frame[1] < neighbors.size()) {
                    int w = neighbors.get(frame[1]++).to();
                    if (index[w] == -1) {
                        index[w] = low[w] = timer++;
                        sccStack.push(w);
                        onStack[w] = true;
                        callStack.push(new int[]{w, 0});
                    } else if (onStack[w]) {
                        low[v] = Math.min(low[v], index[w]);
                    }
                } else {
                    callStack.pop();
                    if (!callStack.isEmpty()) {
                        int parent = callStack.peek()[0];
                        low[parent] = Math.min(low[parent], low[v]);
                    }
                    if (low[v] == index[v]) {
                        List<Integer> component = new ArrayList<>();
                        int w;
                        do {
                            w = sccStack.pop();
                            onStack[w] = false;
                            component.add(w);
                        } while (w != v);
                        result.add(component);
                    }
                }
            }
        }
        return result;
    }

    public static List<List<Integer>> kosarajuSCC(Graph graph) {
        int n = graph.vertices();
        boolean[] visited = new boolean[n];
        Deque<Integer> finishOrder = new ArrayDeque<>();

        for (int v = 0; v < n; v++) {
            if (!visited[v]) fillOrderIterative(graph, v, visited, finishOrder);
        }

        Graph transposed = graph.transpose();

        Arrays.fill(visited, false);
        List<List<Integer>> components = new ArrayList<>();
        while (!finishOrder.isEmpty()) {
            int v = finishOrder.pop();
            if (!visited[v]) {
                List<Integer> component = new ArrayList<>();
                collectIterative(transposed, v, visited, component);
                components.add(component);
            }
        }
        return components;
    }

    private static void fillOrderIterative(Graph graph, int start, boolean[] visited, Deque<Integer> order) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{start, 0});
        visited[start] = true;

        while (!stack.isEmpty()) {
            int[] frame = stack.peek();
            List<Graph.Edge> neighbors = graph.neighbors(frame[0]);

            if (frame[1] < neighbors.size()) {
                int w = neighbors.get(frame[1]++).to();
                if (!visited[w]) {
                    visited[w] = true;
                    stack.push(new int[]{w, 0});
                }
            } else {
                order.push(stack.pop()[0]);
            }
        }
    }

    private static void collectIterative(Graph graph, int start, boolean[] visited, List<Integer> component) {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        visited[start] = true;

        while (!stack.isEmpty()) {
            int v = stack.pop();
            component.add(v);
            for (Graph.Edge e : graph.neighbors(v)) {
                if (!visited[e.to()]) {
                    visited[e.to()] = true;
                    stack.push(e.to());
                }
            }
        }
    }

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
