package com.conceptualware.core.ml;

import java.util.*;

/**
 * Concept #30 — Neural Networks & Deep Learning:
 *
 *   MULTI-LAYER PERCEPTRON (MLP) — fully connected feedforward network.
 *
 *   Architecture:  Input → [Hidden₁ → Hidden₂ → ... → Hiddenₙ] → Output
 *   Each layer:    z = Wᵀx + b    (linear transform)
 *                  a = f(z)        (activation function)
 *
 *   ACTIVATION FUNCTIONS:
 *     ReLU:    f(z) = max(0, z)           f'(z) = 1 if z>0, else 0
 *     Sigmoid: f(z) = 1/(1+e⁻ᶻ)          f'(z) = f(z)(1-f(z))
 *     Tanh:    f(z) = (eᶻ-e⁻ᶻ)/(eᶻ+e⁻ᶻ)  f'(z) = 1 - f(z)²
 *     Softmax: fᵢ = e^zᵢ / Σe^zⱼ         (multi-class output)
 *     LeakyReLU: f(z) = max(0.01z, z)    avoids dying ReLU
 *     GELU:    f(z) ≈ z·Φ(z)             used in BERT/GPT
 *
 *   BACKPROPAGATION (reverse-mode auto-differentiation):
 *     Forward pass:  compute z, a layer by layer
 *     Output delta:  δ_L = (aL - y) ⊙ f'(z_L)    (MSE loss)
 *     Hidden delta:  δₗ = (Wₗ₊₁ᵀ δₗ₊₁) ⊙ f'(zₗ)   (chain rule)
 *     Weight grad:   ∂L/∂Wₗ = δₗ aₗ₋₁ᵀ
 *     Bias grad:     ∂L/∂bₗ = δₗ
 *
 *   OPTIMIZERS:
 *     SGD:     w := w - α·g
 *     Momentum: v := βv + g;  w := w - α·v
 *     Adam:    m := β₁m + (1-β₁)g;  v := β₂v + (1-β₂)g²;  w := w - α·m̂/√(v̂+ε)
 *
 *   REGULARIZATION:
 *     Dropout: randomly zero-out neurons during training (reduces co-adaptation)
 *     BatchNorm: normalize layer inputs (stabilizes training, allows higher lr)
 *     L2 weight decay: add λ||w||² to loss
 */
public class NeuralNetwork {

    // ── Activation Functions ──────────────────────────────────────────────────

    public enum Activation {
        RELU, SIGMOID, TANH, LEAKY_RELU, LINEAR;

        public double[] apply(double[] z) {
            double[] a = new double[z.length];
            for (int i = 0; i < z.length; i++) a[i] = apply(z[i]);
            return a;
        }

        public double apply(double z) {
            return switch (this) {
                case RELU       -> Math.max(0, z);
                case SIGMOID    -> 1.0 / (1 + Math.exp(-z));
                case TANH       -> Math.tanh(z);
                case LEAKY_RELU -> z > 0 ? z : 0.01 * z;
                case LINEAR     -> z;
            };
        }

        public double derivative(double a) {
            return switch (this) {
                case RELU       -> a > 0 ? 1 : 0;
                case SIGMOID    -> a * (1 - a);
                case TANH       -> 1 - a * a;
                case LEAKY_RELU -> a > 0 ? 1 : 0.01;
                case LINEAR     -> 1;
            };
        }
    }

    // ── Layer ─────────────────────────────────────────────────────────────────

    private static class Layer {
        double[][] W;   // weights [outputSize × inputSize]
        double[]   b;   // biases  [outputSize]
        double[]   z;   // pre-activation
        double[]   a;   // post-activation
        double[]   delta; // error signal (backprop)
        final Activation activation;

        // Adam optimizer state
        double[][] mW, vW; // first/second moment for weights
        double[]   mb, vb; // first/second moment for biases
        int adamT = 0;

