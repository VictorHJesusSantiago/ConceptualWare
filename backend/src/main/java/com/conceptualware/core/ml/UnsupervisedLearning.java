package com.conceptualware.core.ml;

import java.util.*;

/**
 * Concept #30 — Unsupervised Machine Learning:
 *
 *   Unsupervised learning finds structure in UNLABELED data.
 *   No target variable y — the algorithm must discover patterns on its own.
 *
 *   CLUSTERING (group similar examples):
 *     K-Means:        partition into k clusters by centroid proximity
 *     DBSCAN:         density-based — finds arbitrary-shaped clusters + outliers
 *     Hierarchical:   build a dendrogram, cut at desired # clusters
 *
 *   DIMENSIONALITY REDUCTION (compress features):
 *     PCA (Principal Component Analysis):
 *       Find orthogonal directions (principal components) of maximum variance.
 *       Project data onto top-k components → preserves most information.
 *       Mathematically: eigendecomposition of the covariance matrix.
 *
 *     t-SNE (conceptual — not implemented numerically due to complexity):
 *       Preserves local structure, good for visualization; not suitable for new data.
 *
 *   ANOMALY DETECTION:
 *     Isolation Forest (concept): randomly isolate points — anomalies isolated faster.
 */
public class UnsupervisedLearning {

    // ── K-MEANS CLUSTERING ────────────────────────────────────────────────────
    /**
     * Algorithm (Lloyd's):
     *   1. Initialize k centroids (random or k-means++ smart init)
     *   2. Assign each point to nearest centroid  → O(n·k·d)
     *   3. Recompute centroids as mean of cluster  → O(n·d)
     *   4. Repeat until centroids don't move (convergence) or maxIter
     *
     * k-means++ initialization:
     *   Choose first centroid uniformly at random.
     *   Each subsequent centroid chosen with probability ∝ D(x)² (squared distance to nearest existing centroid).
     *   Reduces initialization sensitivity, gives O(log k) approximation guarantee.
     *
     * Elbow method: plot inertia (within-cluster SSE) vs k → pick "elbow" k.
     * Silhouette score: measures how well-separated clusters are (-1 to 1, higher=better).
     */
    public static class KMeans {
        private double[][] centroids;
        private int[] labels;
        private final int k;
        private final int maxIter;
        private final long seed;
        private double inertia;

        public KMeans(int k, int maxIter, long seed) {
            this.k = k; this.maxIter = maxIter; this.seed = seed;
        }

        public void fit(double[][] X) {
            int n = X.length, d = X[0].length;
            Random rng = new Random(seed);
            centroids = kMeansPlusPlusInit(X, rng);
            labels = new int[n];

            for (int iter = 0; iter < maxIter; iter++) {
                // Assign step
                boolean changed = false;
                for (int i = 0; i < n; i++) {
                    int newLabel = nearestCentroid(X[i]);
                    if (newLabel != labels[i]) { labels[i] = newLabel; changed = true; }
                }
                if (!changed) break; // convergence

                // Update step: recompute centroids
                double[][] sums = new double[k][d];
                int[] counts = new int[k];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < d; j++) sums[labels[i]][j] += X[i][j];
                    counts[labels[i]]++;
                }
                for (int c = 0; c < k; c++) {
                    if (counts[c] > 0) for (int j = 0; j < d; j++) centroids[c][j] = sums[c][j] / counts[c];
                }
            }

