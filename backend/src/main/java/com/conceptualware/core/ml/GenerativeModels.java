package com.conceptualware.core.ml;

import java.util.*;

public class GenerativeModels {

    public static class Autoencoder {
        private final int inputDim;
        private final int latentDim;
        private final double learningRate;

        private final double[][] We1;
        private final double[]   be1;
        private final double[][] We2;
        private final double[]   be2;

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

        public double[] encode(double[] x) {
            double[] h = relu(addBias(matVec(We1, x), be1));
            return relu(addBias(matVec(We2, h), be2));
        }

        public double[] decode(double[] z) {
            double[] h = relu(addBias(matVec(Wd1, z), bd1));
            return sigmoid(addBias(matVec(Wd2, h), bd2));
        }

        public double[] forward(double[] x) {
            return decode(encode(x));
        }

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

    public static class VAE {
        private final int inputDim;
        private final int latentDim;

        private final double[][] Wmu;
        private final double[]   bMu;
        private final double[][] WlogVar;
        private final double[]   bLogVar;
        private final double[][] Wenc;
        private final double[]   bEnc;

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

        public double[][] encode(double[] x) {
            double[] h  = relu(addBias(matVec(Wenc, x), bEnc));
            double[] mu     = addBias(matVec(Wmu, h), bMu);
            double[] logVar = addBias(matVec(WlogVar, h), bLogVar);
            return new double[][]{mu, logVar};
        }

        public double[] reparameterize(double[] mu, double[] logVar) {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) {
                double sigma = Math.exp(0.5 * logVar[i]);
                z[i] = mu[i] + sigma * rng.nextGaussian();
            }
            return z;
        }

        public double[] decode(double[] z) {
            double[] h = relu(addBias(matVec(Wdec1, z), bDec1));
            return sigmoid(addBias(matVec(Wdec2, h), bDec2));
        }

        public double[][] forward(double[] x) {
            double[][] params = encode(x);
            double[] mu     = params[0];
            double[] logVar = params[1];
            double[] z      = reparameterize(mu, logVar);
            double[] xHat   = decode(z);
            return new double[][]{xHat, mu, logVar};
        }

        public double loss(double[] x, double[] xHat, double[] mu, double[] logVar) {
            double recon = 0;
            for (int i = 0; i < x.length; i++) {
                double d = x[i] - xHat[i];
                recon += d * d;
            }
            recon /= x.length;

            double kl = 0;
            for (int i = 0; i < latentDim; i++) {
                kl += -0.5 * (1 + logVar[i] - mu[i] * mu[i] - Math.exp(logVar[i]));
            }
            kl /= latentDim;

            return recon + kl;
        }

        public double[] generate() {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) z[i] = rng.nextGaussian();
            return decode(z);
        }

    }

    public static class GAN {
        private final int latentDim;
        private final int outputDim;
        private final double lr;
        private final Random rng;

        private final double[][] Wg1, Wg2;
        private final double[]   bg1, bg2;

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

        public double[] generate(double[] z) {
            double[] h = relu(addBias(matVec(Wg1, z), bg1));
            return tanh(addBias(matVec(Wg2, h), bg2));
        }

        public double discriminate(double[] x) {
            double[] h = relu(addBias(matVec(Wd1, x), bd1));
            return sigmoid1d(matVec(Wd2, h)[0] + bd2[0]);
        }

        public double[] sampleFake() {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) z[i] = rng.nextGaussian();
            return generate(z);
        }

        public double discriminatorLoss(double dReal, double dFake) {
            return -(Math.log(dReal + 1e-8) + Math.log(1 - dFake + 1e-8));
        }

        public double generatorLoss(double dFake) {
            return -Math.log(dFake + 1e-8);
        }

        public double[] sampleNoise() {
            double[] z = new double[latentDim];
            for (int i = 0; i < latentDim; i++) z[i] = rng.nextGaussian();
            return z;
        }

        private static double sigmoid1d(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
    }

    public static class DDPM {
        private final int timesteps;
        private final double[] betas;
        private final double[] alphas;
        private final double[] alphasCumprod;
        private final Random rng;
        private final int dim;

        public DDPM(int timesteps, int dim, double betaStart, double betaEnd, long seed) {
            this.timesteps = timesteps;
            this.dim       = dim;
            this.rng       = new Random(seed);

            betas         = new double[timesteps];
            alphas        = new double[timesteps];
            alphasCumprod = new double[timesteps];

            for (int t = 0; t < timesteps; t++) {
                betas[t]   = betaStart + (betaEnd - betaStart) * t / (timesteps - 1);
                alphas[t]  = 1.0 - betas[t];
            }

            double cumProd = 1.0;
            for (int t = 0; t < timesteps; t++) {
                cumProd *= alphas[t];
                alphasCumprod[t] = cumProd;
            }
        }

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

        public double[] reverseStep(double[] xt, double[] epsTheta, int t) {
            double alpha     = alphas[t];
            double alphaBar  = alphasCumprod[t];
            double beta      = betas[t];
            double sigmaT    = Math.sqrt(beta);

            double[] xPrev = new double[dim];
            double[] z     = t > 0 ? sampleNoise() : new double[dim];

            for (int i = 0; i < dim; i++) {
                double mu = (1.0 / Math.sqrt(alpha)) *
                    (xt[i] - beta / Math.sqrt(1.0 - alphaBar) * epsTheta[i]);
                xPrev[i] = mu + sigmaT * z[i];
            }
            return xPrev;
        }

        public double[] sample(Denoiser denoiser) {
            double[] x = sampleNoise();
            for (int t = timesteps - 1; t >= 0; t--) {
                double[] epsTheta = denoiser.predict(x, t);
                x = reverseStep(x, epsTheta, t);
            }
            return x;
        }

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

    @FunctionalInterface
    public interface Denoiser {
        double[] predict(double[] xt, int t);
    }

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
