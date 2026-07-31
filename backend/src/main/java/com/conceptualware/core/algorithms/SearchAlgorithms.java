package com.conceptualware.core.algorithms;

import java.util.*;

public class SearchAlgorithms {

    public static int linearSearch(int[] arr, int target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) return i;
        }
        return -1;
    }

    public static <T> int linearSearch(List<T> list, T target) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i), target)) return i;
        }
        return -1;
    }

    public static int binarySearch(int[] sortedArr, int target) {
        int lo = 0, hi = sortedArr.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if      (sortedArr[mid] == target) return mid;
            else if (sortedArr[mid] <  target) lo = mid + 1;
            else                               hi = mid - 1;
        }
        return -1;
    }

    public static int binarySearchInsertionPoint(int[] sortedArr, int target) {
        int lo = 0, hi = sortedArr.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (sortedArr[mid] < target) lo = mid + 1;
            else                         hi = mid;
        }
        return lo;
    }

    public static int binarySearchRecursive(int[] arr, int target, int lo, int hi) {
        if (lo > hi) return -1;
        int mid = lo + (hi - lo) / 2;
        if      (arr[mid] == target) return mid;
        else if (arr[mid]  < target) return binarySearchRecursive(arr, target, mid + 1, hi);
        else                         return binarySearchRecursive(arr, target, lo, mid - 1);
    }

    public static int exponentialSearch(int[] sortedArr, int target) {
        if (sortedArr.length == 0) return -1;
        if (sortedArr[0] == target) return 0;

        int i = 1;
        while (i < sortedArr.length && sortedArr[i] <= target) i *= 2;

        return binarySearchRecursive(sortedArr, target, i / 2, Math.min(i, sortedArr.length - 1));
    }

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

    public static int interpolationSearch(int[] sortedArr, int target) {
        int lo = 0, hi = sortedArr.length - 1;
        while (lo <= hi && target >= sortedArr[lo] && target <= sortedArr[hi]) {
            if (lo == hi) { return sortedArr[lo] == target ? lo : -1; }

            int pos = lo + (int)(((long)(hi - lo) * (target - sortedArr[lo]))
                                  / (sortedArr[hi] - sortedArr[lo]));

            if      (sortedArr[pos] == target) return pos;
            else if (sortedArr[pos] <  target) lo = pos + 1;
            else                               hi = pos - 1;
        }
        return -1;
    }

    public static double ternarySearchPeak(double lo, double hi, java.util.function.Function<Double, Double> f) {
        for (int i = 0; i < 200; i++) {
            double m1 = lo + (hi - lo) / 3;
            double m2 = hi - (hi - lo) / 3;
            if (f.apply(m1) < f.apply(m2)) lo = m1;
            else                           hi = m2;
        }
        return (lo + hi) / 2;
    }

    public static int bidirectionalBFS(Map<Integer, List<Integer>> graph, int source, int target) {
        if (source == target) return 0;

        Queue<Integer> frontS = new LinkedList<>(), frontT = new LinkedList<>();
        Map<Integer, Integer> distS = new HashMap<>(), distT = new HashMap<>();

        frontS.add(source); distS.put(source, 0);
        frontT.add(target); distT.put(target, 0);

        int result = Integer.MAX_VALUE;

        while (!frontS.isEmpty() || !frontT.isEmpty()) {
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

            if (result != Integer.MAX_VALUE) return result;
        }

        return -1;
    }

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
                for (int i = neighbors.size() - 1; i >= 0; i--) {
                    if (!seen.contains(neighbors.get(i))) stack.push(neighbors.get(i));
                }
            }
        }
        return visited;
    }

    public static Optional<List<Integer>> aStar(
        Map<Integer, List<int[]>> weightedGraph,
        int start,
        int goal,
        java.util.function.Function<Integer, Integer> heuristic
    ) {
        PriorityQueue<int[]> openSet = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        Map<Integer, Integer> gScore  = new HashMap<>();
        Map<Integer, Integer> parent  = new HashMap<>();

        gScore.put(start, 0);
        openSet.offer(new int[]{start, heuristic.apply(start)});

        while (!openSet.isEmpty()) {
            int cur = openSet.poll()[0];

            if (cur == goal) {
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
        return Optional.empty();
    }
}
