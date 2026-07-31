package com.conceptualware.core.ml;

import java.util.*;

public class UnsupervisedLearning {

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
                boolean changed = false;
                for (int i = 0; i < n; i++) {
                    int newLabel = nearestCentroid(X[i]);
                    if (newLabel != labels[i]) { labels[i] = newLabel; changed = true; }
                }
                if (!changed) break;

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

    public static class DBSCAN {
        private int[] labels;
        private final double eps;
        private final int minPts;

        public DBSCAN(double eps, int minPts) { this.eps = eps; this.minPts = minPts; }

        public int[] fit(double[][] X) {
            int n = X.length;
            labels = new int[n];
            Arrays.fill(labels, -2);
            int clusterLabel = 0;

            for (int i = 0; i < n; i++) {
                if (labels[i] != -2) continue;
                List<Integer> neighbors = regionQuery(X, i);
                if (neighbors.size() < minPts) { labels[i] = -1; continue; }
                expandCluster(X, i, neighbors, clusterLabel++);
            }
            return labels.clone();
        }

        private void expandCluster(double[][] X, int p, List<Integer> neighbors, int label) {
            labels[p] = label;
            Queue<Integer> queue = new LinkedList<>(neighbors);
            while (!queue.isEmpty()) {
                int q = queue.poll();
                if (labels[q] == -1) labels[q] = label;
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

    public static class PCA {
        private double[] mean;
        private double[][] components;
        private double[] explainedVarianceRatio;
        private int numComponents;

        public void fit(double[][] X, int k) {
            int n = X.length, p = X[0].length;
            this.numComponents = k;
            this.mean = new double[p];

            for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) mean[j] += X[i][j];
            for (int j = 0; j < p; j++) mean[j] /= n;

            double[][] Xc = new double[n][p];
            for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) Xc[i][j] = X[i][j] - mean[j];

            double[][] C = new double[p][p];
            for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) {
                for (int i = 0; i < n; i++) C[a][b] += Xc[i][a] * Xc[i][b];
                C[a][b] /= n;
            }

            Matrix cov = new Matrix(C);
            components = new double[k][];
            double[] eigenvalues = new double[k];
            for (int c = 0; c < k; c++) {
                double[] ev = cov.dominantEigenvector(100);
                eigenvalues[c] = cov.eigenvalue(ev);
                components[c] = ev;
                for (int a = 0; a < p; a++) for (int b = 0; b < p; b++)
                    C[a][b] -= eigenvalues[c] * ev[a] * ev[b];
                cov = new Matrix(C);
            }

            double totalVar = Arrays.stream(eigenvalues).sum();
            explainedVarianceRatio = new double[k];
            for (int c = 0; c < k; c++) explainedVarianceRatio[c] = eigenvalues[c] / totalVar;
        }

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

    public static class HierarchicalClustering {
        private final int maxClusters;
        private final String linkage;

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
                default -> {
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
