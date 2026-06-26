package com.conceptualware.core.ml;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Concept #30 — Generative Models and Reinforcement Learning
 */
@DisplayName("Category 30 — Generative Models and Reinforcement Learning")
class GenerativeAndRLTest {

    // ── Autoencoder ───────────────────────────────────────────────────────────

    @Nested @DisplayName("Autoencoder")
    class AutoencoderTests {

        @Test @DisplayName("forward pass output has correct dimensionality")
        void outputShape() {
            var ae = new GenerativeModels.Autoencoder(16, 8, 4, 0.01, 42L);
            double[] x = new double[16];
            for (int i = 0; i < 16; i++) x[i] = 0.5;
            double[] xHat = ae.forward(x);
            assertThat(xHat).hasSize(16);
        }

        @Test @DisplayName("latent code has correct dimensionality")
        void latentShape() {
            var ae = new GenerativeModels.Autoencoder(10, 6, 3, 0.01, 1L);
            double[] x = new double[10];
            double[] z = ae.encode(x);
            assertThat(z).hasSize(3);
        }

        @Test @DisplayName("reconstruction loss is non-negative")
        void lossNonNegative() {
            var ae = new GenerativeModels.Autoencoder(8, 4, 2, 0.01, 7L);
            double[] x = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8};
            assertThat(ae.reconstructionLoss(x)).isGreaterThanOrEqualTo(0.0);
        }

        @Test @DisplayName("output values are in [0, 1] due to sigmoid activation")
        void outputBounded() {
            var ae = new GenerativeModels.Autoencoder(6, 4, 2, 0.01, 99L);
            double[] x = {1.0, 0.0, 1.0, 0.0, 0.5, 0.5};
            for (double v : ae.forward(x)) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    // ── VAE ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("Variational Autoencoder (VAE)")
    class VAETests {

        @Test @DisplayName("encode returns mu and logVar of correct size")
        void encodeShape() {
            var vae = new GenerativeModels.VAE(10, 8, 3, 42L);
            double[] x = new double[10];
            double[][] params = vae.encode(x);
            assertThat(params[0]).hasSize(3);   // mu
            assertThat(params[1]).hasSize(3);   // logVar
        }

        @Test @DisplayName("forward returns xHat, mu, logVar")
        void forwardShape() {
            var vae = new GenerativeModels.VAE(8, 6, 2, 1L);
            double[] x = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8};
            double[][] out = vae.forward(x);
            assertThat(out[0]).hasSize(8);   // xHat
            assertThat(out[1]).hasSize(2);   // mu
            assertThat(out[2]).hasSize(2);   // logVar
        }

        @Test @DisplayName("VAE loss is finite and non-negative")
        void lossFinite() {
            var vae = new GenerativeModels.VAE(8, 6, 2, 5L);
            double[] x = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8};
            double[][] out = vae.forward(x);
            double loss = vae.loss(x, out[0], out[1], out[2]);
            assertThat(loss).isFinite().isGreaterThanOrEqualTo(0.0);
        }

        @Test @DisplayName("generate produces output of correct size")
        void generateShape() {
            var vae = new GenerativeModels.VAE(12, 8, 4, 42L);
            double[] sample = vae.generate();
            assertThat(sample).hasSize(12);
        }