        Layer(int inputSize, int outputSize, Activation activation, Random rng) {
            this.activation = activation;
            W = new double[outputSize][inputSize];
            b = new double[outputSize];
            // He initialization for ReLU, Xavier for sigmoid/tanh
            double scale = activation == Activation.RELU ? Math.sqrt(2.0 / inputSize) : Math.sqrt(1.0 / inputSize);
            for (int i = 0; i < outputSize; i++)
                for (int j = 0; j < inputSize; j++) W[i][j] = rng.nextGaussian() * scale;

            mW = new double[outputSize][inputSize]; vW = new double[outputSize][inputSize];
            mb = new double[outputSize];            vb = new double[outputSize];
        }

        double[] forward(double[] input) {
            z = new double[W.length];
            for (int i = 0; i < W.length; i++) {
                z[i] = b[i];
                for (int j = 0; j < input.length; j++) z[i] += W[i][j] * input[j];
            }
            a = activation.apply(z);
            return a;
        }
    }

    // ── MLP Network ───────────────────────────────────────────────────────────

    private final List<Layer> layers = new ArrayList<>();
    private final double learningRate;
    private final double beta1 = 0.9, beta2 = 0.999, epsilon = 1e-8;

    public NeuralNetwork(int[] sizes, Activation[] activations, double learningRate, long seed) {
        Random rng = new Random(seed);
        this.learningRate = learningRate;
        for (int i = 1; i < sizes.length; i++)
            layers.add(new Layer(sizes[i-1], sizes[i], activations[i-1], rng));
    }

    // ── Forward Pass ─────────────────────────────────────────────────────────

    public double[] forward(double[] input) {
        double[] current = input;
        for (var layer : layers) current = layer.forward(current);
        return current;
    }

    /** Softmax for multi-class output. */
    public double[] softmax(double[] z) {
        double max = Arrays.stream(z).max().orElse(0); // numerical stability
        double[] exp = Arrays.stream(z).map(v -> Math.exp(v - max)).toArray();
        double sum = Arrays.stream(exp).sum();
        for (int i = 0; i < exp.length; i++) exp[i] /= sum;
        return exp;
    }

    // ── Backpropagation ───────────────────────────────────────────────────────

    /**
     * Backpropagation via chain rule.
     *
     * Given:
     *   loss = MSE or BCE
     *   output = forward(x)
     *
     * Compute gradient of loss w.r.t. every weight and bias,
     * then update using Adam optimizer.
     */
    public double trainStep(double[] input, double[] target) {
        // Forward pass
        double[] output = forward(input);

        // Compute output delta (MSE gradient: dL/da = a - y)
        Layer outLayer = layers.get(layers.size() - 1);
        outLayer.delta = new double[output.length];
        double loss = 0;
        for (int i = 0; i < output.length; i++) {
            double err = output[i] - target[i];
            loss += err * err;
            outLayer.delta[i] = err * outLayer.activation.derivative(outLayer.a[i]);
        }
        loss /= output.length;

        // Backpropagate through hidden layers
        for (int l = layers.size() - 2; l >= 0; l--) {
            Layer curr = layers.get(l);
            Layer next = layers.get(l + 1);
            curr.delta = new double[curr.a.length];
            for (int j = 0; j < curr.a.length; j++) {
                for (int i = 0; i < next.delta.length; i++) curr.delta[j] += next.W[i][j] * next.delta[i];
                curr.delta[j] *= curr.activation.derivative(curr.a[j]);
            }
        }

        // Update weights using Adam
        double[] prevA = input;
        for (var layer : layers) {
            layer.adamT++;
            double lr_t = learningRate * Math.sqrt(1 - Math.pow(beta2, layer.adamT)) / (1 - Math.pow(beta1, layer.adamT));
            for (int i = 0; i < layer.W.length; i++) {
                for (int j = 0; j < layer.W[i].length; j++) {
                    double g = layer.delta[i] * prevA[j];
                    layer.mW[i][j] = beta1 * layer.mW[i][j] + (1-beta1) * g;
                    layer.vW[i][j] = beta2 * layer.vW[i][j] + (1-beta2) * g * g;
                    layer.W[i][j] -= lr_t * layer.mW[i][j] / (Math.sqrt(layer.vW[i][j]) + epsilon);
                }
                double gb = layer.delta[i];
                layer.mb[i] = beta1 * layer.mb[i] + (1-beta1) * gb;
                layer.vb[i] = beta2 * layer.vb[i] + (1-beta2) * gb * gb;
                layer.b[i] -= lr_t * layer.mb[i] / (Math.sqrt(layer.vb[i]) + epsilon);
            }
            prevA = layer.a;
        }
        return loss;
    }

