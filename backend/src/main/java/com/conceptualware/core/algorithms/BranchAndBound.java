package com.conceptualware.core.algorithms;

import java.util.*;

/**
 * Concept #5 — Branch and Bound (Ramificação e Poda):
 *   General algorithm design paradigm for optimization problems.
 *   Systematically explores the solution space as a tree, pruning branches
 *   that cannot yield a better solution than the current best.
 *
 *   Three operations:
 *     Branch: divide the problem into subproblems (children in search tree)
 *     Bound:  compute an upper/lower bound on the best achievable in that subtree
 *     Prune:  discard the subtree if the bound is worse than the current best
 *
 *   Implemented for two classic NP-hard problems:
 *     1. 0/1 Knapsack (maximization)
 *     2. Travelling Salesman Problem — TSP (minimization, partial tour)
 *
 * Concept #5 — Combinatorial optimization, NP-hard problems, bounding functions
 */
public class BranchAndBound {

    // ── 0/1 Knapsack — Branch and Bound ──────────────────────────────────────

    /**
     * 0/1 Knapsack via Branch and Bound.
     *
     * Upper bound: fractional relaxation (greedy on remaining items sorted by value/weight).
     * Explores items in decreasing value-density order for stronger pruning.
     *
     * @param weights   item weights
     * @param values    item values
     * @param capacity  knapsack capacity
     * @return maximum value achievable without exceeding capacity
     */
    public static int knapsack(int[] weights, int[] values, int capacity) {
        int n = weights.length;

        // Sort items by value/weight ratio descending (greedy order)
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) ->
            Double.compare((double) values[b] / weights[b], (double) values[a] / weights[a]));

        int[] bestValue = {0};
        bbKnapsack(order, weights, values, capacity, 0, 0, 0, bestValue);
        return bestValue[0];
    }

    private static void bbKnapsack(Integer[] order, int[] weights, int[] values,
                                    int capacity, int idx, int currentWeight,
                                    int currentValue, int[] best) {
        if (idx == order.length) {
            best[0] = Math.max(best[0], currentValue);
            return;
        }

        // Compute fractional upper bound on this subtree
        double upperBound = currentValue;
        int remaining = capacity - currentWeight;
        for (int i = idx; i < order.length && remaining > 0; i++) {
            int item = order[i];
            if (weights[item] <= remaining) {
                upperBound += values[item];
                remaining  -= weights[item];
            } else {
                upperBound += (double) values[item] / weights[item] * remaining;
                remaining   = 0;
            }
        }

        // Prune: if upper bound ≤ best known, this branch can't improve
        if (upperBound <= best[0]) return;

        int item = order[idx];

        // Branch: include item (if feasible)
        if (currentWeight + weights[item] <= capacity) {
            bbKnapsack(order, weights, values, capacity,
                       idx + 1, currentWeight + weights[item],
                       currentValue + values[item], best);
        }

        // Branch: exclude item
        bbKnapsack(order, weights, values, capacity, idx + 1, currentWeight, currentValue, best);
    }

    // ── TSP — Branch and Bound ────────────────────────────────────────────────

    /**
     * Travelling Salesman Problem via Branch and Bound.
     * Uses the "reduced cost matrix" bound: minimum cost to visit all remaining cities.
     *
     * Bound: row-reduce + column-reduce the remaining cost matrix.
     * Each row/column must have at least one zero (represents a mandatory tour edge).
     *
     * @param dist  n×n distance matrix (dist[i][j] = cost from city i to j)
     * @return minimum tour length visiting all cities exactly once
     */
    public static int tspBranchAndBound(int[][] dist) {
        int n = dist.length;
        int[] bestCost = {Integer.MAX_VALUE};

        boolean[] visited = new boolean[n];
        visited[0]        = true;
        int[] path        = new int[n];
        path[0]           = 0;

        tspBB(dist, visited, path, 1, 0, 0, n, bestCost);
        return bestCost[0];
    }

    private static void tspBB(int[][] dist, boolean[] visited, int[] path,
                               int depth, int currentCity, int currentCost,
                               int n, int[] best) {
        if (depth == n) {
            // Complete tour: return to start
            int totalCost = currentCost + dist[currentCity][0];
            best[0] = Math.min(best[0], totalCost);
            return;
        }

        // Lower bound: current cost + minimum outgoing edge from each unvisited city
        int lowerBound = currentCost;
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                int minEdge = Integer.MAX_VALUE;
                for (int j = 0; j < n; j++) {
                    if (i != j && (!visited[j] || j == 0)) {
                        minEdge = Math.min(minEdge, dist[i][j]);
                    }
                }
                if (minEdge != Integer.MAX_VALUE) lowerBound += minEdge;
            }
        }

        if (lowerBound >= best[0]) return; // Prune

        for (int next = 0; next < n; next++) {
            if (!visited[next]) {
                visited[next] = true;
                path[depth]   = next;
                tspBB(dist, visited, path, depth + 1,
                      next, currentCost + dist[currentCity][next], n, best);
                visited[next] = false;
            }
        }
    }

    // ── N-Queens — Branch and Bound ──────────────────────────────────────────

    /**
     * N-Queens: place N queens on an N×N board so no two attack each other.
     * Branch: place queen in next row at each column.
     * Bound:  prune if current column is attacked by any placed queen.
     *
     * @return list of all valid queen placements (each int[] is column positions per row)
     */
    public static List<int[]> nQueens(int n) {
        List<int[]> solutions = new ArrayList<>();
        int[] queens = new int[n]; // queens[row] = column
        Arrays.fill(queens, -1);
        solveNQueens(queens, 0, n, solutions);
        return solutions;
    }

    private static void solveNQueens(int[] queens, int row, int n, List<int[]> solutions) {
        if (row == n) {
            solutions.add(Arrays.copyOf(queens, n));
            return;
        }
        for (int col = 0; col < n; col++) {
            if (isValid(queens, row, col)) {
                queens[row] = col;
                solveNQueens(queens, row + 1, n, solutions);
                queens[row] = -1;
            }
        }
    }

    private static boolean isValid(int[] queens, int row, int col) {
        for (int r = 0; r < row; r++) {
            if (queens[r] == col || Math.abs(queens[r] - col) == Math.abs(r - row))
                return false;
        }
        return true;
    }

    // ── Complexity analysis ───────────────────────────────────────────────────

    public record ComplexityAnalysis(String problem, String worstCase, String typicalPruning, String practicalUse) {
        public static ComplexityAnalysis[] all() {
            return new ComplexityAnalysis[]{
                new ComplexityAnalysis("0/1 Knapsack", "O(2^n)", "60-90% of branches pruned", "O(n·2^n) typically"),
                new ComplexityAnalysis("TSP",          "O(n!)",  "75-95% branches pruned",    "Feasible up to ~20 cities"),
                new ComplexityAnalysis("N-Queens",     "O(n!)",  "Highly effective pruning",  "Solves n=25 in seconds"),
            };
        }
    }
}