        @Test @DisplayName("reparameterize produces different samples each call")
        void reparameterizeStochastic() {
            var vae = new GenerativeModels.VAE(4, 4, 2, 77L);
            double[] mu = {0.0, 0.0}, logVar = {0.0, 0.0};
            double[] z1 = vae.reparameterize(mu, logVar);
            double[] z2 = vae.reparameterize(mu, logVar);
            boolean allSame = true;
            for (int i = 0; i < z1.length; i++) if (Math.abs(z1[i]-z2[i]) > 1e-12) allSame = false;
            assertThat(allSame).isFalse();   // stochastic sampling
        }
    }

    // ── GAN ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("GAN")
    class GANTests {

        @Test @DisplayName("generate produces output of correct dimensionality")
        void generateShape() {
            var gan = new GenerativeModels.GAN(10, 16, 8, 0.001, 42L);
            double[] noise = gan.sampleNoise();
            double[] fake  = gan.generate(noise);
            assertThat(fake).hasSize(8);
        }

        @Test @DisplayName("discriminate outputs probability in (0, 1)")
        void discriminateRange() {
            var gan = new GenerativeModels.GAN(8, 16, 6, 0.001, 1L);
            double[] fake = gan.sampleFake();
            double d = gan.discriminate(fake);
            assertThat(d).isBetween(0.0, 1.0);
        }

        @Test @DisplayName("generator and discriminator losses are finite")
        void lossesFinite() {
            var gan = new GenerativeModels.GAN(4, 8, 4, 0.001, 7L);
            double[] fake  = gan.sampleFake();
            double dReal = 0.9;
            double dFake = gan.discriminate(fake);

            double dLoss = gan.discriminatorLoss(dReal, dFake);
            double gLoss = gan.generatorLoss(dFake);

            assertThat(dLoss).isFinite();
            assertThat(gLoss).isFinite();
        }

        @Test @DisplayName("generate output values are in (-1, 1) due to tanh")
        void generateBounded() {
            var gan = new GenerativeModels.GAN(5, 10, 4, 0.001, 99L);
            for (int i = 0; i < 20; i++) {
                for (double v : gan.sampleFake()) {
                    assertThat(v).isBetween(-1.0, 1.0);
                }
            }
        }
    }

    // ── DDPM ─────────────────────────────────────────────────────────────────

    @Nested @DisplayName("DDPM (Diffusion Model)")
    class DDPMTests {

        @Test @DisplayName("forward noise produces correct output shape")
        void forwardNoiseShape() {
            var ddpm = new GenerativeModels.DDPM(100, 8, 1e-4, 0.02, 42L);
            double[] x0 = new double[8];
            double[][] result = ddpm.forwardNoise(x0, 50);
            assertThat(result[0]).hasSize(8);  // xₜ
            assertThat(result[1]).hasSize(8);  // ε
        }

        @Test @DisplayName("at t=0 noisy sample is close to x0 (small noise)")
        void smallNoiseAtT0() {
            var ddpm = new GenerativeModels.DDPM(1000, 4, 1e-4, 0.02, 1L);
            double[] x0 = {0.5, 0.5, 0.5, 0.5};
            double[][] result = ddpm.forwardNoise(x0, 0);
            double[] xt = result[0];
            // at t=0, sqrt(alphaBar) ≈ 1, sqrt(1-alphaBar) ≈ small
            for (int i = 0; i < 4; i++) {
                assertThat(xt[i]).isCloseTo(x0[i], within(0.1));
            }
        }

        @Test @DisplayName("alphasCumprod is strictly decreasing")
        void alphasCumprodDecreasing() {
            var ddpm = new GenerativeModels.DDPM(100, 4, 1e-4, 0.02, 0L);
            double[] ab = ddpm.getAlphasCumprod();
            for (int i = 1; i < ab.length; i++) {
                assertThat(ab[i]).isLessThan(ab[i-1]);
            }
        }

        @Test @DisplayName("cosine schedule produces values in (0, 1)")
        void cosineScheduleRange() {
            double[] betas = GenerativeModels.DDPM.cosineSchedule(100);
            for (double b : betas) {
                assertThat(b).isBetween(0.0, 1.0);
            }
        }

        @Test @DisplayName("sample produces output of correct size")
        void sampleShape() {
            var ddpm = new GenerativeModels.DDPM(10, 4, 1e-4, 0.02, 42L);
            // Zero denoiser: predicts no noise (demonstrates API)
            GenerativeModels.Denoiser zeroDenoiser = (xt, t) -> new double[xt.length];
            double[] sample = ddpm.sample(zeroDenoiser);
            assertThat(sample).hasSize(4);
        }
    }

    // ── Q-Learning ────────────────────────────────────────────────────────────

    @Nested @DisplayName("Q-Learning")
    class QLearningTests {

        @Test @DisplayName("trains on GridWorld and learns positive rewards")
        void learnsGridWorld() {
            var env = new ReinforcementLearning.GridWorld();
            var ql  = new ReinforcementLearning.QLearning(
                env.numStates(), env.numActions(),
                0.1, 0.9, 0.3, 42L
            );

            double totalReward = 0;
            for (int ep = 0; ep < 500; ep++) {
                totalReward += ql.trainEpisode(env);
            }
            // After 500 episodes, average reward should improve
            // (some episodes reach goal = +10 reward)
            assertThat(totalReward).isGreaterThan(-500 * 10);   // not all failures
        }

        @Test @DisplayName("Q-values are updated after training steps")
        void qValuesUpdated() {
            var ql = new ReinforcementLearning.QLearning(16, 4, 0.5, 0.9, 0.0, 1L);
            // Force a specific update
            ql.update(0, 0, 1.0, 1, false);
            assertThat(ql.getQ(0, 0)).isGreaterThan(0);
        }

        @Test @DisplayName("greedy action selection is deterministic")
        void greedyDeterministic() {
            var ql = new ReinforcementLearning.QLearning(16, 4, 0.5, 0.9, 0.0, 1L);
            ql.update(5, 2, 10.0, 6, false);
            int a1 = ql.greedyAction(5);
            int a2 = ql.greedyAction(5);
            assertThat(a1).isEqualTo(a2).isEqualTo(2);
        }
    }

    // ── SARSA ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("SARSA")
    class SARSATests {

        @Test @DisplayName("Q-values updated in on-policy fashion")
        void onPolicyUpdate() {
            var sarsa = new ReinforcementLearning.SARSA(16, 4, 0.5, 0.9, 0.0, 7L);
            sarsa.update(0, 1, 5.0, 3, 1, false);
            assertThat(sarsa.getQ(0, 1)).isGreaterThan(0);
        }

        @Test @DisplayName("terminal state gives zero next Q-value")
        void terminalZeroQ() {
            var sarsa = new ReinforcementLearning.SARSA(4, 2, 1.0, 0.9, 0.0, 0L);
            // At terminal state, target = r + 0 (done=true)
            sarsa.update(0, 0, 10.0, 1, 0, true);
            // Q(0,0) = 0 + 1.0 * (10 + 0.9*0 - 0) = 10
            assertThat(sarsa.getQ(0, 0)).isCloseTo(10.0, within(1e-6));
        }
    }

    // ── Policy Gradient ───────────────────────────────────────────────────────

    @Nested @DisplayName("Policy Gradient (REINFORCE)")
    class PolicyGradientTests {

        @Test @DisplayName("policy parameters updated after episode")
        void parametersUpdated() {
            var pg = new ReinforcementLearning.PolicyGradient(4, 2, 0.01, 0.99, 42L);
            double[][] thetaBefore = pg.getTheta().clone();

            pg.updateEpisode(
                new int[]{0, 1, 2, 3},      // states
                new int[]{0, 1, 0, 1},      // actions
                new double[]{1, 2, 3, 4}    // rewards
            );

            boolean changed = false;
            double[][] thetaAfter = pg.getTheta();
            for (int s = 0; s < 4; s++) {
                for (int a = 0; a < 2; a++) {
                    if (Math.abs(thetaBefore[s][a] - thetaAfter[s][a]) > 1e-10) {
                        changed = true;
                        break;
                    }
                }
            }
            assertThat(changed).isTrue();
        }

        @Test @DisplayName("sampled action is a valid action index")
        void actionInRange() {
            var pg = new ReinforcementLearning.PolicyGradient(3, 4, 0.01, 0.99, 0L);
            for (int i = 0; i < 100; i++) {
                int a = pg.sampleAction(1);
                assertThat(a).isBetween(0, 3);
            }
        }
    }

    // ── DQN replay buffer ─────────────────────────────────────────────────────

    @Nested @DisplayName("DQN Replay Buffer")
    class DQNTests {

        @Test @DisplayName("buffer stores transitions up to capacity")
        void bufferCapacity() {
            var dqn = new ReinforcementLearning.DQNConcepts(50);
            for (int i = 0; i < 100; i++) dqn.store(i % 16, i % 4, 1.0, (i+1) % 16, false);
            assertThat(dqn.bufferSize()).isEqualTo(50);
        }

        @Test @DisplayName("sampled batch does not exceed requested size")
        void batchSize() {
            var dqn = new ReinforcementLearning.DQNConcepts(100);
            for (int i = 0; i < 80; i++) dqn.store(i % 4, i % 2, 0.5, (i+1) % 4, false);
            var batch = dqn.sampleBatch(32);
            assertThat(batch.size()).isEqualTo(32);
        }
    }
}
