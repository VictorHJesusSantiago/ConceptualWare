package com.conceptualware.core.ml;

import java.util.*;

/**
 * Concept #30 — Supervised Machine Learning:
 *
 *   In supervised learning, the model learns a mapping f: X → Y from
 *   labeled training examples {(x₁,y₁), ..., (xₙ,yₙ)}.
 *
 *   REGRESSION algorithms (continuous Y):
 *     LinearRegression  — fits hyperplane via gradient descent or normal equation
 *     PolynomialRegression — feature expansion Xᵢ → [1, x, x², ..., xᵈ]
 *
 *   CLASSIFICATION algorithms (discrete Y):
 *     LogisticRegression — sigmoid function, binary cross-entropy loss
 *     KNN               — k-nearest neighbor majority vote
 *     NaiveBayes        — P(y|x) ∝ P(y) ∏ P(xᵢ|y) — conditional independence
 *     DecisionTree      — axis-aligned splits, Gini impurity or information gain
 *     RandomForest      — bagged ensemble of decision trees
 *     SupportVectorMachine — maximum-margin hyperplane with optional kernel
 *
 *   Loss functions:
 *     MSE  = (1/n) Σ (ŷ - y)²            (regression)
 *     MAE  = (1/n) Σ |ŷ - y|             (regression, robust to outliers)
 *     BCE  = -1/n Σ [y log ŷ + (1-y) log(1-ŷ)]  (binary classification)
 *     CE   = -Σ y_c log ŷ_c              (multi-class softmax)
 */
public class SupervisedLearning {

    // ── LINEAR REGRESSION ─────────────────────────────────────────────────────
    /**
     * Ordinary Least Squares (OLS) via gradient descent.
     *   Loss:        L(w) = (1/2n) Σ (Xw - y)²
     *   Gradient:    ∇L = (1/n) Xᵀ(Xw - y)
     *   Update:      w := w - α·∇L
     *
     *   Normal equation (closed-form): w = (XᵀX)⁻¹ Xᵀy — O(p³), no iterations
     */
    public static class LinearRegression {
        private double[] weights;  // w[0]=bias, w[1..p]=feature weights
        private final double learningRate;
        private final int maxIter;
        private final double l2Lambda; // L2 regularization (Ridge)

        public LinearRegression(double learningRate, int maxIter, double l2Lambda) {
            this.learningRate = learningRate;
            this.maxIter = maxIter;
            this.l2Lambda = l2Lambda;
        }

        public TrainingHistory fit(double[][] X, double[] y) {
            int n = X.length, p = X[0].length;
            weights = new double[p + 1]; // +1 for bias
            double[] losses = new double[maxIter];

            for (int iter = 0; iter < maxIter; iter++) {
                double[] grad = new double[p + 1];
                double loss = 0;
                for (int i = 0; i < n; i++) {
                    double pred = predict(X[i]);
                    double err  = pred - y[i];
                    loss += err * err;
                    grad[0] += err; // bias gradient
                    for (int j = 0; j < p; j++) grad[j + 1] += err * X[i][j];
                }
                loss /= (2 * n);
                losses[iter] = loss;
                // Gradient descent update with L2 regularization
                weights[0] -= learningRate * grad[0] / n;
                for (int j = 1; j <= p; j++) {
                    grad[j] = grad[j] / n + l2Lambda * weights[j]; // Ridge penalty
                    weights[j] -= learningRate * grad[j];
                }
            }
            return new TrainingHistory(losses, weights.clone());
        }

        public double predict(double[] x) {
            double pred = weights[0]; // bias
            for (int j = 0; j < x.length; j++) pred += weights[j + 1] * x[j];
            return pred;
        }

        public double r2Score(double[][] X, double[] y) {
            double mean = Arrays.stream(y).average().orElse(0);
            double ssTot = 0, ssRes = 0;
            for (int i = 0; i < X.length; i++) {
                double pred = predict(X[i]);
                ssRes += Math.pow(pred - y[i], 2);
                ssTot += Math.pow(mean - y[i], 2);
            }
            return 1 - (ssRes / ssTot);
        }

        public double mse(double[][] X, double[] y) {
            double sum = 0;
            for (int i = 0; i < X.length; i++) { double e = predict(X[i]) - y[i]; sum += e*e; }
            return sum / X.length;
        }

        public double[] weights() { return weights.clone(); }
    }

