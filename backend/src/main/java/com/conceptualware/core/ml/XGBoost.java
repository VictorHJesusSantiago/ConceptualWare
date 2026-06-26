package com.conceptualware.core.ml;

import java.util.*;

/**
 * Concept #30 — XGBoost (eXtreme Gradient Boosting)
 *
 * XGBoost (Chen & Guestrin, 2016) is a scalable gradient boosting framework
 * that builds an ensemble of decision trees sequentially, each correcting the
 * residuals of the previous ensemble.
 *
 * Gradient Boosting framework:
 *   ŷᵢ⁽ᵐ⁾ = ŷᵢ⁽ᵐ⁻¹⁾ + η · fₘ(xᵢ)
 *
 *   fₘ is fitted to NEGATIVE GRADIENTS of the loss:
 *     rᵢₘ = −∂L(yᵢ, ŷᵢ⁽ᵐ⁻¹⁾)/∂ŷᵢ
 *
 * XGBoost innovations over vanilla gradient boosting:
 *   1. Second-order Taylor expansion of loss (uses both gradient gᵢ and Hessian hᵢ)
 *   2. Regularization: Ω(f) = γT + ½λ‖w‖²  (T = #leaves, w = leaf weights)
 *   3. Approximate tree-learning via quantile sketch (handles large datasets)
 *   4. Sparsity-aware split finding (handles missing values natively)
 *   5. Column (feature) subsampling for diversity (like Random Forest)
 *   6. Cache-aware block structure for fast histogram computation
 *
 * This implementation demonstrates the EXACT XGBoost math for binary classification
 * (logistic loss) using exact greedy split finding on small datasets.
 *
 * Objective (regularized):
 *   Obj = Σᵢ L(yᵢ, ŷᵢ) + Σₖ Ω(fₖ)
 *
 * Per leaf j, optimal weight (from second-order approximation):
 *   w*ⱼ = −(Σᵢ∈Iⱼ gᵢ) / (Σᵢ∈Iⱼ hᵢ + λ)
 *
 * Split gain (score improvement when splitting leaf into left/right):
 *   Gain = ½ [ GL²/(HL+λ) + GR²/(HR+λ) − (GL+GR)²/(HL+HR+λ) ] − γ
 *
 * where G = Σgᵢ, H = Σhᵢ for samples in each partition.
 */
public class XGBoost {

    // ── Hyperparameters ───────────────────────────────────────────────────────

    private final int   nEstimators;    // number of boosting rounds
    private final double learningRate;  // η — shrinkage factor
    private final int   maxDepth;       // max tree depth
    private final double lambda;        // L2 leaf weight regularization
    private final double gamma;         // minimum gain to make a split
    private final double subsample;     // row subsampling ratio per tree
    private final double colSampleByTree; // feature subsampling ratio per tree
    private final int   minChildWeight; // minimum sum of Hessians in a leaf
    private final Random rng;

    // ── Model state ───────────────────────────────────────────────────────────

    private final List<BoostTree> trees = new ArrayList<>();
    private double basePrediction;   // log-odds of positive class

    /**
     * @param nEstimators      number of trees to build (boosting rounds)
     * @param learningRate     step size shrinkage (0 < η ≤ 1)
     * @param maxDepth         maximum depth of each tree
     * @param lambda           L2 regularization on leaf weights
     * @param gamma            minimum split gain threshold
     * @param subsample        fraction of samples per tree (0, 1]
     * @param colSampleByTree  fraction of features per tree (0, 1]
     * @param minChildWeight   minimum Hessian sum in a leaf node
     * @param seed             random seed
     */
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

    /** Default hyperparameters (good starting point for most datasets). */
    public XGBoost() {
        this(100, 0.1, 6, 1.0, 0.0, 0.8, 0.8, 1, 42L);
    }

    // ── Training ──────────────────────────────────────────────────────────────

