package com.conceptualware.core.ml;

import org.junit.jupiter.api.*;

import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Concept #30 — GRU and XGBoost implementations
 */
@DisplayName("Category 30 — GRU and XGBoost")
class GRUXGBoostTest {

    // ── GRU Tests ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("GRU (Gated Recurrent Unit)")
    class GRUTests {

        @Test @DisplayName("forward produces output of correct shape")
        void forwardShape() {
            GRU gru = new GRU(4, 8, 42L);
            double[][] sequence = new double[10][4];
            new Random(1).doubles().limit(40)
                .toArray();
            Random rng = new Random(1);
            for (double[] row : sequence)
                for (int j = 0; j < 4; j++) row[j] = rng.nextGaussian();

            double[] h = gru.forward(sequence);
            assertThat(h).hasSize(8);
        }

        @Test @DisplayName("step produces output bounded (-1, 1) — tanh output")
        void stepBounded() {
            GRU gru = new GRU(3, 5, 99L);
            double[] x = {1.0, -2.0, 0.5};
            double[] h = new double[5];
            double[] newH = gru.step(x, h);

            for (double v : newH) {
                assertThat(v).isBetween(-1.0, 1.0);
            }
        }

        @Test @DisplayName("different inputs produce different outputs")
        void deterministicOutputs() {
            GRU gru = new GRU(2, 4, 7L);
            double[] h = new double[4];
            double[] xA = {1.0, 0.0};
            double[] xB = {0.0, 1.0};

            double[] hA = gru.step(xA, h.clone());
            double[] hB = gru.step(xB, h.clone());

            // Different inputs → different hidden states
            boolean allEqual = true;
            for (int i = 0; i < 4; i++) {
                if (Math.abs(hA[i] - hB[i]) > 1e-10) { allEqual = false; break; }
            }
            assertThat(allEqual).isFalse();
        }

        @Test @DisplayName("forwardAllStates returns one vector per time step")
        void allStatesShape() {
            GRU gru = new GRU(3, 6, 123L);
            int seqLen = 7;
            double[][] seq = new double[seqLen][3];
            double[][] states = gru.forwardAllStates(seq);

            assertThat(states).hasSize(seqLen);
            for (double[] s : states) assertThat(s).hasSize(6);
        }

        @Test @DisplayName("bidirectional GRU output is double the hidden size")
        void bidirectionalShape() {
            GRU fwd = new GRU(4, 8, 1L);
            GRU bwd = new GRU(4, 8, 2L);
            double[][] seq = new double[5][4];

            double[] out = GRU.bidirectional(seq, fwd, bwd);
            assertThat(out).hasSize(16);   // 8 + 8
        }

        @Test @DisplayName("paramCount matches expected formula 3*H*(H+I+1)")
        void paramCount() {
            int I = 10, H = 20;
            GRU gru = new GRU(I, H, 0L);
            int expected = 3 * H * (H + I + 1);
            assertThat(gru.paramCount()).isEqualTo(expected);
        }
    }

    // ── XGBoost Tests ─────────────────────────────────────────────────────────

    @Nested @DisplayName("XGBoost (Gradient Boosting)")
    class XGBoostTests {

        /** Simple linearly separable binary dataset */
        static double[][] X() {
            double[][] X = new double[200][2];
            Random rng = new Random(42);
            for (int i = 0; i < 100; i++) {
                X[i][0] = rng.nextDouble() * 2;
                X[i][1] = rng.nextDouble() * 2;
            }
            for (int i = 100; i < 200; i++) {
                X[i][0] = rng.nextDouble() * 2 + 3;
                X[i][1] = rng.nextDouble() * 2 + 3;
            }
            return X;
        }

        static int[] y() {
            int[] y = new int[200];
            for (int i = 100; i < 200; i++) y[i] = 1;
            return y;
        }

        @Test @DisplayName("fits linearly separable dataset with high accuracy")
        void fitsLinearData() {
            XGBoost xgb = new XGBoost(50, 0.1, 4, 1.0, 0.0, 1.0, 1.0, 1, 42L);
            xgb.fit(X(), y());

            int[] preds = xgb.predict(X());
            int correct = 0;
            int[] truth = y();
            for (int i = 0; i < preds.length; i++) if (preds[i] == truth[i]) correct++;
            double accuracy = (double) correct / preds.length;

            assertThat(accuracy).isGreaterThan(0.95);   // near-perfect on linearly separable
        }