    public List<Double> train(double[][] X, double[][] y, int epochs) {
        List<Double> lossHistory = new ArrayList<>();
        Random rng = new Random(42);
        for (int e = 0; e < epochs; e++) {
            double totalLoss = 0;
            // SGD with shuffle
            Integer[] indices = new Integer[X.length];
            for (int i = 0; i < X.length; i++) indices[i] = i;
            Arrays.sort(indices, (a, b) -> rng.nextInt(3) - 1);
            for (int idx : indices) totalLoss += trainStep(X[idx], y[idx]);
            lossHistory.add(totalLoss / X.length);
        }
        return lossHistory;
    }

    // ── CNN BUILDING BLOCKS ───────────────────────────────────────────────────
    /**
     * Concept #30 — Convolutional Neural Networks (CNN):
     *
     *   CNNs exploit spatial locality by applying learned filters (kernels).
     *   Key layers:
     *     Convolution:  output[i,j] = Σ kernel[m,n] * input[i+m, j+n]
     *     Max Pooling:  output[i,j] = max(input[2i..2i+s, 2j..2j+s])  (stride s)
     *     Flatten:      reshape 2D feature map to 1D vector
     *
     *   Parameter sharing: same kernel applied everywhere → fewer params than FC.
     *   Translation invariance: max pooling ignores exact position of features.
     */
    public static class Conv2D {
        private final double[][] kernel; // [kH × kW]
        private final double bias;

        public Conv2D(double[][] kernel, double bias) { this.kernel = kernel; this.bias = bias; }

        /** 2D cross-correlation (conv without kernel flip). Padding: 'valid' (no padding). */
        public double[][] forward(double[][] input) {
            int inH = input.length, inW = input[0].length;
            int kH = kernel.length, kW = kernel[0].length;
            int outH = inH - kH + 1, outW = inW - kW + 1;
            double[][] output = new double[outH][outW];

            for (int i = 0; i < outH; i++)
                for (int j = 0; j < outW; j++) {
                    double sum = bias;
                    for (int m = 0; m < kH; m++) for (int n = 0; n < kW; n++) sum += kernel[m][n] * input[i+m][j+n];
                    output[i][j] = Math.max(0, sum); // ReLU activation inline
                }
            return output;
        }

        /** Max pooling with given stride and pool size. */
        public static double[][] maxPool(double[][] input, int poolSize, int stride) {
            int outH = (input.length - poolSize) / stride + 1;
            int outW = (input[0].length - poolSize) / stride + 1;
            double[][] output = new double[outH][outW];
            for (int i = 0; i < outH; i++)
                for (int j = 0; j < outW; j++) {
                    double max = Double.NEGATIVE_INFINITY;
                    for (int m = 0; m < poolSize; m++) for (int n = 0; n < poolSize; n++)
                        max = Math.max(max, input[i*stride+m][j*stride+n]);
                    output[i][j] = max;
                }
            return output;
        }

        /** Flatten 2D feature map to 1D. */
        public static double[] flatten(double[][] featureMap) {
            int h = featureMap.length, w = featureMap[0].length;
            double[] flat = new double[h * w];
            for (int i = 0; i < h; i++) for (int j = 0; j < w; j++) flat[i*w+j] = featureMap[i][j];
            return flat;
        }
    }

