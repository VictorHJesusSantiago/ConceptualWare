package com.conceptualware.core.ml;

import java.util.Random;

/**
 * Concept #30 — Gated Recurrent Unit (GRU)
 *
 * GRU (Cho et al., 2014) is a simplified LSTM that merges the cell state
 * and hidden state into a single hidden state, and fuses the forget/input
 * gates into a single "update" gate. Fewer parameters → faster training,
 * competitive performance on many sequence tasks.
 *
 * GRU equations (per time step t):
 *   Reset gate:   rₜ = σ(Wr · [hₜ₋₁, xₜ] + br)
 *   Update gate:  zₜ = σ(Wz · [hₜ₋₁, xₜ] + bz)
 *   Candidate:    h̃ₜ = tanh(Wh · [rₜ ⊙ hₜ₋₁, xₜ] + bh)
 *   Hidden:       hₜ = (1 − zₜ) ⊙ hₜ₋₁ + zₜ ⊙ h̃ₜ
 *
 * Intuition:
 *   reset gate rₜ ≈ 0 → candidate ignores previous hidden state (new topic)
 *   update gate zₜ ≈ 1 → output is the candidate (full update)
 *   update gate zₜ ≈ 0 → output copies previous hidden state (skip this input)
 *
 * GRU vs LSTM:
 *   GRU  — 2 gates, 1 state, 3× weight matrices (smaller, faster)
 *   LSTM — 3 gates, 2 states (h + c), 4× weight matrices (larger, more expressive)
 *   Practical rule: try GRU first; use LSTM if GRU underfits on very long sequences.
 */
public class GRU {

    // Weight matrices: [hiddenSize × (hiddenSize + inputSize)]
    private final double[][] Wr;   // reset gate weights
    private final double[][] Wz;   // update gate weights
    private final double[][] Wh;   // candidate weights

    // Biases: [hiddenSize]
    private final double[] br;
    private final double[] bz;
    private final double[] bh;

    private final int hiddenSize;
    private final int inputSize;
    private final Random rng;

    /**
     * @param inputSize   dimensionality of each input vector xₜ
     * @param hiddenSize  dimensionality of hidden state hₜ
     * @param seed        random seed for reproducible initialization
     */
    public GRU(int inputSize, int hiddenSize, long seed) {
        this.inputSize  = inputSize;
        this.hiddenSize = hiddenSize;
        this.rng        = new Random(seed);

        int sz = hiddenSize + inputSize;   // concatenated [h, x] dimension

        // Xavier initialization: scale ∝ 1/√(fan_in) for tanh/sigmoid gates
        Wr = initMatrix(hiddenSize, sz);
        Wz = initMatrix(hiddenSize, sz);
        Wh = initMatrix(hiddenSize, sz);

        br = new double[hiddenSize];   // zero-initialized (standard)
        bz = new double[hiddenSize];
        bh = new double[hiddenSize];
    }

    /**
     * Process one time step.
     *
     * @param x  input vector [inputSize]
     * @param h  previous hidden state [hiddenSize]
     * @return   new hidden state hₜ [hiddenSize]
     */
    public double[] step(double[] x, double[] h) {
        // Concatenate [h, x] for gate computations
        double[] hx = concat(h, x);

        // Reset gate: rₜ = σ(Wr · [h,x] + br)
        double[] r = sigmoid(addBias(matVec(Wr, hx), br));

        // Update gate: zₜ = σ(Wz · [h,x] + bz)
        double[] z = sigmoid(addBias(matVec(Wz, hx), bz));

        // Candidate: h̃ₜ = tanh(Wh · [r⊙h, x] + bh)
        // Note: only the h part is gated by r — x is unchanged
        double[] rh = hadamard(r, h);                   // rₜ ⊙ hₜ₋₁
        double[] rhx = concat(rh, x);
        double[] hCandidate = tanh(addBias(matVec(Wh, rhx), bh));

        // Output: hₜ = (1−zₜ)⊙hₜ₋₁ + zₜ⊙h̃ₜ
        double[] newH = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            newH[i] = (1.0 - z[i]) * h[i] + z[i] * hCandidate[i];
        }
        return newH;
    }

    /**
     * Process a full sequence of inputs.
     *
     * @param sequence  array of input vectors, each [inputSize]
     * @return          final hidden state after processing all inputs
     */
    public double[] forward(double[][] sequence) {
        double[] h = new double[hiddenSize];   // h₀ = zero vector
        for (double[] x : sequence) {
            h = step(x, h);
        }
        return h;
    }

    /**
     * Process sequence and return ALL hidden states (useful for attention or CRF on top).
     *
     * @param sequence  array of input vectors
     * @return          hidden states at each time step [seqLen × hiddenSize]
     */
    public double[][] forwardAllStates(double[][] sequence) {
        double[][] states = new double[sequence.length][hiddenSize];
        double[] h = new double[hiddenSize];
        for (int t = 0; t < sequence.length; t++) {
            h = step(sequence[t], h);
            states[t] = h.clone();
        }
        return states;
    }

    // ── Bidirectional GRU ─────────────────────────────────────────────────────
    /**
     * Concept: Bidirectional RNN.
     *
     * BiGRU processes the sequence in both directions and concatenates outputs.
     * The forward pass captures left-to-right context; the backward pass captures
     * right-to-left context. Useful for classification tasks (not generation).
     *
     *   yₜ = [hₜ→ ; hₜ←]   (concat forward and backward hidden states)
     *
     * @param sequence  input sequence [seqLen × inputSize]
     * @param backward  a second GRU instance for the backward direction
     * @return          concatenated final hidden state [2 × hiddenSize]
     */
    public static double[] bidirectional(double[][] sequence, GRU forward, GRU backward) {
        double[] hForward  = forward.forward(sequence);

        // Reverse sequence for backward pass
        double[][] reversed = new double[sequence.length][];
        for (int i = 0; i < sequence.length; i++) {
            reversed[i] = sequence[sequence.length - 1 - i];
        }
        double[] hBackward = backward.forward(reversed);

        // Concatenate [hForward, hBackward]
        return concat(hForward, hBackward);
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private double[][] initMatrix(int rows, int cols) {
        double[][] m = new double[rows][cols];
        double scale = Math.sqrt(1.0 / cols);   // Xavier uniform approx
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

    // ── Accessors for inspection / debugging ──────────────────────────────────

    public int getHiddenSize() { return hiddenSize; }
    public int getInputSize()  { return inputSize;  }

    /** Total trainable parameters: 3 × hiddenSize × (hiddenSize + inputSize + 1) */
    public int paramCount() {
        return 3 * hiddenSize * (hiddenSize + inputSize + 1);
    }
}
