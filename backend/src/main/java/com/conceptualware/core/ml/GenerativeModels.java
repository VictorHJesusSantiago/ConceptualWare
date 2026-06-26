package com.conceptualware.core.ml;

import java.util.*;

/**
 * Concept #30 — Generative Models
 *
 * Generative models learn the data distribution p(x) and can sample new data.
 * Contrast with discriminative models that learn p(y|x).
 *
 * Models implemented:
 *   1. Autoencoder (AE) — encoder-decoder with bottleneck
 *   2. Variational Autoencoder (VAE) — latent space with learned distribution
 *   3. Generative Adversarial Network (GAN) — min-max game between generator and discriminator
 *   4. Denoising Diffusion Probabilistic Model (DDPM) — forward/reverse Markov chain
 */
public class GenerativeModels {

    // ── Autoencoder ────────────────────────────────────────────────────────────
    /**
     * Autoencoder: encoder compresses input → latent code z, decoder reconstructs.
     *
     *   x → Encoder → z → Decoder → x̂
     *
     * Loss: reconstruction loss L = ‖x − x̂‖²  (MSE)
     *
     * Applications:
     *   - Dimensionality reduction (like PCA but non-linear)
     *   - Anomaly detection (high reconstruction error = anomaly)
     *   - Denoising (train with noisy input, clean target)
     *   - Representation learning
     *
     * Problem: latent space is not regularized — no guarantee of smooth interpolation.
     * VAE fixes this by constraining the latent distribution.
     */
    public static class Autoencoder {
        private final int inputDim;
        private final int latentDim;
        private final double learningRate;

        // Encoder: inputDim → hiddenDim → latentDim
        private final double[][] We1;  // encoder layer 1
        private final double[]   be1;
        private final double[][] We2;  // encoder layer 2 (to latent)
        private final double[]   be2;

        // Decoder: latentDim → hiddenDim → inputDim
        private final double[][] Wd1;
        private final double[]   bd1;
        private final double[][] Wd2;
        private final double[]   bd2;

        private final int hiddenDim;

        public Autoencoder(int inputDim, int hiddenDim, int latentDim, double lr, long seed) {
            this.inputDim    = inputDim;
            this.hiddenDim   = hiddenDim;
            this.latentDim   = latentDim;
            this.learningRate = lr;

            Random rng = new Random(seed);
            double s1 = Math.sqrt(2.0 / inputDim);
            double s2 = Math.sqrt(2.0 / hiddenDim);
            double s3 = Math.sqrt(2.0 / latentDim);

            We1 = randn(hiddenDim, inputDim, rng, s1);  be1 = new double[hiddenDim];
            We2 = randn(latentDim, hiddenDim, rng, s2); be2 = new double[latentDim];
            Wd1 = randn(hiddenDim, latentDim, rng, s3); bd1 = new double[hiddenDim];
            Wd2 = randn(inputDim, hiddenDim, rng, s2);  bd2 = new double[inputDim];
        }

        /** Encode input to latent representation (deterministic). */
        public double[] encode(double[] x) {
            double[] h = relu(addBias(matVec(We1, x), be1));
            return relu(addBias(matVec(We2, h), be2));   // latent code z
        }

        /** Decode latent code to reconstruction. */
        public double[] decode(double[] z) {
            double[] h = relu(addBias(matVec(Wd1, z), bd1));
            return sigmoid(addBias(matVec(Wd2, h), bd2));  // sigmoid for [0,1] output
        }

        /** Forward pass: encode then decode. */
        public double[] forward(double[] x) {
            return decode(encode(x));
        }

        /** MSE reconstruction loss. */
        public double reconstructionLoss(double[] x) {
            double[] xHat = forward(x);
            double loss = 0;
            for (int i = 0; i < x.length; i++) {
                double diff = x[i] - xHat[i];
                loss += diff * diff;
            }
            return loss / x.length;
        }
    }

