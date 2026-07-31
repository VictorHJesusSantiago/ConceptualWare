package com.conceptualware.core.ml;

import java.util.Arrays;
import java.util.Random;

public class SVM {

    public enum KernelType { LINEAR, POLYNOMIAL, RBF, SIGMOID }

    private final double      C;
    private final KernelType  kernelType;
    private final double      gamma;
    private final double      coef0;
    private final int         degree;
    private final int         maxIter;
    private final double      tol;

    private double[]   alphas;
    private double     bias;
    private double[][] supportVecs;
    private int[]      labels;
    private int[]      supportIdx;

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

    public SVM(double C) {
        this(C, KernelType.RBF, 1.0, 0.0, 3, 1000, 1e-3);
    }

    public SVM() { this(1.0); }

    public void fit(double[][] X, int[] y) {
        int n = X.length;
        supportVecs = X;

        labels = new int[n];
        for (int i = 0; i < n; i++) labels[i] = y[i] == 1 ? 1 : -1;

        alphas = new double[n];
        bias   = 0.0;

        double[][] K = computeKernelMatrix(X);

        Random rng = new Random(42L);
        int passes = 0;
        int maxPasses = Math.min(maxIter, n * 10);

        int totalSweeps = 0;
        int maxTotalSweeps = Math.max(maxPasses, n) * 20;

        while (passes < maxPasses && totalSweeps < maxTotalSweeps) {
            totalSweeps++;
            int numChanged = 0;

            for (int i = 0; i < n; i++) {
                double Ei = decisionFunction(i, K) - labels[i];

                boolean violatesKKT = (labels[i] * Ei < -tol && alphas[i] < C)
                                   || (labels[i] * Ei >  tol && alphas[i] > 0);

                if (!violatesKKT) continue;

                int j = rng.nextInt(n - 1);
                if (j >= i) j++;

                double Ej = decisionFunction(j, K) - labels[j];

                double alphaI_old = alphas[i];
                double alphaJ_old = alphas[j];

                double L, H;
                if (labels[i] != labels[j]) {
                    L = Math.max(0, alphas[j] - alphas[i]);
                    H = Math.min(C, C + alphas[j] - alphas[i]);
                } else {
                    L = Math.max(0, alphas[i] + alphas[j] - C);
                    H = Math.min(C, alphas[i] + alphas[j]);
                }
                if (L >= H) continue;

                double eta = K[i][i] + K[j][j] - 2.0 * K[i][j];
                if (eta <= 0) continue;

                alphas[j] += labels[j] * (Ei - Ej) / eta;
                alphas[j]  = Math.min(H, Math.max(L, alphas[j]));

                if (Math.abs(alphas[j] - alphaJ_old) < 1e-8) continue;

                alphas[i] += labels[i] * labels[j] * (alphaJ_old - alphas[j]);

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

        int svCount = 0;
        for (double a : alphas) if (a > tol) svCount++;
        supportIdx = new int[svCount];
        int k = 0;
        for (int i = 0; i < n; i++) if (alphas[i] > tol) supportIdx[k++] = i;
    }

    public int[] predict(double[][] X) {
        int[] preds = new int[X.length];
        for (int i = 0; i < X.length; i++) {
            preds[i] = rawScore(X[i]) >= 0 ? 1 : 0;
        }
        return preds;
    }

    public double[] decisionFunction(double[][] X) {
        double[] scores = new double[X.length];
        for (int i = 0; i < X.length; i++) scores[i] = rawScore(X[i]);
        return scores;
    }

    public int getSupportVectorCount() { return supportIdx == null ? 0 : supportIdx.length; }

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
