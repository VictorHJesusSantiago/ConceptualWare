package com.conceptualware.core.datastructures;

import java.util.*;

/**
 * Concept #4 — K-D Tree (Árvore K-Dimensional):
 *   Binary space partitioning tree for multi-dimensional points.
 *   Partitions space by cycling through dimensions at each level.
 *
 *   Build:  O(n log n) — median-finding at each level
 *   Search: O(log n) average for nearest neighbor, O(n) worst case
 *   Range query: O(√n + k) where k = results returned
 *
 *   The key insight: at each node, split on dimension (depth % k).
 *   Points < split-value go left, points >= split-value go right.
 *
 *   Used by: spatial databases, machine learning (k-NN classifier),
 *            collision detection, ray tracing, geographic information systems.
 *
 * Concept #5 — Geometric algorithms, nearest-neighbor search, space partitioning
 */
public class KDTree {

    private final int k;   // number of dimensions
    private Node root;
    private int  size;

    public KDTree(int dimensions) {
        this.k = dimensions;
    }

    // ── Point ─────────────────────────────────────────────────────────────────

    public record Point(double[] coords, String label) {
        public Point(double[] coords) { this(coords, null); }

        double squaredDistanceTo(Point other) {
            double sum = 0;
            for (int i = 0; i < coords.length; i++) {
                double diff = coords[i] - other.coords[i];
                sum += diff * diff;
            }
            return sum;
        }

        double distanceTo(Point other) { return Math.sqrt(squaredDistanceTo(other)); }

        @Override public String toString() {
            return (label != null ? label + ":" : "") + Arrays.toString(coords);
        }
    }

    // ── Node ─────────────────────────────────────────────────────────────────

    private static class Node {
        Point point;
        Node  left, right;
        int   splitDim; // dimension used for partitioning at this node

        Node(Point point, int splitDim) {
            this.point    = point;
            this.splitDim = splitDim;
        }
    }

    // ── Build (median-split construction) ────────────────────────────────────

    public void buildFromPoints(List<Point> points) {
        root = build(new ArrayList<>(points), 0);
        size = points.size();
    }

    private Node build(List<Point> points, int depth) {
        if (points.isEmpty()) return null;

        int dim = depth % k;

        // Sort by current dimension and pick median for balanced tree
        points.sort(Comparator.comparingDouble(p -> p.coords()[dim]));
        int medianIdx = points.size() / 2;

        Node node   = new Node(points.get(medianIdx), dim);
        node.left  = build(points.subList(0, medianIdx),           depth + 1);
        node.right = build(points.subList(medianIdx + 1, points.size()), depth + 1);
        return node;
    }

    // ── Insert ────────────────────────────────────────────────────────────────

    public void insert(Point point) {
        root = insert(root, point, 0);
        size++;
    }

    private Node insert(Node node, Point point, int depth) {
        if (node == null) return new Node(point, depth % k);

        int dim = depth % k;
        if (point.coords()[dim] < node.point.coords()[dim])
            node.left  = insert(node.left,  point, depth + 1);
        else
            node.right = insert(node.right, point, depth + 1);
        return node;
    }

    // ── Nearest Neighbor Search ───────────────────────────────────────────────

    /**
     * Finds the closest point to the query point using branch-and-bound:
     *   1. Traverse down to leaf (like BST search on split dimension)
     *   2. On the way back, check if the hypersphere of the current best
     *      intersects the other side of the splitting hyperplane → if yes, search that side too.
     */
    public Optional<Point> nearestNeighbor(Point query) {
        if (root == null) return Optional.empty();
        NearestResult result = new NearestResult(null, Double.MAX_VALUE);
        nearestNeighbor(root, query, result);
        return Optional.ofNullable(result.best);
    }

    private record NearestResult(Point best, double bestDistSq) {
        NearestResult withCandidate(Point p, double distSq) {
            return distSq < bestDistSq ? new NearestResult(p, distSq) : this;
        }
    }

    private NearestResult nearestNeighbor(Node node, Point query, NearestResult result) {
        if (node == null) return result;

        double distSq = query.squaredDistanceTo(node.point);
        result = result.withCandidate(node.point, distSq);

        int    dim    = node.splitDim;
        double diff   = query.coords()[dim] - node.point.coords()[dim];
        Node   near   = diff < 0 ? node.left  : node.right;
        Node   far    = diff < 0 ? node.right : node.left;

        result = nearestNeighbor(near, query, result); // search closer subtree first

        // Prune: only search far side if the splitting hyperplane is within best distance
        if (diff * diff < result.bestDistSq()) {
            result = nearestNeighbor(far, query, result);
        }
        return result;
    }

    // ── K Nearest Neighbors ───────────────────────────────────────────────────

    /** Returns the k nearest points to query, sorted by distance. */
    public List<Point> kNearestNeighbors(Point query, int kNearest) {
        // Max-heap of size k: keeps track of the k closest seen so far
        PriorityQueue<double[]> heap = new PriorityQueue<>(
            Comparator.comparingDouble((double[] a) -> a[0]).reversed()
        );
        List<Point> candidates = new ArrayList<>();
        collectKNearest(root, query, kNearest, heap, candidates);

        // Return sorted by distance
        candidates.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(query)));
        return candidates.subList(0, Math.min(kNearest, candidates.size()));
    }

    private void collectKNearest(Node node, Point query, int k,
                                  PriorityQueue<double[]> heap, List<Point> best) {
        if (node == null) return;

        double distSq = query.squaredDistanceTo(node.point);
        if (heap.size() < k || distSq < heap.peek()[0]) {
            heap.offer(new double[]{distSq});
            best.add(node.point);
            if (heap.size() > k) { heap.poll(); best.remove(best.size() - 1); }
        }

        int    dim  = node.splitDim;
        double diff = query.coords()[dim] - node.point.coords()[dim];
        collectKNearest(diff < 0 ? node.left : node.right, query, k, heap, best);
        if (heap.size() < k || diff * diff < heap.peek()[0])
            collectKNearest(diff < 0 ? node.right : node.left, query, k, heap, best);
    }

    // ── Range Search ─────────────────────────────────────────────────────────

    /** Returns all points within a hypercube [min[i], max[i]] for each dimension. */
    public List<Point> rangeSearch(double[] min, double[] max) {
        List<Point> result = new ArrayList<>();
        rangeSearch(root, min, max, result);
        return result;
    }

    private void rangeSearch(Node node, double[] min, double[] max, List<Point> result) {
        if (node == null) return;

        if (inRange(node.point, min, max)) result.add(node.point);

        int    dim  = node.splitDim;
        if (min[dim] <= node.point.coords()[dim]) rangeSearch(node.left,  min, max, result);
        if (max[dim] >= node.point.coords()[dim]) rangeSearch(node.right, min, max, result);
    }

    private boolean inRange(Point p, double[] min, double[] max) {
        for (int i = 0; i < k; i++)
            if (p.coords()[i] < min[i] || p.coords()[i] > max[i]) return false;
        return true;
    }

    public int    size()          { return size; }
    public boolean isEmpty()      { return size == 0; }
    public int    dimensions()    { return k; }
}