    // ── Variational Autoencoder (VAE) ──────────────────────────────────────────
    /**
     * VAE (Kingma & Welling, 2013):
     *
     * Instead of encoding to a fixed point z, encoder outputs parameters
     * of a distribution: μ(x) and σ(x).
     *
     * Latent variable: z ~ N(μ(x), σ²(x))
     *
     * Reparameterization trick (enables backprop through sampling):
     *   z = μ + σ ⊙ ε,  ε ~ N(0, I)
     *   ∂z/∂μ = 1,  ∂z/∂σ = ε  — differentiable w.r.t. parameters
     *
     * ELBO (Evidence Lower BOund) — VAE objective:
     *   L = 𝔼_q[log p(x|z)] − KL(q(z|x) ‖ p(z))
     *       \_____reconstruction_____/   \__regularization__/
     *
     * KL term (closed form for Gaussian q and N(0,I) prior):
     *   KL = −½ Σⱼ (1 + log σⱼ² − μⱼ² − σⱼ²)
     *
     * KL forces latent space to be smooth and regularized:
     *   - Nearby points in latent space → similar decodings
     *   - Can generate new samples by z ~ N(0, I) → decoder
     */
    public static class VAE {
        private final int inputDim;
        private final int latentDim;

        // Encoder outputs μ and log(σ²) [both size latentDim]
        private final double[][] Wmu;      // for mean
        private final double[]   bMu;
        private final double[][] WlogVar;  // for log variance
        private final double[]   bLogVar;
        private final double[][] Wenc;     // shared encoder layer
        private final double[]   bEnc;

        // Decoder
        private final double[][] Wdec1;
        private final double[]   bDec1;
        private final double[][] Wdec2;
        private final double[]   bDec2;

        private final int hiddenDim;
        private final Random rng;

        public VAE(int inputDim, int hiddenDim, int latentDim, long seed) {
            this.inputDim  = inputDim;
            this.hiddenDim = hiddenDim;
            this.latentDim = latentDim;
            this.rng       = new Random(seed);

            double s1 = Math.sqrt(2.0 / inputDim);
            double s2 = Math.sqrt(2.0 / hiddenDim);
            double s3 = Math.sqrt(2.0 / latentDim);

            Wenc    = randn(hiddenDim, inputDim,  rng, s1); bEnc    = new double[hiddenDim];
            Wmu     = randn(latentDim, hiddenDim, rng, s2); bMu     = new double[latentDim];
            WlogVar = randn(latentDim, hiddenDim, rng, s2); bLogVar = new double[latentDim];
            Wdec1   = randn(hiddenDim, latentDim, rng, s3); bDec1   = new double[hiddenDim];
            Wdec2   = randn(inputDim,  hiddenDim, rng, s2); bDec2   = new double[inputDim];
        }

        /**
         * Encode to distribution parameters (μ, log σ²).
         * @return double[2][latentDim] — [0]=μ, [1]=logVar
         */
        public double[][] encode(double[] x) {
            double[] h  = relu(addBias(matVec(Wenc, x), bEnc));
            double[] mu     = addBias(matVec(Wmu, h), bMu);
            double[] logVar = addBias(matVec(WlogVar, h), bLogVar);
            return new double[][]{mu, logVar};
        }

        /**
         * Reparameterization: z = μ + σ⊙ε, ε~N(0,I)
         * This is the "reparameterization trick" — allows gradients to flow through z.
         */
        public double[] reparameterize(double[] mu, double[] logVar) {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) {
                double sigma = Math.exp(0.5 * logVar[i]);  // σ = exp(logVar/2)
                z[i] = mu[i] + sigma * rng.nextGaussian(); // z = μ + σ·ε
            }
            return z;
        }

        /** Decode latent z to reconstructed input. */
        public double[] decode(double[] z) {
            double[] h = relu(addBias(matVec(Wdec1, z), bDec1));
            return sigmoid(addBias(matVec(Wdec2, h), bDec2));
        }

        /**
         * Forward pass: returns [xHat, mu, logVar].
         * Uses reparameterization trick for differentiability.
         */
        public double[][] forward(double[] x) {
            double[][] params = encode(x);
            double[] mu     = params[0];
            double[] logVar = params[1];
            double[] z      = reparameterize(mu, logVar);
            double[] xHat   = decode(z);
            return new double[][]{xHat, mu, logVar};
        }

