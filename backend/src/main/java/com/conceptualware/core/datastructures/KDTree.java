package com.conceptualware.core.datastructures;

import java.util.*;

public class KDTree {

    private final int k;
    private Node root;
    private int  size;

    public KDTree(int dimensions) {
        this.k = dimensions;
    }

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

    private static class Node {
        Point point;
        Node  left, right;
        int   splitDim;

        Node(Point point, int splitDim) {
            this.point    = point;
            this.splitDim = splitDim;
        }
    }

    public void buildFromPoints(List<Point> points) {
        root = build(new ArrayList<>(points), 0);
        size = points.size();
    }

    private Node build(List<Point> points, int depth) {
        if (points.isEmpty()) return null;

        int dim = depth % k;

        points.sort(Comparator.comparingDouble(p -> p.coords()[dim]));
        int medianIdx = points.size() / 2;

        Node node   = new Node(points.get(medianIdx), dim);
        node.left  = build(points.subList(0, medianIdx),           depth + 1);
        node.right = build(points.subList(medianIdx + 1, points.size()), depth + 1);
        return node;
    }

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

    public Optional<Point> nearestNeighbor(Point query) {
        if (root == null) return Optional.empty();
        NearestResult result = new NearestResult(null, Double.MAX_VALUE);
        result = nearestNeighbor(root, query, result);
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

        result = nearestNeighbor(near, query, result);

        if (diff * diff < result.bestDistSq()) {
            result = nearestNeighbor(far, query, result);
        }
        return result;
    }

    public List<Point> kNearestNeighbors(Point query, int kNearest) {
        PriorityQueue<double[]> heap = new PriorityQueue<>(
            Comparator.comparingDouble((double[] a) -> a[0]).reversed()
        );
        List<Point> candidates = new ArrayList<>();
        collectKNearest(root, query, kNearest, heap, candidates);

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
