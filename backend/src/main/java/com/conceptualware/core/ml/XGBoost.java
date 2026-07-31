package com.conceptualware.core.ml;

import java.util.*;

public class XGBoost {

    private final int   nEstimators;
    private final double learningRate;
    private final int   maxDepth;
    private final double lambda;
    private final double gamma;
    private final double subsample;
    private final double colSampleByTree;
    private final int   minChildWeight;
    private final Random rng;

    private final List<BoostTree> trees = new ArrayList<>();
    private double basePrediction;

    public XGBoost(int nEstimators, double learningRate, int maxDepth,
                   double lambda, double gamma, double subsample,
                   double colSampleByTree, int minChildWeight, long seed) {
        this.nEstimators     = nEstimators;
        this.learningRate    = learningRate;
        this.maxDepth        = maxDepth;
        this.lambda          = lambda;
        this.gamma           = gamma;
        this.subsample       = subsample;
        this.colSampleByTree = colSampleByTree;
        this.minChildWeight  = minChildWeight;
        this.rng             = new Random(seed);
    }

    public XGBoost() {
        this(100, 0.1, 6, 1.0, 0.0, 0.8, 0.8, 1, 42L);
    }

    public void fit(double[][] X, int[] y) {
        int n = X.length;

        long positives = Arrays.stream(y).filter(v -> v == 1).count();
        double priorProb = (double) positives / n;
        basePrediction   = Math.log((priorProb + 1e-9) / (1.0 - priorProb + 1e-9));

        double[] F = new double[n];
        Arrays.fill(F, basePrediction);

        for (int m = 0; m < nEstimators; m++) {
            double[] g = new double[n];
            double[] h = new double[n];
            for (int i = 0; i < n; i++) {
                double p = sigmoid(F[i]);
                g[i] = p - y[i];
                h[i] = Math.max(p * (1.0 - p), 1e-6);
            }

            int[] sampleIdx = subsampleRows(n);

            int[] featureIdx = subsampleCols(X[0].length);

            BoostTree tree = new BoostTree(maxDepth, lambda, gamma, minChildWeight);
            tree.build(X, g, h, sampleIdx, featureIdx);
            trees.add(tree);

            for (int i = 0; i < n; i++) {
                F[i] += learningRate * tree.predict(X[i]);
            }
        }
    }

    public double[] predictProba(double[][] X) {
        double[] probs = new double[X.length];
        for (int i = 0; i < X.length; i++) {
            double rawScore = basePrediction;
            for (BoostTree tree : trees) {
                rawScore += learningRate * tree.predict(X[i]);
            }
            probs[i] = sigmoid(rawScore);
        }
        return probs;
    }

    public int[] predict(double[][] X) {
        double[] probs = predictProba(X);
        int[] labels = new int[probs.length];
        for (int i = 0; i < probs.length; i++) {
            labels[i] = probs[i] >= 0.5 ? 1 : 0;
        }
        return labels;
    }

    public double[] featureImportance(int numFeatures) {
        double[] importance = new double[numFeatures];
        for (BoostTree tree : trees) {
            tree.accumulateImportance(importance);
        }
        double total = Arrays.stream(importance).sum();
        if (total > 0) for (int i = 0; i < numFeatures; i++) importance[i] /= total;
        return importance;
    }

    private int[] subsampleRows(int n) {
        int size = (int) Math.ceil(n * subsample);
        int[] idx = new int[size];
        List<Integer> all = new ArrayList<>(n);
        for (int i = 0; i < n; i++) all.add(i);
        Collections.shuffle(all, rng);
        for (int i = 0; i < size; i++) idx[i] = all.get(i);
        return idx;
    }