    // ── RNN / LSTM / GRU ─────────────────────────────────────────────────────
    /**
     * Concept #30 — Recurrent Neural Networks:
     *
     *   RNN:  hₜ = tanh(Wₓxₜ + Wₕhₜ₋₁ + b)
     *     Problem: vanishing/exploding gradient over long sequences.
     *
     *   LSTM (Long Short-Term Memory) — solves vanishing gradient via gates:
     *     Forget gate: fₜ = σ(Wf[hₜ₋₁,xₜ] + bf)        — what to forget from cell
     *     Input gate:  iₜ = σ(Wi[hₜ₋₁,xₜ] + bi)        — what new info to store
     *     Candidate:   c̃ₜ = tanh(Wc[hₜ₋₁,xₜ] + bc)
     *     Cell update: cₜ = fₜ⊙cₜ₋₁ + iₜ⊙c̃ₜ
     *     Output gate: oₜ = σ(Wo[hₜ₋₁,xₜ] + bo)
     *     Hidden:      hₜ = oₜ⊙tanh(cₜ)
     *
     *   GRU (Gated Recurrent Unit) — simpler, fewer parameters:
     *     Reset gate:  rₜ = σ(Wr[hₜ₋₁,xₜ])
     *     Update gate: zₜ = σ(Wz[hₜ₋₁,xₜ])
     *     Candidate:   h̃ₜ = tanh(Wh[rₜ⊙hₜ₋₁,xₜ])
     *     Hidden:      hₜ = (1-zₜ)⊙hₜ₋₁ + zₜ⊙h̃ₜ
     */
    public static class LSTM {
        // All weight matrices [hiddenSize × (hiddenSize + inputSize)]
        private final double[][] Wf, Wi, Wc, Wo;
        private final double[]   bf, bi, bc, bo;
        private final int hiddenSize;
        private final Random rng;

        public LSTM(int inputSize, int hiddenSize, long seed) {
            this.hiddenSize = hiddenSize;
            this.rng = new Random(seed);
            int sz = hiddenSize + inputSize;
            Wf = randomMatrix(hiddenSize, sz); bf = new double[hiddenSize];
            Wi = randomMatrix(hiddenSize, sz); bi = new double[hiddenSize];
            Wc = randomMatrix(hiddenSize, sz); bc = new double[hiddenSize];
            Wo = randomMatrix(hiddenSize, sz); bo = new double[hiddenSize];
        }

        /** Run one LSTM step. Returns [h, c] pair. */
        public double[][] step(double[] x, double[] h, double[] c) {
            double[] concat = concat(h, x);
            double[] ft = sigmoid(addBias(matVec(Wf, concat), bf));
            double[] it = sigmoid(addBias(matVec(Wi, concat), bi));
            double[] ct_tilde = tanh(addBias(matVec(Wc, concat), bc));
            double[] ot = sigmoid(addBias(matVec(Wo, concat), bo));

            double[] new_c = new double[hiddenSize];
            double[] new_h = new double[hiddenSize];
            for (int i = 0; i < hiddenSize; i++) {
                new_c[i] = ft[i] * c[i] + it[i] * ct_tilde[i];
                new_h[i] = ot[i] * Math.tanh(new_c[i]);
            }
            return new double[][]{new_h, new_c};
        }

        /** Process a sequence of inputs, return final hidden state. */
        public double[] forward(double[][] sequence) {
            double[] h = new double[hiddenSize];
            double[] c = new double[hiddenSize];
            for (double[] x : sequence) {
                double[][] hc = step(x, h, c);
                h = hc[0]; c = hc[1];
            }
            return h;
        }

        private double[][] randomMatrix(int rows, int cols) {
            double[][] m = new double[rows][cols];
            double scale = Math.sqrt(1.0 / cols);
            for (int i=0;i<rows;i++) for (int j=0;j<cols;j++) m[i][j] = rng.nextGaussian() * scale;
            return m;
        }

        private double[] matVec(double[][] W, double[] x) {
            double[] out = new double[W.length];
            for (int i=0;i<W.length;i++) for (int j=0;j<x.length;j++) out[i] += W[i][j] * x[j];
            return out;
        }

        private double[] concat(double[] a, double[] b) {
            double[] c = new double[a.length + b.length];
            System.arraycopy(a, 0, c, 0, a.length);
            System.arraycopy(b, 0, c, a.length, b.length);
            return c;
        }

        private double[] sigmoid(double[] v) { double[] r = new double[v.length]; for (int i=0;i<v.length;i++) r[i]=1/(1+Math.exp(-v[i])); return r; }
        private double[] tanh(double[] v)    { double[] r = new double[v.length]; for (int i=0;i<v.length;i++) r[i]=Math.tanh(v[i]); return r; }
        private double[] addBias(double[] v, double[] b) { double[] r = new double[v.length]; for (int i=0;i<v.length;i++) r[i]=v[i]+b[i]; return r; }
    }
}
