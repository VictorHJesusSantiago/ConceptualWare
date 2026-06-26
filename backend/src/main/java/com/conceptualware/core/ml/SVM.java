package com.conceptualware.core.ml;

import java.util.Arrays;
import java.util.Random;

/**
 * Concept #30 — Support Vector Machine (SVM)
 *
 * SVM (Vapnik, 1963/1995) finds the maximum-margin hyperplane that separates
 * two classes. The margin is the distance from the hyperplane to the nearest
 * training samples (support vectors).
 *
 * Hard-margin SVM (linearly separable):
 *   minimize ½‖w‖²
 *   subject to yᵢ(wᵀxᵢ + b) ≥ 1  ∀i
 *
 * Soft-margin SVM (non-separable, with slack variables ξᵢ):
 *   minimize ½‖w‖² + C Σᵢ ξᵢ
 *   subject to yᵢ(wᵀxᵢ + b) ≥ 1 − ξᵢ,  ξᵢ ≥ 0
 *
 *   C controls bias-variance tradeoff:
 *     C large → small margin, few misclassifications (low bias, high variance)
 *     C small → large margin, more misclassifications (high bias, low variance)
 *
 * Kernel trick:
 *   Replace xᵢᵀxⱼ with K(xᵢ, xⱼ) to implicitly map to high-dimensional space.
 *   K(x, z) = φ(x)ᵀφ(z)  — inner product in feature space
 *   No need to compute φ(x) explicitly — only the kernel value is needed.
 *
 * Kernels implemented:
 *   Linear:     K(x,z) = xᵀz
 *   Polynomial: K(x,z) = (γ·xᵀz + r)^d
 *   RBF/Gauss:  K(x,z) = exp(−γ‖x−z‖²)   ← most popular, handles non-linear
 *   Sigmoid:    K(x,z) = tanh(γ·xᵀz + r)
 *
 * Training via SMO (Sequential Minimal Optimization, Platt 1998):
 *   SMO decomposes the QP into smallest possible sub-problems (2 variables).
 *   It analytically solves each 2-variable sub-problem in closed form,
 *   avoiding expensive matrix factorization used in full QP solvers.
 *
 * Dual formulation:
 *   maximize  Σᵢ αᵢ − ½ Σᵢ Σⱼ αᵢ αⱼ yᵢ yⱼ K(xᵢ, xⱼ)
 *   subject to 0 ≤ αᵢ ≤ C,  Σᵢ αᵢ yᵢ = 0
 *
 *   Prediction: f(x) = Σᵢ αᵢ yᵢ K(xᵢ, x) + b
 *   Support vectors: samples where αᵢ > 0
 */
public class SVM {

    public enum KernelType { LINEAR, POLYNOMIAL, RBF, SIGMOID }

    // ── Hyperparameters ───────────────────────────────────────────────────────

    private final double      C;           // regularization (inverse of margin width)
    private final KernelType  kernelType;
    private final double      gamma;       // kernel bandwidth (RBF/Poly/Sigmoid)
    private final double      coef0;       // intercept term (Poly/Sigmoid: r)
    private final int         degree;      // polynomial degree (Poly)
    private final int         maxIter;
    private final double      tol;         // convergence tolerance

    // ── Model state ───────────────────────────────────────────────────────────

    private double[]   alphas;       // Lagrange multipliers [n]
    private double     bias;         // threshold b
    private double[][] supportVecs;  // training data (kept for kernel eval at predict)
    private int[]      labels;       // training labels in {-1, +1}
    private int[]      supportIdx;   // indices of support vectors (αᵢ > tol)

    public SVM(double C, KernelType kernel, double gamma,
               double coef0, int degree, int maxIter, double tol) {
        this.C          = C;
        this.kernelType = kernel;
        this.gamma      = gamma;
        this.coef0      = coef0;
        this.degree     = degree;
        this.maxIter    = maxIter;
        this.tol        = tol;
    }

    /** RBF kernel with default gamma = 1/(n_features * Var(X)) set at fit time. */
    public SVM(double C) {
        this(C, KernelType.RBF, 1.0, 0.0, 3, 1000, 1e-3);
    }

    public SVM() { this(1.0); }

    // ── Training — Simplified SMO ─────────────────────────────────────────────