            // Compute inertia (within-cluster sum of squared distances)
            inertia = 0;
            for (int i = 0; i < n; i++) inertia += squaredDist(X[i], centroids[labels[i]]);
        }

        private double[][] kMeansPlusPlusInit(double[][] X, Random rng) {
            int n = X.length, d = X[0].length;
            double[][] cents = new double[k][d];
            cents[0] = X[rng.nextInt(n)].clone();

            for (int c = 1; c < k; c++) {
                double[] distances = new double[n];
                double total = 0;
                for (int i = 0; i < n; i++) {
                    double minDist = Double.MAX_VALUE;
                    for (int prev = 0; prev < c; prev++) minDist = Math.min(minDist, squaredDist(X[i], cents[prev]));
                    distances[i] = minDist;
                    total += minDist;
                }
                // Weighted random selection
                double rand = rng.nextDouble() * total;
                double cumul = 0;
                for (int i = 0; i < n; i++) {
                    cumul += distances[i];
                    if (cumul >= rand) { cents[c] = X[i].clone(); break; }
                }
            }
            return cents;
        }

        public int predict(double[] x) { return nearestCentroid(x); }
        public int[] labels()          { return labels.clone(); }
        public double[][] centroids()  { return centroids; }
        public double inertia()        { return inertia; }

        private int nearestCentroid(double[] x) {
            int best = 0; double bestDist = Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                double d = squaredDist(x, centroids[c]);
                if (d < bestDist) { bestDist = d; best = c; }
            }
            return best;
        }

        private double squaredDist(double[] a, double[] b) {
            double sum = 0;
            for (int j = 0; j < a.length; j++) sum += Math.pow(a[j] - b[j], 2);
            return sum;
        }

        /** Silhouette score: (b - a) / max(a, b). Range [-1, 1]. Higher is better. */
        public double silhouetteScore(double[][] X) {
            int n = X.length;
            double totalSil = 0;
            for (int i = 0; i < n; i++) {
                double a = intraClusterDist(X, i);
                double b = minInterClusterDist(X, i);
                totalSil += (b - a) / Math.max(a, b);
            }
            return totalSil / n;
        }

        private double intraClusterDist(double[][] X, int idx) {
            int cluster = labels[idx]; double sum = 0; int cnt = 0;
            for (int i = 0; i < X.length; i++) if (i != idx && labels[i] == cluster) { sum += Math.sqrt(squaredDist(X[idx], X[i])); cnt++; }
            return cnt == 0 ? 0 : sum / cnt;
        }

        private double minInterClusterDist(double[][] X, int idx) {
            double minDist = Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                if (c == labels[idx]) continue;
                double sum = 0; int cnt = 0;
                for (int i = 0; i < X.length; i++) if (labels[i] == c) { sum += Math.sqrt(squaredDist(X[idx], X[i])); cnt++; }
                if (cnt > 0) minDist = Math.min(minDist, sum / cnt);
            }
            return minDist;
        }

        /** Elbow analysis: fit k-means for k=2..maxK, return inertias. */
        public static double[] elbowAnalysis(double[][] X, int maxK, int maxIter) {
            double[] inertias = new double[maxK - 1];
            for (int k = 2; k <= maxK; k++) {
                KMeans km = new KMeans(k, maxIter, 42);
                km.fit(X);
                inertias[k - 2] = km.inertia();
            }
            return inertias;
        }
    }

    // ── DBSCAN ────────────────────────────────────────────────────────────────
    /**
     * Density-Based Spatial Clustering of Applications with Noise.
     *   Parameters: eps (neighborhood radius), minPts (min points for core point)
     *   Core point:    has ≥ minPts neighbors within eps
     *   Border point:  within eps of a core point, but < minPts own neighbors
     *   Noise point:   neither core nor border → outlier (label = -1)
     *
     *   Advantage over k-means: discovers arbitrary-shaped clusters, handles noise.
     *   Disadvantage: sensitive to eps/minPts; struggles with varying densities.
     */
    public static class DBSCAN {
        private int[] labels; // -1 = noise, ≥0 = cluster id
        private final double eps;
        private final int minPts;

        public DBSCAN(double eps, int minPts) { this.eps = eps; this.minPts = minPts; }

        public int[] fit(double[][] X) {
            int n = X.length;
            labels = new int[n];
            Arrays.fill(labels, -2); // -2 = unvisited
            int clusterLabel = 0;

            for (int i = 0; i < n; i++) {
                if (labels[i] != -2) continue;
                List<Integer> neighbors = regionQuery(X, i);
                if (neighbors.size() < minPts) { labels[i] = -1; continue; } // noise
                expandCluster(X, i, neighbors, clusterLabel++);
            }
            return labels.clone();
        }

        private void expandCluster(double[][] X, int p, List<Integer> neighbors, int label) {
            labels[p] = label;
            Queue<Integer> queue = new LinkedList<>(neighbors);
            while (!queue.isEmpty()) {
                int q = queue.poll();
                if (labels[q] == -1) labels[q] = label; // border → cluster
                if (labels[q] != -2) continue;
                labels[q] = label;
                List<Integer> qNeighbors = regionQuery(X, q);
                if (qNeighbors.size() >= minPts) queue.addAll(qNeighbors);
            }
        }

        private List<Integer> regionQuery(double[][] X, int p) {
            List<Integer> result = new ArrayList<>();
            for (int i = 0; i < X.length; i++) if (dist(X[p], X[i]) <= eps) result.add(i);
            return result;
        }

        private double dist(double[] a, double[] b) {
            double sum = 0; for (int j=0;j<a.length;j++) sum+=Math.pow(a[j]-b[j],2); return Math.sqrt(sum);
        }

        public int numClusters() { return (int) Arrays.stream(labels).filter(l -> l >= 0).distinct().count(); }
        public int numNoise()    { return (int) Arrays.stream(labels).filter(l -> l == -1).count(); }
    }

    // ── PCA (PRINCIPAL COMPONENT ANALYSIS) ────────────────────────────────────
    /**
     * Finds orthogonal axes of maximum variance (principal components).
     *
     *   Algorithm:
     *     1. Center data: X_c = X - μ
     *     2. Compute covariance matrix: C = (1/n) Xᵀ_c X_c  [p×p]
     *     3. Eigendecomposition: C = V Λ Vᵀ (columns of V = eigenvectors)
     *     4. Sort eigenvectors by eigenvalue (descending variance)
     *     5. Project: Z = X_c · V[:, :k]  [n×k]
     *
     *   We use power iteration (deflation) for eigendecomposition since n >> p
     *   is the typical case and we only need top-k eigenvectors.
     *
     *   Explained variance ratio[i] = λᵢ / Σ λⱼ
     *   Cumulative explained variance helps choose k (e.g., keep 95% variance).
     */
    public static class PCA {
        private double[] mean;
        private double[][] components;    // [k × p] — top-k eigenvectors
        private double[] explainedVarianceRatio;
        private int numComponents;

        public void fit(double[][] X, int k) {
            int n = X.length, p = X[0].length;
            this.numComponents = k;
            this.mean = new double[p];

            // Step 1: Center data
            for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) mean[j] += X[i][j];
            for (int j = 0; j < p; j++) mean[j] /= n;

            double[][] Xc = new double[n][p];
            for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) Xc[i][j] = X[i][j] - mean[j];

            // Step 2: Covariance matrix C = Xᵀ X / n  [p×p]
            double[][] C = new double[p][p];
            for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) {
                for (int i = 0; i < n; i++) C[a][b] += Xc[i][a] * Xc[i][b];
                C[a][b] /= n;
            }

            // Step 3-4: Power iteration + deflation for top-k eigenvectors
            Matrix cov = new Matrix(C);
            components = new double[k][];
            double[] eigenvalues = new double[k];
            for (int c = 0; c < k; c++) {
                double[] ev = cov.dominantEigenvector(100);
                eigenvalues[c] = cov.eigenvalue(ev);
                components[c] = ev;
                // Deflate: remove this component from covariance matrix
                // C := C - λ vvᵀ
                for (int a = 0; a < p; a++) for (int b = 0; b < p; b++)
                    C[a][b] -= eigenvalues[c] * ev[a] * ev[b];
                cov = new Matrix(C);
            }

            // Explained variance ratio
            double totalVar = Arrays.stream(eigenvalues).sum();
            explainedVarianceRatio = new double[k];
            for (int c = 0; c < k; c++) explainedVarianceRatio[c] = eigenvalues[c] / totalVar;
        }

        /** Project n×p data onto n×k PCA space. */
        public double[][] transform(double[][] X) {
            int n = X.length, p = X[0].length, k = numComponents;
            double[][] Z = new double[n][k];
            for (int i = 0; i < n; i++) {
                double[] xc = new double[p];
                for (int j = 0; j < p; j++) xc[j] = X[i][j] - mean[j];
                for (int c = 0; c < k; c++) {
                    for (int j = 0; j < p; j++) Z[i][c] += xc[j] * components[c][j];
                }
            }
            return Z;
        }

        public double[][] fitTransform(double[][] X, int k) { fit(X, k); return transform(X); }

        public double cumulativeExplainedVariance(int k) {
            double sum = 0;
            for (int i = 0; i < k && i < explainedVarianceRatio.length; i++) sum += explainedVarianceRatio[i];
            return sum;
        }

        public double[] explainedVarianceRatio() { return explainedVarianceRatio.clone(); }
        public double[][] components() { return components; }
    }

    // ── HIERARCHICAL CLUSTERING ────────────────────────────────────────────────
    /**
     * Agglomerative (bottom-up): each point starts as its own cluster.
     * Merge the two closest clusters until only one remains → dendrogram.
     * Linkage methods: Single (min), Complete (max), Average, Ward.
     */
    public static class HierarchicalClustering {
        private final int maxClusters;
        private final String linkage; // "single", "complete", "average"

        public HierarchicalClustering(int maxClusters, String linkage) {
            this.maxClusters = maxClusters;
            this.linkage = linkage;
        }

        public int[] fit(double[][] X) {
            int n = X.length;
            List<List<Integer>> clusters = new ArrayList<>();
            for (int i = 0; i < n; i++) { List<Integer> c = new ArrayList<>(); c.add(i); clusters.add(c); }

            while (clusters.size() > maxClusters) {
                double minDist = Double.MAX_VALUE;
                int a = -1, b = -1;
                for (int i = 0; i < clusters.size(); i++)
                    for (int j = i + 1; j < clusters.size(); j++) {
                        double d = clusterDistance(X, clusters.get(i), clusters.get(j));
                        if (d < minDist) { minDist = d; a = i; b = j; }
                    }
                clusters.get(a).addAll(clusters.get(b));
                clusters.remove(b);
            }

            int[] labels = new int[n];
            for (int c = 0; c < clusters.size(); c++) for (int idx : clusters.get(c)) labels[idx] = c;
            return labels;
        }

        private double clusterDistance(double[][] X, List<Integer> ca, List<Integer> cb) {
            return switch (linkage) {
                case "single" -> {
                    double min = Double.MAX_VALUE;
                    for (int a : ca) for (int b : cb) min = Math.min(min, euclidean(X[a], X[b]));
                    yield min;
                }
                case "complete" -> {
                    double max = 0;
                    for (int a : ca) for (int b : cb) max = Math.max(max, euclidean(X[a], X[b]));
                    yield max;
                }
                default -> { // average
                    double sum = 0;
                    for (int a : ca) for (int b : cb) sum += euclidean(X[a], X[b]);
                    yield sum / (ca.size() * cb.size());
                }
            };
        }

        private double euclidean(double[] a, double[] b) {
            double s = 0; for (int j=0;j<a.length;j++) s+=Math.pow(a[j]-b[j],2); return Math.sqrt(s);
        }
    }
}
