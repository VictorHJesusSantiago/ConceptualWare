package com.conceptualware.core.algorithms;

import java.util.*;

/**
 * Concept #5 — Search Algorithms (Algoritmos de Busca):
 *   - Linear Search (busca linear / sequencial)
 *   - Binary Search (busca binária)
 *   - Exponential Search
 *   - Interpolation Search
 *   - Jump Search
 *   - Ternary Search
 *   - Bidirectional BFS (busca bidirecional em grafo)
 *   - Depth-First Search (DFS)
 *   - A* (A-Star) pathfinding
 */
public class SearchAlgorithms {

    // ── Linear Search — O(n) ──────────────────────────────────────────────────

    /**
     * Linear / Sequential search: scans every element until target is found.
     * Best: O(1) if first element. Worst: O(n). No precondition (works unsorted).
     */
    public static int linearSearch(int[] arr, int target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) return i;
        }
        return -1;
    }

    /** Generalized linear search for any Comparable list. */
    public static <T> int linearSearch(List<T> list, T target) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i), target)) return i;
        }
        return -1;
    }

    // ── Binary Search — O(log n) ──────────────────────────────────────────────

    /**
     * Binary search: requires sorted array. Halves search space at each step.
     * Standard iterative implementation — preferred over recursive (no stack overhead).
     */
    public static int binarySearch(int[] sortedArr, int target) {
        int lo = 0, hi = sortedArr.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2; // avoids integer overflow vs (lo+hi)/2
            if      (sortedArr[mid] == target) return mid;
            else if (sortedArr[mid] <  target) lo = mid + 1;
            else                               hi = mid - 1;
        }
        return -1;
    }

    /** Binary search returning insertion point (like Java Arrays.binarySearch). */
    public static int binarySearchInsertionPoint(int[] sortedArr, int target) {
        int lo = 0, hi = sortedArr.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (sortedArr[mid] < target) lo = mid + 1;
            else                         hi = mid;
        }
        return lo; // index where target should be inserted to maintain sort order
    }

    /** Recursive binary search — demonstrates divide-and-conquer cleanly. */
    public static int binarySearchRecursive(int[] arr, int target, int lo, int hi) {
        if (lo > hi) return -1;
        int mid = lo + (hi - lo) / 2;
        if      (arr[mid] == target) return mid;
        else if (arr[mid]  < target) return binarySearchRecursive(arr, target, mid + 1, hi);
        else                         return binarySearchRecursive(arr, target, lo, mid - 1);
    }

    // ── Exponential Search — O(log n) ────────────────────────────────────────

    /**
     * First finds range [2^i, 2^(i+1)] where target may exist,
     * then binary searches within that range.
     * Better than plain binary search for unbounded/infinite arrays.
     */
    public static int exponentialSearch(int[] sortedArr, int target) {
        if (sortedArr.length == 0) return -1;
        if (sortedArr[0] == target) return 0;

        int i = 1;
        while (i < sortedArr.length && sortedArr[i] <= target) i *= 2;

        return binarySearchRecursive(sortedArr, target, i / 2, Math.min(i, sortedArr.length - 1));
    }

    // ── Jump Search — O(√n) ───────────────────────────────────────────────────

    /**
     * Jumps forward by √n steps, then linear scans backward.
     * Works on sorted arrays. Optimal block size = √n.
     */
    public static int jumpSearch(int[] sortedArr, int target) {
        int n    = sortedArr.length;
        int step = (int) Math.sqrt(n);
        int prev = 0;

        while (prev < n && sortedArr[Math.min(step, n) - 1] < target) {
            prev = step;
            step += (int) Math.sqrt(n);
            if (prev >= n) return -1;
        }

        while (prev < Math.min(step, n)) {
            if (sortedArr[prev] == target) return prev;
            prev++;
        }
        return -1;
    }

    // ── Interpolation Search — O(log log n) average ───────────────────────────

    /**
     * Like binary search but estimates position using linear interpolation.
     * O(log log n) average for uniformly distributed data, O(n) worst case.
     */
    public static int interpolationSearch(int[] sortedArr, int target) {
        int lo = 0, hi = sortedArr.length - 1;
        while (lo <= hi && target >= sortedArr[lo] && target <= sortedArr[hi]) {
            if (lo == hi) { return sortedArr[lo] == target ? lo : -1; }

            // Interpolate position estimate
            int pos = lo + (int)(((long)(hi - lo) * (target - sortedArr[lo]))
                                  / (sortedArr[hi] - sortedArr[lo]));

            if      (sortedArr[pos] == target) return pos;
            else if (sortedArr[pos] <  target) lo = pos + 1;
            else                               hi = pos - 1;
        }
        return -1;
    }

    // ── Ternary Search — O(log₃ n) ───────────────────────────────────────────

    /** Ternary search on unimodal function (finds peak/valley). */
    public static double ternarySearchPeak(double lo, double hi, java.util.function.Function<Double, Double> f) {
        for (int i = 0; i < 200; i++) { // ~200 iterations gives double precision
            double m1 = lo + (hi - lo) / 3;
            double m2 = hi - (hi - lo) / 3;
            if (f.apply(m1) < f.apply(m2)) lo = m1;
            else                           hi = m2;
        }
        return (lo + hi) / 2;
    }

    // ── Bidirectional BFS — O(b^(d/2)) ───────────────────────────────────────

    /**
     * Bidirectional BFS: simultaneously BFS from source and target.
     * Meets in the middle — dramatically reduces search space from b^d to 2·b^(d/2).
     * Used in: Google Maps routing, social network shortest paths.
     *
     * @param graph adjacency list (undirected)
     * @param source starting node
     * @param target destination node
     * @return shortest path length, or -1 if unreachable
     */
    public static int bidirectionalBFS(Map<Integer, List<Integer>> graph, int source, int target) {
        if (source == target) return 0;

        // Two frontiers and visited sets — one from each end
        Queue<Integer> frontS = new LinkedList<>(), frontT = new LinkedList<>();
        Map<Integer, Integer> distS = new HashMap<>(), distT = new HashMap<>();

        frontS.add(source); distS.put(source, 0);
        frontT.add(target); distT.put(target, 0);

        int result = Integer.MAX_VALUE;

        while (!frontS.isEmpty() || !frontT.isEmpty()) {
            // Expand the smaller frontier (balances search)
            if (!frontS.isEmpty()) {
                int cur = frontS.poll();
                for (int neighbor : graph.getOrDefault(cur, List.of())) {
                    if (!distS.containsKey(neighbor)) {
                        distS.put(neighbor, distS.get(cur) + 1);
                        frontS.add(neighbor);
                    }
                    if (distT.containsKey(neighbor)) {
                        result = Math.min(result, distS.get(neighbor) + distT.get(neighbor));
                    }
                }
            }

            if (!frontT.isEmpty()) {
                int cur = frontT.poll();
                for (int neighbor : graph.getOrDefault(cur, List.of())) {
                    if (!distT.containsKey(neighbor)) {
                        distT.put(neighbor, distT.get(cur) + 1);
                        frontT.add(neighbor);
                    }
                    if (distS.containsKey(neighbor)) {
                        result = Math.min(result, distS.get(neighbor) + distT.get(neighbor));
                    }
                }
            }

            // Early termination if path found
            if (result != Integer.MAX_VALUE) return result;
        }

        return -1; // unreachable
    }

    // ── BFS (standard) ────────────────────────────────────────────────────────

    public static List<Integer> bfs(Map<Integer, List<Integer>> graph, int start) {
        List<Integer> visited = new ArrayList<>();
        Set<Integer>  seen    = new HashSet<>();
        Queue<Integer> queue  = new LinkedList<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            visited.add(cur);
            for (int n : graph.getOrDefault(cur, List.of())) {
                if (seen.add(n)) queue.add(n);
            }
        }
        return visited;
    }

    // ── DFS (iterative) ───────────────────────────────────────────────────────

    public static List<Integer> dfs(Map<Integer, List<Integer>> graph, int start) {
        List<Integer> visited = new ArrayList<>();
        Set<Integer>  seen    = new HashSet<>();
        Deque<Integer> stack  = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int cur = stack.pop();
            if (seen.add(cur)) {
                visited.add(cur);
                List<Integer> neighbors = graph.getOrDefault(cur, List.of());
                // Reverse so leftmost neighbor is processed first (matches recursive DFS)
                for (int i = neighbors.size() - 1; i >= 0; i--) {
                    if (!seen.contains(neighbors.get(i))) stack.push(neighbors.get(i));
                }
            }
        }
        return visited;
    }

    // ── A* Search ─────────────────────────────────────────────────────────────

    /**
     * A* (A-Star): finds shortest path using f(n) = g(n) + h(n).
     *   g(n) = actual cost from start to n
     *   h(n) = admissible heuristic estimate from n to goal
     *
     * Optimal and complete when heuristic is admissible (never overestimates).
     * Used in: game AI, GPS navigation, robotics pathfinding.
     */
    public static Optional<List<Integer>> aStar(
        Map<Integer, List<int[]>> weightedGraph, // int[] = {neighbor, weight}
        int start,
        int goal,
        java.util.function.Function<Integer, Integer> heuristic
    ) {
        // Min-heap on f = g + h
        PriorityQueue<int[]> openSet = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        Map<Integer, Integer> gScore  = new HashMap<>();
        Map<Integer, Integer> parent  = new HashMap<>();

        gScore.put(start, 0);
        openSet.offer(new int[]{start, heuristic.apply(start)});

        while (!openSet.isEmpty()) {
            int cur = openSet.poll()[0];

            if (cur == goal) {
                // Reconstruct path
                List<Integer> path = new ArrayList<>();
                for (Integer n = goal; n != null; n = parent.get(n)) path.add(0, n);
                return Optional.of(path);
            }

            for (int[] edge : weightedGraph.getOrDefault(cur, List.of())) {
                int neighbor = edge[0], weight = edge[1];
                int tentativeG = gScore.get(cur) + weight;
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    gScore.put(neighbor, tentativeG);
                    parent.put(neighbor, cur);
                    openSet.offer(new int[]{neighbor, tentativeG + heuristic.apply(neighbor)});
                }
            }
        }
        return Optional.empty(); // no path
    }
}