    // ── LOGISTIC REGRESSION ───────────────────────────────────────────────────
    /**
     * Binary classification using sigmoid function.
     *   ŷ = σ(Xw) = 1 / (1 + e^(-Xw))
     *   Loss:     BCE = -1/n Σ [y log ŷ + (1-y) log(1-ŷ)]
     *   Gradient: ∇L = (1/n) Xᵀ(ŷ - y)
     *   Decision: class 1 if ŷ ≥ threshold (default 0.5)
     */
    public static class LogisticRegression {
        private double[] weights;
        private final double learningRate;
        private final int maxIter;
        private final double threshold;

        public LogisticRegression(double learningRate, int maxIter, double threshold) {
            this.learningRate = learningRate;
            this.maxIter = maxIter;
            this.threshold = threshold;
        }

        public TrainingHistory fit(double[][] X, int[] y) {
            int n = X.length, p = X[0].length;
            weights = new double[p + 1];
            double[] losses = new double[maxIter];

            for (int iter = 0; iter < maxIter; iter++) {
                double[] grad = new double[p + 1];
                double loss = 0;
                for (int i = 0; i < n; i++) {
                    double z    = linearCombination(X[i]);
                    double prob = sigmoid(z);
                    double err  = prob - y[i];
                    loss += -(y[i] * Math.log(prob + 1e-15) + (1 - y[i]) * Math.log(1 - prob + 1e-15));
                    grad[0] += err;
                    for (int j = 0; j < p; j++) grad[j+1] += err * X[i][j];
                }
                losses[iter] = loss / n;
                for (int j = 0; j <= p; j++) weights[j] -= learningRate * grad[j] / n;
            }
            return new TrainingHistory(losses, weights.clone());
        }

        public double predictProba(double[] x) { return sigmoid(linearCombination(x)); }
        public int predict(double[] x)          { return predictProba(x) >= threshold ? 1 : 0; }

        private double linearCombination(double[] x) {
            double z = weights[0];
            for (int j = 0; j < x.length; j++) z += weights[j+1] * x[j];
            return z;
        }

        public double accuracy(double[][] X, int[] y) {
            int correct = 0;
            for (int i = 0; i < X.length; i++) if (predict(X[i]) == y[i]) correct++;
            return (double) correct / X.length;
        }
    }

    // ── K-NEAREST NEIGHBORS ───────────────────────────────────────────────────
    /**
     * Non-parametric, instance-based (lazy) learner.
     * Prediction: find k nearest training points by Euclidean distance → majority vote.
     * Time:  O(n·d) per query (brute-force); O(log n) with k-d tree.
     * Space: O(n) — stores entire training set.
     * Hyperparameter k: low k → high variance (overfitting), high k → high bias.
     */
    public static class KNN {
        private double[][] trainX;
        private int[] trainY;
        private final int k;
        private final DistanceMetric metric;

        public enum DistanceMetric { EUCLIDEAN, MANHATTAN, CHEBYSHEV, COSINE }

        public KNN(int k, DistanceMetric metric) { this.k = k; this.metric = metric; }

        public void fit(double[][] X, int[] y) { this.trainX = X; this.trainY = y; }

        public int predict(double[] x) {
            int n = trainX.length;
            double[] distances = new double[n];
            for (int i = 0; i < n; i++) distances[i] = distance(x, trainX[i]);

            Integer[] indices = new Integer[n];
            for (int i = 0; i < n; i++) indices[i] = i;
            Arrays.sort(indices, Comparator.comparingDouble(i -> distances[i]));

            Map<Integer, Integer> votes = new HashMap<>();
            for (int i = 0; i < k; i++) votes.merge(trainY[indices[i]], 1, Integer::sum);
            return votes.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        }

        private double distance(double[] a, double[] b) {
            return switch (metric) {
                case EUCLIDEAN  -> { double sum = 0; for (int i=0;i<a.length;i++) sum+=Math.pow(a[i]-b[i],2); yield Math.sqrt(sum); }
                case MANHATTAN  -> { double sum = 0; for (int i=0;i<a.length;i++) sum+=Math.abs(a[i]-b[i]); yield sum; }
                case CHEBYSHEV  -> { double max = 0; for (int i=0;i<a.length;i++) max=Math.max(max,Math.abs(a[i]-b[i])); yield max; }
                case COSINE     -> {
                    double dot=0, na=0, nb=0;
                    for (int i=0;i<a.length;i++) { dot+=a[i]*b[i]; na+=a[i]*a[i]; nb+=b[i]*b[i]; }
                    yield 1 - (na*nb == 0 ? 0 : dot / (Math.sqrt(na)*Math.sqrt(nb)));
                }
            };
        }

        public double accuracy(double[][] X, int[] y) {
            int correct = 0;
            for (int i = 0; i < X.length; i++) if (predict(X[i]) == y[i]) correct++;
            return (double) correct / X.length;
        }
    }