    /**
     * Fit XGBoost on binary classification data.
     *
     * @param X  feature matrix [n × p]
     * @param y  binary labels {0, 1} [n]
     */
    public void fit(double[][] X, int[] y) {
        int n = X.length;

        // Base prediction: log-odds of positive class
        long positives = Arrays.stream(y).filter(v -> v == 1).count();
        double priorProb = (double) positives / n;
        basePrediction   = Math.log((priorProb + 1e-9) / (1.0 - priorProb + 1e-9));

        // Current predictions in log-odds space (raw scores)
        double[] F = new double[n];
        Arrays.fill(F, basePrediction);

        for (int m = 0; m < nEstimators; m++) {
            // 1. Compute gradients (first-order) and Hessians (second-order)
            //    Binary cross-entropy loss: L = -y log(p) - (1-y) log(1-p)
            //    p = sigmoid(F)
            //    gᵢ = ∂L/∂Fᵢ = pᵢ − yᵢ
            //    hᵢ = ∂²L/∂Fᵢ² = pᵢ(1 − pᵢ)
            double[] g = new double[n];
            double[] h = new double[n];
            for (int i = 0; i < n; i++) {
                double p = sigmoid(F[i]);
                g[i] = p - y[i];
                h[i] = Math.max(p * (1.0 - p), 1e-6);
            }

            // 2. Row subsampling
            int[] sampleIdx = subsampleRows(n);

            // 3. Feature subsampling
            int[] featureIdx = subsampleCols(X[0].length);

            // 4. Build tree on this boosting round's gradients/Hessians
            BoostTree tree = new BoostTree(maxDepth, lambda, gamma, minChildWeight);
            tree.build(X, g, h, sampleIdx, featureIdx);
            trees.add(tree);

            // 5. Update predictions: F = F + η · tree(x)
            for (int i = 0; i < n; i++) {
                F[i] += learningRate * tree.predict(X[i]);
            }
        }
    }

    /**
     * Predict class probabilities P(y=1|x).
     *
     * @param X  feature matrix [n × p]
     * @return   probabilities [n]
     */
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

    /**
     * Predict binary class labels using 0.5 threshold.
     */
    public int[] predict(double[][] X) {
        double[] probs = predictProba(X);
        int[] labels = new int[probs.length];
        for (int i = 0; i < probs.length; i++) {
            labels[i] = probs[i] >= 0.5 ? 1 : 0;
        }
        return labels;
    }

    /**
     * Feature importance: total gain contributed by each feature across all trees.
     * High gain → feature is informative for predictions.
     *
     * @return  feature importance array [p], where index = feature index
     */
    public double[] featureImportance(int numFeatures) {
        double[] importance = new double[numFeatures];
        for (BoostTree tree : trees) {
            tree.accumulateImportance(importance);
        }
        // Normalize to [0, 1]
        double total = Arrays.stream(importance).sum();
        if (total > 0) for (int i = 0; i < numFeatures; i++) importance[i] /= total;
        return importance;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

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

    // ── Tree implementation ───────────────────────────────────────────────────

    /**
     * Single regression tree built to minimize the XGBoost objective.
     * Leaf values are optimal weights w* derived from gradients and Hessians.
     */
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
            // Leaf case: max depth reached or too few samples
            double G = sum(g, idx), H = sum(h, idx);
            double leafWeight = -G / (H + lambda);   // optimal leaf weight w*

            if (depth >= maxDepth || idx.length <= 1 || H < minChildWeight) {
                return new TreeNode(leafWeight);
            }

            // Find best split across candidate features and thresholds
            BestSplit best = findBestSplit(X, g, h, idx, features, G, H);

            if (best == null || best.gain <= 0) {
                return new TreeNode(leafWeight);
            }

            // Partition samples into left and right
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
                // Collect unique thresholds (midpoints between adjacent sorted values)
                double[] vals = new double[idx.length];
                for (int k = 0; k < idx.length; k++) vals[k] = X[idx[k]][fIdx];
                Arrays.sort(vals);

                for (int k = 0; k < vals.length - 1; k++) {
                    double threshold = (vals[k] + vals[k + 1]) / 2.0;
                    if (vals[k] == vals[k + 1]) continue;   // skip duplicate values

                    double GL = 0, HL = 0;
                    for (int i : idx) {
                        if (X[i][fIdx] <= threshold) { GL += g[i]; HL += h[i]; }
                    }
                    double GR = G - GL, HR = H - HL;

                    if (HL < minChildWeight || HR < minChildWeight) continue;

                    // XGBoost split gain formula
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

        // Leaf node
        TreeNode(double leafWeight) { this.leafWeight = leafWeight; }

        // Internal node
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

    // ── Getters ───────────────────────────────────────────────────────────────

    public int getNumTrees()    { return trees.size(); }
    public double getBasePred() { return basePrediction; }
}