    /**
     * Fit on binary data. Labels should be in {0, 1} — converted internally to {-1, +1}.
     *
     * @param X  feature matrix [n × p]
     * @param y  binary labels {0, 1} [n]
     */
    public void fit(double[][] X, int[] y) {
        int n = X.length;
        supportVecs = X;

        // Convert {0,1} → {-1,+1}
        labels = new int[n];
        for (int i = 0; i < n; i++) labels[i] = y[i] == 1 ? 1 : -1;

        alphas = new double[n];   // initialize all αᵢ = 0
        bias   = 0.0;

        // Precompute kernel matrix (O(n²) — feasible for small/medium datasets)
        double[][] K = computeKernelMatrix(X);

        // SMO main loop
        Random rng = new Random(42L);
        int passes = 0;
        int maxPasses = Math.min(maxIter, n * 10);

        while (passes < maxPasses) {
            int numChanged = 0;

            for (int i = 0; i < n; i++) {
                // Error at sample i
                double Ei = decisionFunction(i, K) - labels[i];

                // KKT condition check:
                //   αᵢ = 0  ⟹  yᵢ·fᵢ ≥ 1
                //   αᵢ = C  ⟹  yᵢ·fᵢ ≤ 1
                //   0<αᵢ<C  ⟹  yᵢ·fᵢ = 1
                boolean violatesKKT = (labels[i] * Ei < -tol && alphas[i] < C)
                                   || (labels[i] * Ei >  tol && alphas[i] > 0);

                if (!violatesKKT) continue;

                // Pick j ≠ i randomly (heuristic — full SMO uses second choice heuristic)
                int j = rng.nextInt(n - 1);
                if (j >= i) j++;

                double Ej = decisionFunction(j, K) - labels[j];

                double alphaI_old = alphas[i];
                double alphaJ_old = alphas[j];

                // Compute bounds L, H for αⱼ (box constraint + linear constraint)
                double L, H;
                if (labels[i] != labels[j]) {
                    L = Math.max(0, alphas[j] - alphas[i]);
                    H = Math.min(C, C + alphas[j] - alphas[i]);
                } else {
                    L = Math.max(0, alphas[i] + alphas[j] - C);
                    H = Math.min(C, alphas[i] + alphas[j]);
                }
                if (L >= H) continue;

                // Second-order optimization: η = K(xᵢ,xᵢ) + K(xⱼ,xⱼ) − 2K(xᵢ,xⱼ)
                double eta = K[i][i] + K[j][j] - 2.0 * K[i][j];
                if (eta <= 0) continue;

                // Update αⱼ (unconstrained): αⱼ += yⱼ(Eᵢ − Eⱼ)/η
                alphas[j] += labels[j] * (Ei - Ej) / eta;
                alphas[j]  = Math.min(H, Math.max(L, alphas[j]));   // clip to [L, H]

                if (Math.abs(alphas[j] - alphaJ_old) < 1e-8) continue;

                // Update αᵢ: αᵢ = αᵢ + yᵢyⱼ(αⱼ_old − αⱼ)
                alphas[i] += labels[i] * labels[j] * (alphaJ_old - alphas[j]);

                // Update bias b
                double b1 = bias - Ei
                          - labels[i] * (alphas[i] - alphaI_old) * K[i][i]
                          - labels[j] * (alphas[j] - alphaJ_old) * K[i][j];
                double b2 = bias - Ej
                          - labels[i] * (alphas[i] - alphaI_old) * K[i][j]
                          - labels[j] * (alphas[j] - alphaJ_old) * K[j][j];

                if (0 < alphas[i] && alphas[i] < C)       bias = b1;
                else if (0 < alphas[j] && alphas[j] < C)  bias = b2;
                else                                        bias = (b1 + b2) / 2.0;

                numChanged++;
            }

            passes = (numChanged == 0) ? passes + 1 : 0;
        }

        // Collect support vector indices
        int svCount = 0;
        for (double a : alphas) if (a > tol) svCount++;
        supportIdx = new int[svCount];
        int k = 0;
        for (int i = 0; i < n; i++) if (alphas[i] > tol) supportIdx[k++] = i;
    }

    // ── Prediction ────────────────────────────────────────────────────────────

    /**
     * Predict class label {0, 1} for each sample.
     */
    public int[] predict(double[][] X) {
        int[] preds = new int[X.length];
        for (int i = 0; i < X.length; i++) {
            preds[i] = rawScore(X[i]) >= 0 ? 1 : 0;
        }
        return preds;
    }

    /**
     * Raw decision function score: Σᵢ αᵢ yᵢ K(xᵢ, x) + b
     * Positive → class 1, negative → class -1.
     */
    public double[] decisionFunction(double[][] X) {
        double[] scores = new double[X.length];
        for (int i = 0; i < X.length; i++) scores[i] = rawScore(X[i]);
        return scores;
    }

    public int getSupportVectorCount() { return supportIdx == null ? 0 : supportIdx.length; }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private double rawScore(double[] x) {
        double score = bias;
        for (int i = 0; i < supportVecs.length; i++) {
            if (alphas[i] > tol) {
                score += alphas[i] * labels[i] * kernel(supportVecs[i], x);
            }
        }
        return score;
    }

    private double decisionFunction(int i, double[][] K) {
        double score = bias;
        for (int j = 0; j < supportVecs.length; j++) {
            if (alphas[j] > tol) {
                score += alphas[j] * labels[j] * K[j][i];
            }
        }
        return score;
    }

    private double[][] computeKernelMatrix(double[][] X) {
        int n = X.length;
        double[][] K = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = i; j < n; j++) {
                K[i][j] = K[j][i] = kernel(X[i], X[j]);
            }
        return K;
    }

    private double kernel(double[] a, double[] b) {
        return switch (kernelType) {
            case LINEAR     -> dot(a, b);
            case POLYNOMIAL -> Math.pow(gamma * dot(a, b) + coef0, degree);
            case RBF        -> Math.exp(-gamma * squaredDist(a, b));
            case SIGMOID    -> Math.tanh(gamma * dot(a, b) + coef0);
        };
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static double squaredDist(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) { double d = a[i] - b[i]; s += d * d; }
        return s;
    }
}
