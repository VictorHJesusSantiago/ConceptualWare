package com.conceptualware.core.ml;

import java.util.Random;

public class GRU {

    private final double[][] Wr;
    private final double[][] Wz;
    private final double[][] Wh;

    private final double[] br;
    private final double[] bz;
    private final double[] bh;

    private final int hiddenSize;
    private final int inputSize;
    private final Random rng;

    public GRU(int inputSize, int hiddenSize, long seed) {
        this.inputSize  = inputSize;
        this.hiddenSize = hiddenSize;
        this.rng        = new Random(seed);

        int sz = hiddenSize + inputSize;

        Wr = initMatrix(hiddenSize, sz);
        Wz = initMatrix(hiddenSize, sz);
        Wh = initMatrix(hiddenSize, sz);

        br = new double[hiddenSize];
        bz = new double[hiddenSize];
        bh = new double[hiddenSize];
    }

    public double[] step(double[] x, double[] h) {
        double[] hx = concat(h, x);

        double[] r = sigmoid(addBias(matVec(Wr, hx), br));

        double[] z = sigmoid(addBias(matVec(Wz, hx), bz));

        double[] rh = hadamard(r, h);
        double[] rhx = concat(rh, x);
        double[] hCandidate = tanh(addBias(matVec(Wh, rhx), bh));

        double[] newH = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            newH[i] = (1.0 - z[i]) * h[i] + z[i] * hCandidate[i];
        }
        return newH;
    }

    public double[] forward(double[][] sequence) {
        double[] h = new double[hiddenSize];
        for (double[] x : sequence) {
            h = step(x, h);
        }
        return h;
    }

    public double[][] forwardAllStates(double[][] sequence) {
        double[][] states = new double[sequence.length][hiddenSize];
        double[] h = new double[hiddenSize];
        for (int t = 0; t < sequence.length; t++) {
            h = step(sequence[t], h);
            states[t] = h.clone();
        }
        return states;
    }

    public static double[] bidirectional(double[][] sequence, GRU forward, GRU backward) {
        double[] hForward  = forward.forward(sequence);

        double[][] reversed = new double[sequence.length][];
        for (int i = 0; i < sequence.length; i++) {
            reversed[i] = sequence[sequence.length - 1 - i];
        }
        double[] hBackward = backward.forward(reversed);

        return concat(hForward, hBackward);
    }

    private double[][] initMatrix(int rows, int cols) {
        double[][] m = new double[rows][cols];
        double scale = Math.sqrt(1.0 / cols);
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                m[i][j] = rng.nextGaussian() * scale;
        return m;
    }

    private static double[] matVec(double[][] W, double[] x) {
        int rows = W.length, cols = W[0].length;
        double[] out = new double[rows];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                out[i] += W[i][j] * x[j];
        return out;
    }

    private static double[] addBias(double[] v, double[] b) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] + b[i];
        return out;
    }

    private static double[] sigmoid(double[] v) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = 1.0 / (1.0 + Math.exp(-v[i]));
        return out;
    }

    private static double[] tanh(double[] v) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Math.tanh(v[i]);
        return out;
    }

    private static double[] hadamard(double[] a, double[] b) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] * b[i];
        return out;
    }

    private static double[] concat(double[] a, double[] b) {
        double[] out = new double[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    public int getHiddenSize() { return hiddenSize; }
    public int getInputSize()  { return inputSize;  }

    public int paramCount() {
        return 3 * hiddenSize * (hiddenSize + inputSize + 1);
    }
}