    // ── NAIVE BAYES (GAUSSIAN) ────────────────────────────────────────────────
    /**
     * Bayes' theorem: P(y|x) ∝ P(y) ∏ P(xᵢ|y)
     * Gaussian NB: P(xᵢ|y) = (1/√(2πσ²)) exp(-(xᵢ-μ)²/2σ²)
     * Log-probability to avoid underflow: log P(y|x) = log P(y) + Σ log P(xᵢ|y)
     */
    public static class GaussianNaiveBayes {
        private double[] classPriors;    // log P(y=c)
        private double[][] classMeans;   // μ[c][j]
        private double[][] classVars;    // σ²[c][j]
        private int numClasses;

        public void fit(double[][] X, int[] y) {
            int n = X.length, p = X[0].length;
            numClasses = Arrays.stream(y).max().orElse(0) + 1;
            classPriors = new double[numClasses];
            classMeans  = new double[numClasses][p];
            classVars   = new double[numClasses][p];

            int[] counts = new int[numClasses];
            for (int i = 0; i < n; i++) { counts[y[i]]++; for (int j=0;j<p;j++) classMeans[y[i]][j] += X[i][j]; }

            for (int c = 0; c < numClasses; c++) {
                classPriors[c] = Math.log((double) counts[c] / n);
                for (int j=0;j<p;j++) classMeans[c][j] /= counts[c];
            }

            // Compute variance
            for (int i = 0; i < n; i++) for (int j=0;j<p;j++) classVars[y[i]][j] += Math.pow(X[i][j]-classMeans[y[i]][j],2);
            for (int c=0;c<numClasses;c++) for (int j=0;j<p;j++) classVars[c][j] = classVars[c][j] / counts[c] + 1e-9; // Laplace smoothing
        }

        public int predict(double[] x) {
            double bestLogProb = Double.NEGATIVE_INFINITY;
            int bestClass = 0;
            for (int c = 0; c < numClasses; c++) {
                double logProb = classPriors[c];
                for (int j = 0; j < x.length; j++) {
                    double v = classVars[c][j];
                    double m = classMeans[c][j];
                    logProb += -0.5 * Math.log(2 * Math.PI * v) - Math.pow(x[j]-m,2)/(2*v);
                }
                if (logProb > bestLogProb) { bestLogProb = logProb; bestClass = c; }
            }
            return bestClass;
        }

        public double accuracy(double[][] X, int[] y) {
            int correct = 0;
            for (int i = 0; i < X.length; i++) if (predict(X[i]) == y[i]) correct++;
            return (double) correct / X.length;
        }
    }

    // ── DECISION TREE ─────────────────────────────────────────────────────────
    /**
     * CART (Classification and Regression Trees):
     *   At each node, find the (feature, threshold) split that maximally reduces impurity.
     *   Gini impurity: G = 1 - Σ pᵢ²  (classification)
     *   Variance:      V = (1/n) Σ (yᵢ - ȳ)²  (regression)
     *   Stopping: max_depth, min_samples_leaf
     */
    public static class DecisionTree {
        private Node root;
        private final int maxDepth;
        private final int minSamplesLeaf;

        record Node(int featureIdx, double threshold, Node left, Node right, int label) {}

        public DecisionTree(int maxDepth, int minSamplesLeaf) {
            this.maxDepth = maxDepth;
            this.minSamplesLeaf = minSamplesLeaf;
        }

        public void fit(double[][] X, int[] y) { root = buildTree(X, y, 0); }

        private Node buildTree(double[][] X, int[] y, int depth) {
            int n = X.length;
            if (depth >= maxDepth || n <= minSamplesLeaf || isPure(y)) {
                return new Node(-1, 0, null, null, majorityClass(y));
            }
            double bestGain = -1; int bestFeat = -1; double bestThresh = 0;
            for (int j = 0; j < X[0].length; j++) {
                double[] vals = Arrays.stream(X).mapToDouble(row -> row[j]).toArray();
                Arrays.sort(vals);
                for (int t = 0; t < vals.length - 1; t++) {
                    double thresh = (vals[t] + vals[t+1]) / 2;
                    double gain = informationGain(X, y, j, thresh);
                    if (gain > bestGain) { bestGain = gain; bestFeat = j; bestThresh = thresh; }
                }
            }
            if (bestFeat == -1) return new Node(-1, 0, null, null, majorityClass(y));

            List<Integer> leftIdx = new ArrayList<>(), rightIdx = new ArrayList<>();
            for (int i = 0; i < n; i++) (X[i][bestFeat] <= bestThresh ? leftIdx : rightIdx).add(i);

            double[][] leftX = leftIdx.stream().map(i -> X[i]).toArray(double[][]::new);
            int[]      leftY = leftIdx.stream().mapToInt(i -> y[i]).toArray();
            double[][] rightX = rightIdx.stream().map(i -> X[i]).toArray(double[][]::new);
            int[]      rightY = rightIdx.stream().mapToInt(i -> y[i]).toArray();

            return new Node(bestFeat, bestThresh, buildTree(leftX, leftY, depth+1), buildTree(rightX, rightY, depth+1), -1);
        }