    private int[] subsampleCols(int p) {
        int size = (int) Math.ceil(p * colSampleByTree);
        size = Math.max(1, size);
        List<Integer> all = new ArrayList<>(p);
        for (int i = 0; i < p; i++) all.add(i);
        Collections.shuffle(all, rng);
        int[] idx = new int[size];
        for (int i = 0; i < size; i++) idx[i] = all.get(i);
        return idx;
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    static class BoostTree {
        private final int    maxDepth;
        private final double lambda;
        private final double gamma;
        private final int    minChildWeight;

        private TreeNode root;

        BoostTree(int maxDepth, double lambda, double gamma, int minChildWeight) {
            this.maxDepth       = maxDepth;
            this.lambda         = lambda;
            this.gamma          = gamma;
            this.minChildWeight = minChildWeight;
        }

        void build(double[][] X, double[] g, double[] h,
                   int[] sampleIdx, int[] featureIdx) {
            root = buildNode(X, g, h, sampleIdx, featureIdx, 0);
        }

        double predict(double[] x) {
            return root.predict(x);
        }

        void accumulateImportance(double[] importance) {
            root.accumulateGain(importance);
        }

        private TreeNode buildNode(double[][] X, double[] g, double[] h,
                                   int[] idx, int[] features, int depth) {
            double G = sum(g, idx), H = sum(h, idx);
            double leafWeight = -G / (H + lambda);

            if (depth >= maxDepth || idx.length < minChildWeight) {
                return new TreeNode(leafWeight);
            }

            BestSplit best = findBestSplit(X, g, h, idx, features, G, H);

            if (best == null || best.gain <= 0) {
                return new TreeNode(leafWeight);
            }

            int[] leftIdx = partition(idx, X, best.featureIdx, best.threshold, true);
            int[] rightIdx = partition(idx, X, best.featureIdx, best.threshold, false);

            TreeNode node = new TreeNode(best.featureIdx, best.threshold, best.gain, leafWeight);
            node.left  = buildNode(X, g, h, leftIdx,  features, depth + 1);
            node.right = buildNode(X, g, h, rightIdx, features, depth + 1);
            return node;
        }

        private BestSplit findBestSplit(double[][] X, double[] g, double[] h,
                                        int[] idx, int[] features,
                                        double G, double H) {
            BestSplit best = null;

            for (int fIdx : features) {
                double[] vals = new double[idx.length];
                for (int k = 0; k < idx.length; k++) vals[k] = X[idx[k]][fIdx];
                Arrays.sort(vals);

                for (int k = 0; k < vals.length - 1; k++) {
                    double threshold = (vals[k] + vals[k + 1]) / 2.0;
                    if (vals[k] == vals[k + 1]) continue;

                    double GL = 0, HL = 0;
                    int countL = 0;
                    for (int i : idx) {
                        if (X[i][fIdx] <= threshold) { GL += g[i]; HL += h[i]; countL++; }
                    }
                    double GR = G - GL, HR = H - HL;
                    int countR = idx.length - countL;

                    if (countL < minChildWeight || countR < minChildWeight) continue;

                    double gain = 0.5 * (GL * GL / (HL + lambda)
                                       + GR * GR / (HR + lambda)
                                       - G  * G  / (H  + lambda))
                                  - gamma;

                    if (best == null || gain > best.gain) {
                        best = new BestSplit(fIdx, threshold, gain);
                    }
                }
            }
            return best;
        }

        private int[] partition(int[] idx, double[][] X, int fIdx, double threshold, boolean left) {
            List<Integer> result = new ArrayList<>();
            for (int i : idx) {
                boolean goLeft = X[i][fIdx] <= threshold;
                if (goLeft == left) result.add(i);
            }
            return result.stream().mapToInt(Integer::intValue).toArray();
        }

        private static double sum(double[] arr, int[] idx) {
            double s = 0;
            for (int i : idx) s += arr[i];
            return s;
        }
    }

    static class TreeNode {
        int featureIdx = -1;
        double threshold;
        double leafWeight;
        double gainAtSplit;
        TreeNode left, right;

        TreeNode(double leafWeight) { this.leafWeight = leafWeight; }

        TreeNode(int featureIdx, double threshold, double gain, double leafWeight) {
            this.featureIdx = featureIdx;
            this.threshold  = threshold;
            this.gainAtSplit = gain;
            this.leafWeight = leafWeight;
        }

        boolean isLeaf() { return left == null && right == null; }

        double predict(double[] x) {
            if (isLeaf()) return leafWeight;
            return x[featureIdx] <= threshold ? left.predict(x) : right.predict(x);
        }

        void accumulateGain(double[] importance) {
            if (isLeaf()) return;
            importance[featureIdx] += gainAtSplit;
            left.accumulateGain(importance);
            right.accumulateGain(importance);
        }
    }

    static class BestSplit {
        final int featureIdx;
        final double threshold;
        final double gain;

        BestSplit(int featureIdx, double threshold, double gain) {
            this.featureIdx = featureIdx;
            this.threshold  = threshold;
            this.gain       = gain;
        }
    }

    public int getNumTrees()    { return trees.size(); }
    public double getBasePred() { return basePrediction; }
}
