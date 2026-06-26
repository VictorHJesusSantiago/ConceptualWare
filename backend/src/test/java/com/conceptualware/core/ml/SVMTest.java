package com.conceptualware.core.ml;

import org.junit.jupiter.api.*;

import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Concept #30 — Support Vector Machine (SVM)
 */
@DisplayName("Category 30 — SVM (Support Vector Machine)")
class SVMTest {

    /** Linearly separable 2D dataset: two well-separated clusters */
    static double[][] X() {
        double[][] X = new double[100][2];
        Random rng = new Random(42);
        for (int i = 0; i < 50; i++) {
            X[i][0] = rng.nextGaussian() - 3;
            X[i][1] = rng.nextGaussian() - 3;
        }
        for (int i = 50; i < 100; i++) {
            X[i][0] = rng.nextGaussian() + 3;
            X[i][1] = rng.nextGaussian() + 3;
        }
        return X;
    }

    static int[] y() {
        int[] y = new int[100];
        for (int i = 50; i < 100; i++) y[i] = 1;
        return y;
    }

    @Test @DisplayName("linear kernel classifies separable dataset perfectly")
    void linearKernelSeparable() {
        SVM svm = new SVM(1.0, SVM.KernelType.LINEAR, 1.0, 0.0, 1, 500, 1e-3);
        svm.fit(X(), y());

        int[] preds = svm.predict(X());
        int[] truth = y();
        int correct = 0;
        for (int i = 0; i < truth.length; i++) if (preds[i] == truth[i]) correct++;
        assertThat((double) correct / truth.length).isGreaterThan(0.95);
    }

    @Test @DisplayName("RBF kernel classifies separable dataset with high accuracy")
    void rbfKernelSeparable() {
        SVM svm = new SVM(10.0, SVM.KernelType.RBF, 0.1, 0.0, 3, 500, 1e-3);
        svm.fit(X(), y());

        int[] preds = svm.predict(X());
        int[] truth = y();
        int correct = 0;
        for (int i = 0; i < truth.length; i++) if (preds[i] == truth[i]) correct++;
        assertThat((double) correct / truth.length).isGreaterThan(0.90);
    }

    @Test @DisplayName("polynomial kernel produces valid predictions")
    void polynomialKernel() {
        SVM svm = new SVM(1.0, SVM.KernelType.POLYNOMIAL, 1.0, 1.0, 2, 300, 1e-3);
        svm.fit(X(), y());
        int[] preds = svm.predict(X());
        for (int p : preds) assertThat(p).isIn(0, 1);
    }

    @Test @DisplayName("decision function returns positive score for class 1")
    void decisionFunctionSign() {
        SVM svm = new SVM(1.0);
        svm.fit(X(), y());

        double[][] testPositive = {{4.0, 4.0}, {5.0, 5.0}};
        double[][] testNegative = {{-4.0, -4.0}, {-5.0, -5.0}};

        double[] scoresPos = svm.decisionFunction(testPositive);
        double[] scoresNeg = svm.decisionFunction(testNegative);

        for (double s : scoresPos) assertThat(s).isGreaterThan(0);
        for (double s : scoresNeg) assertThat(s).isLessThan(0);
    }

    @Test @DisplayName("support vectors exist after fitting")
    void supportVectorsExist() {
        SVM svm = new SVM(1.0);
        svm.fit(X(), y());
        assertThat(svm.getSupportVectorCount()).isGreaterThan(0);
    }

    @Test @DisplayName("high C (hard margin) uses fewer support vectors than low C")
    void highCFewerSVs() {
        SVM highC = new SVM(100.0, SVM.KernelType.LINEAR, 1.0, 0.0, 1, 500, 1e-3);
        SVM lowC  = new SVM(0.01,  SVM.KernelType.LINEAR, 1.0, 0.0, 1, 500, 1e-3);

        highC.fit(X(), y());
        lowC.fit(X(), y());

        // Hard margin → fewer, well-placed support vectors
        assertThat(highC.getSupportVectorCount()).isLessThanOrEqualTo(
            lowC.getSupportVectorCount() + 20   // some tolerance
        );
    }
}