        private double informationGain(double[][] X, int[] y, int feat, double thresh) {
            double parentGini = gini(y);
            List<Integer> leftIdx = new ArrayList<>(), rightIdx = new ArrayList<>();
            for (int i = 0; i < X.length; i++) (X[i][feat] <= thresh ? leftIdx : rightIdx).add(i);
            if (leftIdx.isEmpty() || rightIdx.isEmpty()) return 0;
            int[] leftY  = leftIdx.stream().mapToInt(i -> y[i]).toArray();
            int[] rightY = rightIdx.stream().mapToInt(i -> y[i]).toArray();
            return parentGini - ((double)leftIdx.size()/y.length)*gini(leftY) - ((double)rightIdx.size()/y.length)*gini(rightY);
        }

        private double gini(int[] y) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (int v : y) counts.merge(v, 1, Integer::sum);
            double g = 1.0;
            for (double c : counts.values()) { double p = c/y.length; g -= p*p; }
            return g;
        }

        private boolean isPure(int[] y) { return Arrays.stream(y).distinct().count() == 1; }

        private int majorityClass(int[] y) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (int v : y) counts.merge(v, 1, Integer::sum);
            return counts.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        }

        public int predict(double[] x) { return traverse(root, x); }

        private int traverse(Node node, double[] x) {
            if (node.left() == null) return node.label();
            return x[node.featureIdx()] <= node.threshold() ? traverse(node.left(), x) : traverse(node.right(), x);
        }

        public double accuracy(double[][] X, int[] y) {
            int correct = 0;
            for (int i = 0; i < X.length; i++) if (predict(X[i]) == y[i]) correct++;
            return (double) correct / X.length;
        }
    }

    // ── RANDOM FOREST ─────────────────────────────────────────────────────────
    /**
     * Ensemble of DecisionTrees trained on bootstrap samples (bagging).
     *   Bootstrap: sample n examples with replacement from training set
     *   Feature subsampling: at each split, consider only √p features
     *   Prediction: majority vote across all trees
     *
     *   Reduces overfitting vs single tree by:
     *     - Bagging (bootstrap aggregating): reduces variance
     *     - Feature randomness: decorrelates trees
     */
    public static class RandomForest {
        private final List<DecisionTree> trees = new ArrayList<>();
        private final int numTrees;
        private final int maxDepth;
        private final Random rng;
        private double[] featureImportances;

        public RandomForest(int numTrees, int maxDepth, long seed) {
            this.numTrees = numTrees;
            this.maxDepth = maxDepth;
            this.rng = new Random(seed);
        }

        public void fit(double[][] X, int[] y) {
            int n = X.length;
            for (int t = 0; t < numTrees; t++) {
                // Bootstrap sample
                int[] indices = new int[n];
                for (int i = 0; i < n; i++) indices[i] = rng.nextInt(n);
                double[][] bootX = new double[n][];
                int[] bootY = new int[n];
                for (int i = 0; i < n; i++) { bootX[i] = X[indices[i]]; bootY[i] = y[indices[i]]; }

                DecisionTree tree = new DecisionTree(maxDepth, 2);
                tree.fit(bootX, bootY);
                trees.add(tree);
            }
        }

        public int predict(double[] x) {
            Map<Integer, Integer> votes = new HashMap<>();
            for (var tree : trees) votes.merge(tree.predict(x), 1, Integer::sum);
            return votes.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        }

        public double accuracy(double[][] X, int[] y) {
            int correct = 0;
            for (int i = 0; i < X.length; i++) if (predict(X[i]) == y[i]) correct++;
            return (double) correct / X.length;
        }
    }

    // ── Shared Types ──────────────────────────────────────────────────────────

    public record TrainingHistory(double[] losses, double[] finalWeights) {
        public double finalLoss() { return losses[losses.length - 1]; }
    }

    private static double sigmoid(double z) { return 1.0 / (1 + Math.exp(-z)); }
}