        /**
         * VAE loss = reconstruction loss + KL divergence:
         *   L = MSE(x, x̂) + β · KL(N(μ,σ²) ‖ N(0,I))
         *   KL = −½ Σⱼ (1 + logVar_j − μⱼ² − exp(logVar_j))
         *
         * β-VAE: β > 1 encourages more disentangled latent representations.
         */
        public double loss(double[] x, double[] xHat, double[] mu, double[] logVar) {
            // Reconstruction: MSE
            double recon = 0;
            for (int i = 0; i < x.length; i++) {
                double d = x[i] - xHat[i];
                recon += d * d;
            }
            recon /= x.length;

            // KL divergence (closed form)
            double kl = 0;
            for (int i = 0; i < latentDim; i++) {
                kl += -0.5 * (1 + logVar[i] - mu[i] * mu[i] - Math.exp(logVar[i]));
            }
            kl /= latentDim;

            return recon + kl;
        }

        /** Generate a new sample by sampling from prior z~N(0,I) then decoding. */
        public double[] generate() {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) z[i] = rng.nextGaussian();
            return decode(z);
        }

    }

    // ── GAN ────────────────────────────────────────────────────────────────────
    /**
     * Generative Adversarial Network (GAN — Goodfellow et al., 2014):
     *
     * Two networks in a zero-sum minimax game:
     *   Generator G:       noise z ~ N(0,I) → fake samples G(z)
     *   Discriminator D:   real or fake? D(x) ∈ [0,1]
     *
     * Objective:
     *   min_G max_D V(D,G) = 𝔼_x[log D(x)] + 𝔼_z[log(1 − D(G(z)))]
     *
     * Training alternates:
     *   Step 1 — Update D (maximize): real → D→1, fake → D→0
     *     L_D = −[log D(x) + log(1 − D(G(z)))]
     *   Step 2 — Update G (maximize D(G(z))):
     *     L_G = −log D(G(z))   (non-saturating loss — stronger gradient early)
     *
     * Problems with vanilla GAN:
     *   - Mode collapse: G generates only a few modes (ignores diversity)
     *   - Training instability: D too strong → G gets no gradient
     *   - Difficult to evaluate: no direct likelihood estimate
     *
     * Variants:
     *   DCGAN:   Deep Convolutional GAN — uses CNN architecture
     *   WGAN:    Wasserstein distance as loss — training stability
     *   StyleGAN: progressive growing + style injection — high-fidelity images
     *   cGAN:    Conditional GAN — condition on class label y
     */
    public static class GAN {
        private final int latentDim;
        private final int outputDim;
        private final double lr;
        private final Random rng;

        // Generator weights (z → output)
        private final double[][] Wg1, Wg2;
        private final double[]   bg1, bg2;

        // Discriminator weights (x → probability)
        private final double[][] Wd1, Wd2;
        private final double[]   bd1, bd2;

        public GAN(int latentDim, int hiddenDim, int outputDim, double lr, long seed) {
            this.latentDim = latentDim;
            this.outputDim = outputDim;
            this.lr        = lr;
            this.rng       = new Random(seed);

            double sg = Math.sqrt(2.0 / latentDim);
            double sd = Math.sqrt(2.0 / outputDim);

            Wg1 = randn(hiddenDim, latentDim, rng, sg);  bg1 = new double[hiddenDim];
            Wg2 = randn(outputDim, hiddenDim, rng, Math.sqrt(2.0/hiddenDim)); bg2 = new double[outputDim];
            Wd1 = randn(hiddenDim, outputDim, rng, sd);  bd1 = new double[hiddenDim];
            Wd2 = randn(1, hiddenDim, rng, Math.sqrt(2.0/hiddenDim));         bd2 = new double[1];
        }

        /** Generator: z → fake sample G(z) */
        public double[] generate(double[] z) {
            double[] h = relu(addBias(matVec(Wg1, z), bg1));
            return tanh(addBias(matVec(Wg2, h), bg2));  // tanh: output in (-1,1)
        }

        /** Discriminator: x → P(real) D(x) in [0,1] */
        public double discriminate(double[] x) {
            double[] h = relu(addBias(matVec(Wd1, x), bd1));
            return sigmoid1d(matVec(Wd2, h)[0] + bd2[0]);
        }

        /** Sample a new fake example from generator. */
        public double[] sampleFake() {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) z[i] = rng.nextGaussian();
            return generate(z);
        }

        /** Discriminator loss: binary cross-entropy on real + fake. */
        public double discriminatorLoss(double dReal, double dFake) {
            return -(Math.log(dReal + 1e-8) + Math.log(1 - dFake + 1e-8));
        }

        /** Generator loss (non-saturating): -log D(G(z)) */
        public double generatorLoss(double dFake) {
            return -Math.log(dFake + 1e-8);
        }

        /** Sample noise vector for generator input. */
        public double[] sampleNoise() {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) z[i] = rng.nextGaussian();
            return z;
        }

        private static double sigmoid1d(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
    }

    // ── Denoising Diffusion Probabilistic Model (DDPM) ────────────────────────
    /**
     * DDPM (Ho et al., 2020 — "Denoising Diffusion Probabilistic Models"):
     *
     * Diffusion models generate data by learning to REVERSE a gradual noising process.
     *
     * Forward process (fixed Markov chain, adds Gaussian noise):
     *   q(xₜ|xₜ₋₁) = N(xₜ; √(1−βₜ)xₜ₋₁, βₜI)
     *   q(xₜ|x₀) = N(xₜ; √ᾱₜ x₀, (1−ᾱₜ)I)   — nice closed form
     *
     *   where αₜ = 1 − βₜ,  ᾱₜ = Πₛ₌₁ᵗ αₛ
     *   Reparameterized: xₜ = √ᾱₜ x₀ + √(1−ᾱₜ) ε,   ε ~ N(0,I)
     *
     * Reverse process (learned denoiser):
     *   p_θ(xₜ₋₁|xₜ) = N(xₜ₋₁; μ_θ(xₜ,t), σₜ²I)
     *   μ_θ = (1/√αₜ) [xₜ − βₜ/√(1−ᾱₜ) ε_θ(xₜ,t)]
     *
     * Training objective (simplified):
     *   L = 𝔼_{x₀,ε,t} [‖ε − ε_θ(xₜ,t)‖²]
     *   Train a U-Net to predict the added noise ε from noisy input xₜ.
     *
     * Generation:
     *   xₜ ~ N(0,I)  →  xₜ₋₁ = μ_θ(xₜ,t) + σₜz  →  ... →  x₀
     *
     * Why diffusion models outperform GANs (ImageNet benchmarks, 2021+):
     *   - No mode collapse (no adversarial training instability)
     *   - Directly optimize a principled likelihood-based objective
     *   - Can do conditional generation, inpainting, super-resolution
     *
     * Extensions: DDIM (faster sampling), Stable Diffusion (latent diffusion), DALL-E 2.
     */
    public static class DDPM {
        private final int timesteps;        // T — total diffusion steps
        private final double[] betas;       // noise schedule β₁,...,βₜ
        private final double[] alphas;      // αₜ = 1 − βₜ
        private final double[] alphasCumprod; // ᾱₜ = Π αₛ
        private final Random rng;
        private final int dim;              // data dimensionality

        /**
         * @param timesteps  T (typically 1000 for high-quality generation)
         * @param dim        dimensionality of data x₀
         * @param betaStart  β₁ (small: 1e-4)
         * @param betaEnd    βₜ (larger: 0.02)
         */
        public DDPM(int timesteps, int dim, double betaStart, double betaEnd, long seed) {
            this.timesteps = timesteps;
            this.dim       = dim;
            this.rng       = new Random(seed);

            // Linear noise schedule: β increases linearly from betaStart to betaEnd
            betas         = new double[timesteps];
            alphas        = new double[timesteps];
            alphasCumprod = new double[timesteps];

            for (int t = 0; t < timesteps; t++) {
                betas[t]   = betaStart + (betaEnd - betaStart) * t / (timesteps - 1);
                alphas[t]  = 1.0 - betas[t];
            }

            // ᾱₜ = cumulative product
            double cumProd = 1.0;
            for (int t = 0; t < timesteps; t++) {
                cumProd *= alphas[t];
                alphasCumprod[t] = cumProd;
            }
        }

        /**
         * Forward process: add noise to x₀ to get xₜ in ONE step (closed form).
         *   xₜ = √ᾱₜ x₀ + √(1−ᾱₜ) ε,   ε ~ N(0,I)
         *
         * @param x0  clean data [dim]
         * @param t   timestep (0-indexed)
         * @return    [xₜ, ε] — noisy sample and the noise that was added
         */
        public double[][] forwardNoise(double[] x0, int t) {
            double sqrtAlphaBar    = Math.sqrt(alphasCumprod[t]);
            double sqrtOneMinusBar = Math.sqrt(1.0 - alphasCumprod[t]);

            double[] eps = sampleNoise();
            double[] xt  = new double[dim];
            for (int i = 0; i < dim; i++) {
                xt[i] = sqrtAlphaBar * x0[i] + sqrtOneMinusBar * eps[i];
            }
            return new double[][]{xt, eps};
        }

        /**
         * Reverse step: given denoised prediction of noise ε_θ(xₜ, t),
         * compute xₜ₋₁ using the reverse diffusion formula.
         *
         *   μ_θ = (1/√αₜ)(xₜ − βₜ/√(1−ᾱₜ) ε_θ)
         *   xₜ₋₁ ~ N(μ_θ, σₜ²I),  σₜ = √βₜ
         *
         * @param xt      noisy sample at timestep t
         * @param epsTheta  predicted noise from denoising model
         * @param t       current timestep (1-indexed here, so t>0)
         * @return xₜ₋₁
         */
        public double[] reverseStep(double[] xt, double[] epsTheta, int t) {
            double alpha     = alphas[t];
            double alphaBar  = alphasCumprod[t];
            double beta      = betas[t];
            double sigmaT    = Math.sqrt(beta);   // posterior std dev

            double[] xPrev = new double[dim];
            double[] z     = t > 0 ? sampleNoise() : new double[dim];   // no noise at t=0

            for (int i = 0; i < dim; i++) {
                double mu = (1.0 / Math.sqrt(alpha)) *
                    (xt[i] - beta / Math.sqrt(1.0 - alphaBar) * epsTheta[i]);
                xPrev[i] = mu + sigmaT * z[i];
            }
            return xPrev;
        }

        /**
         * Sampling: generate x₀ from random noise via iterative denoising.
         * Uses a trivial denoiser (identity — for structural demo only).
         * In practice, ε_θ is a trained U-Net.
         *
         * @param denoiser  function: (xₜ, t) → predicted noise ε_θ
         * @return  generated sample x₀
         */
        public double[] sample(Denoiser denoiser) {
            double[] x = sampleNoise();   // xT ~ N(0,I)
            for (int t = timesteps - 1; t >= 0; t--) {
                double[] epsTheta = denoiser.predict(x, t);
                x = reverseStep(x, epsTheta, t);
            }
            return x;
        }

        /** Cosine noise schedule (improved schedule from Nichol & Dhariwal, 2021). */
        public static double[] cosineSchedule(int T) {
            double[] betas = new double[T];
            double s = 0.008;
            for (int t = 0; t < T; t++) {
                double fT  = cosineAlpha(t, T, s);
                double fT1 = cosineAlpha(t + 1, T, s);
                betas[t]   = Math.min(1.0 - fT1 / fT, 0.999);
            }
            return betas;
        }

        private static double cosineAlpha(int t, int T, double s) {
            return Math.pow(Math.cos((t / (double) T + s) / (1 + s) * Math.PI / 2), 2);
        }

        private double[] sampleNoise() {
            double[] eps = new double[dim];
            for (int i = 0; i < dim; i++) eps[i] = rng.nextGaussian();
            return eps;
        }

        public int getTimesteps()          { return timesteps; }
        public double[] getAlphasCumprod() { return alphasCumprod.clone(); }
        public double getSqrtAlphaBar(int t) { return Math.sqrt(alphasCumprod[t]); }
        public double getSqrtOneMinusBar(int t) { return Math.sqrt(1 - alphasCumprod[t]); }
    }

    /** Denoising model interface (the neural network being trained). */
    @FunctionalInterface
    public interface Denoiser {
        double[] predict(double[] xt, int t);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static double[][] randn(int rows, int cols, Random rng, double scale) {
        double[][] m = new double[rows][cols];
        for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++)
            m[i][j] = rng.nextGaussian() * scale;
        return m;
    }

    private static double[] matVec(double[][] W, double[] x) {
        double[] out = new double[W.length];
        for (int i = 0; i < W.length; i++) for (int j = 0; j < x.length; j++)
            out[i] += W[i][j] * x[j];
        return out;
    }

    private static double[] addBias(double[] v, double[] b) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] + b[i];
        return out;
    }

    private static double[] relu(double[] v) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Math.max(0, v[i]);
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
}