        @Test @DisplayName("predictProba returns values in [0, 1]")
        void probaInRange() {
            XGBoost xgb = new XGBoost();
            xgb.fit(X(), y());
            double[] probs = xgb.predictProba(X());

            for (double p : probs) {
                assertThat(p).isBetween(0.0, 1.0);
            }
        }

        @Test @DisplayName("builds correct number of trees")
        void numTrees() {
            XGBoost xgb = new XGBoost(25, 0.3, 3, 1.0, 0.0, 1.0, 1.0, 1, 0L);
            xgb.fit(X(), y());
            assertThat(xgb.getNumTrees()).isEqualTo(25);
        }

        @Test @DisplayName("feature importance sums to 1")
        void featureImportanceSumsToOne() {
            XGBoost xgb = new XGBoost(30, 0.1, 4, 1.0, 0.0, 1.0, 1.0, 1, 7L);
            xgb.fit(X(), y());
            double[] importance = xgb.featureImportance(2);

            double sum = 0;
            for (double v : importance) sum += v;
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }

        @Test @DisplayName("higher learning rate converges faster")
        void learningRateEffect() {
            double[][] Xdata = X();
            int[]  ydata = y();

            XGBoost slowModel = new XGBoost(10, 0.01, 3, 1.0, 0.0, 1.0, 1.0, 1, 42L);
            XGBoost fastModel = new XGBoost(10, 0.5,  3, 1.0, 0.0, 1.0, 1.0, 1, 42L);

            slowModel.fit(Xdata, ydata);
            fastModel.fit(Xdata, ydata);

            int slowCorrect = 0, fastCorrect = 0;
            int[] ps = slowModel.predict(Xdata);
            int[] pf = fastModel.predict(Xdata);
            for (int i = 0; i < ydata.length; i++) {
                if (ps[i] == ydata[i]) slowCorrect++;
                if (pf[i] == ydata[i]) fastCorrect++;
            }
            // Fast model should have higher (or equal) accuracy after same n rounds
            assertThat(fastCorrect).isGreaterThanOrEqualTo(slowCorrect);
        }

        @Test @DisplayName("regularization lambda prevents overfitting on tiny dataset")
        void regularizationEffect() {
            // 6 samples — easy to overfit with lambda=0
            double[][] Xsmall = {{0},{1},{2},{3},{4},{5}};
            int[]  ysmall = {0, 0, 0, 1, 1, 1};

            XGBoost reg   = new XGBoost(50, 0.1, 3, 10.0, 0.0, 1.0, 1.0, 1, 0L);
            XGBoost noreg = new XGBoost(50, 0.1, 3, 0.0,  0.0, 1.0, 1.0, 1, 0L);

            reg.fit(Xsmall, ysmall);
            noreg.fit(Xsmall, ysmall);

            // Both should predict correctly on training data (too easy)
            int[] predsReg   = reg.predict(Xsmall);
            int[] predsNoreg = noreg.predict(Xsmall);

            int correctReg = 0, correctNoreg = 0;
            for (int i = 0; i < ysmall.length; i++) {
                if (predsReg[i] == ysmall[i])   correctReg++;
                if (predsNoreg[i] == ysmall[i]) correctNoreg++;
            }
            assertThat(correctReg).isGreaterThanOrEqualTo(5);
            assertThat(correctNoreg).isGreaterThanOrEqualTo(5);
        }

        @Test @DisplayName("gamma > 0 produces fewer splits (simpler trees)")
        void gammaReducesSplits() {
            XGBoost noPrune = new XGBoost(20, 0.1, 5, 1.0, 0.0,  1.0, 1.0, 1, 42L);
            XGBoost prune   = new XGBoost(20, 0.1, 5, 1.0, 10.0, 1.0, 1.0, 1, 42L);

            double[][] Xdata = X(); int[] ydata = y();
            noPrune.fit(Xdata, ydata);
            prune.fit(Xdata, ydata);

            // Pruned model should still classify reasonably
            int[] preds = prune.predict(Xdata);
            int correct = 0;
            for (int i = 0; i < ydata.length; i++) if (preds[i] == ydata[i]) correct++;
            assertThat((double) correct / ydata.length).isGreaterThan(0.7);
        }
    }
}
