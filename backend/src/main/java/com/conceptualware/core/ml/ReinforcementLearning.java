package com.conceptualware.core.ml;

import java.util.*;

/**
 * Concept #30 — Reinforcement Learning (RL)
 *
 * RL is a paradigm where an agent learns to maximize cumulative reward
 * through interaction with an environment, without labelled data.
 *
 * Key components:
 *   Agent:       the learner/decision-maker
 *   Environment: what the agent interacts with
 *   State (s):   current situation
 *   Action (a):  what the agent can do
 *   Reward (r):  scalar feedback signal
 *   Policy (π):  strategy — maps states to actions π(s) → a
 *   Value (V):   expected cumulative reward from a state
 *   Q-value:     expected cumulative reward for (state, action) pair
 *
 * Return (discounted sum of future rewards):
 *   Gₜ = rₜ + γrₜ₊₁ + γ²rₜ₊₂ + ... = Σₖ γᵏ rₜ₊ₖ
 *   γ (gamma): discount factor in [0,1] — 0=myopic, 1=far-sighted
 *
 * Algorithms implemented:
 *   1. Q-Learning (off-policy, tabular) — Watkins 1989
 *   2. SARSA (on-policy, tabular) — Rummery & Niranjan 1994
 *   3. Policy Gradient / REINFORCE — Williams 1992
 *   4. Deep Q-Network (DQN) concepts — Mnih et al. 2013
 */
public class ReinforcementLearning {

    // ── Q-Learning ─────────────────────────────────────────────────────────────
    /**
     * Q-Learning (off-policy temporal-difference learning):
     *
     *   Q(s,a) ← Q(s,a) + α[r + γ max_a' Q(s',a') − Q(s,a)]
     *
     * "Off-policy" = learns optimal policy regardless of the behavior policy
     * (can learn from e-greedy exploration while converging to greedy optimal).
     *
     * Convergence theorem: Q-Learning converges to Q* under mild conditions
     * (all state-action pairs visited infinitely often, learning rate conditions).
     *
     * Grid World example: agent navigates a grid to reach a goal.
     * States = (row, col), Actions = {UP, DOWN, LEFT, RIGHT}.
     */
    public static class QLearning {
        private final double[][][] Q;   // Q[state][action] — tabular Q-table
        private final int numStates;
        private final int numActions;
        private final double alpha;     // learning rate
        private final double gamma;     // discount factor
        private final double epsilon;   // ε-greedy exploration rate
        private final Random rng;

        public QLearning(int numStates, int numActions,
                         double alpha, double gamma, double epsilon, long seed) {
            this.numStates  = numStates;
            this.numActions = numActions;
            this.alpha      = alpha;
            this.gamma      = gamma;
            this.epsilon    = epsilon;
            this.rng        = new Random(seed);

            // Q-table is a 2D array — for demo, first dim is flattened state
            this.Q = new double[numStates][1][numActions];
        }

        /**
         * ε-greedy action selection:
         *   with probability ε → random action (exploration)
         *   with probability 1−ε → greedy action (exploitation)
         */
        public int selectAction(int state) {
            if (rng.nextDouble() < epsilon) {
                return rng.nextInt(numActions);   // explore
            }
            return greedyAction(state);           // exploit
        }

        /** arg max_a Q(s, a) */
        public int greedyAction(int state) {
            int best = 0;
            for (int a = 1; a < numActions; a++) {
                if (Q[state][0][a] > Q[state][0][best]) best = a;
            }
            return best;
        }

        /**
         * Q-Learning update (called after each step):
         * Q(s,a) ← Q(s,a) + α [ r + γ max_a' Q(s',a') − Q(s,a) ]
         *
         * @param s      current state
         * @param a      action taken
         * @param r      reward received
         * @param sPrime next state
         * @param done   true if episode ended (terminal state)
         */
        public void update(int s, int a, double r, int sPrime, boolean done) {
            double maxNextQ = done ? 0.0 : maxQ(sPrime);
            double tdTarget = r + gamma * maxNextQ;
            double tdError  = tdTarget - Q[s][0][a];
            Q[s][0][a] += alpha * tdError;
        }

        public double getQ(int state, int action) { return Q[state][0][action]; }

        private double maxQ(int state) {
            double max = Double.NEGATIVE_INFINITY;
            for (int a = 0; a < numActions; a++) max = Math.max(max, Q[state][0][a]);
            return max;
        }

        /** Run one full training episode on a simple environment. */
        public double trainEpisode(Environment env) {
            int state = env.reset();
            double totalReward = 0;
            for (int step = 0; step < 1000; step++) {
                int action = selectAction(state);
                StepResult result = env.step(action);
                update(state, action, result.reward(), result.nextState(), result.done());
                totalReward += result.reward();
                state = result.nextState();
                if (result.done()) break;
            }
            return totalReward;
        }
    }

    // ── SARSA ─────────────────────────────────────────────────────────────────
    /**
     * SARSA (on-policy temporal-difference):
     *
     *   Q(s,a) ← Q(s,a) + α [ r + γ Q(s',a') − Q(s,a) ]
     *
     * Difference from Q-Learning: uses ACTUAL next action a' (from behavior policy)
     * instead of max_a'. On-policy = learns about the policy it's following.
     *
     * SARSA is more conservative — avoids risky paths even if they have high Q.
     * Q-Learning is more aggressive — assumes optimal future behaviour.
     *
     * Example: cliff walking — SARSA learns safe path far from cliff;
     *          Q-Learning learns optimal path close to cliff (risky if exploring).
     */
    public static class SARSA {
        private final double[][] Q;
        private final double alpha, gamma, epsilon;
        private final Random rng;

        public SARSA(int numStates, int numActions,
                     double alpha, double gamma, double epsilon, long seed) {
            this.Q       = new double[numStates][numActions];
            this.alpha   = alpha;
            this.gamma   = gamma;
            this.epsilon = epsilon;
            this.rng     = new Random(seed);
        }

        public int selectAction(int state) {
            if (rng.nextDouble() < epsilon) return rng.nextInt(Q[state].length);
            return argmax(Q[state]);
        }

        /** SARSA update: uses actual next action a' (not max) */
        public void update(int s, int a, double r, int sPrime, int aPrime, boolean done) {
            double nextQ = done ? 0.0 : Q[sPrime][aPrime];
            Q[s][a] += alpha * (r + gamma * nextQ - Q[s][a]);
        }

        public double getQ(int s, int a) { return Q[s][a]; }

        private static int argmax(double[] arr) {
            int best = 0;
            for (int i = 1; i < arr.length; i++) if (arr[i] > arr[best]) best = i;
            return best;
        }
    }

    // ── Policy Gradient / REINFORCE ────────────────────────────────────────────
    /**
     * REINFORCE (Monte Carlo Policy Gradient):
     *
     * Instead of learning Q-values, directly parameterize a policy π_θ(a|s)
     * and update parameters to maximize expected return.
     *
     * Gradient of expected return:
     *   ∇_θ J(θ) = 𝔼_π [ Gₜ · ∇_θ log π_θ(aₜ|sₜ) ]
     *
     * Algorithm:
     *   1. Run episode with current policy
     *   2. Compute returns Gₜ for each time step
     *   3. Update: θ ← θ + α Σₜ Gₜ ∇_θ log π_θ(aₜ|sₜ)
     *
     * Log-likelihood trick: ∇_θ log π = ∇_θ π / π
     *   Avoids computing high-variance gradient directly.
     *
     * Baseline: subtract V(s) from Gₜ to reduce variance (actor-critic).
     *   Gₜ − V(sₜ) = advantage estimate Aₜ
     *
     * This implementation uses a softmax policy over linear function approximation.
     */
    public static class PolicyGradient {
        private final double[][] theta;   // policy parameters [numStates × numActions]
        private final double alpha;
        private final double gamma;
        private final Random rng;

        public PolicyGradient(int numStates, int numActions, double alpha, double gamma, long seed) {
            this.theta = new double[numStates][numActions];
            this.alpha = alpha;
            this.gamma = gamma;
            this.rng   = new Random(seed);
        }

        /** Sample action from softmax policy π_θ(a|s) = softmax(θ[s])_a */
        public int sampleAction(int state) {
            double[] probs = softmax(theta[state]);
            double r = rng.nextDouble();
            double cumSum = 0;
            for (int a = 0; a < probs.length - 1; a++) {
                cumSum += probs[a];
                if (r < cumSum) return a;
            }
            return probs.length - 1;
        }

        /**
         * Update policy after one episode.
         *
         * @param states   visited states [T]
         * @param actions  actions taken [T]
         * @param rewards  rewards received [T]
         */
        public void updateEpisode(int[] states, int[] actions, double[] rewards) {
            int T = rewards.length;

            // Compute discounted returns Gₜ for each time step
            double[] returns = new double[T];
            double G = 0;
            for (int t = T - 1; t >= 0; t--) {
                G = rewards[t] + gamma * G;
                returns[t] = G;
            }

            // Normalize returns to reduce variance (baseline trick)
            double mean = mean(returns), std = std(returns);

            // Update: θ[s][a] += α · Gₜ · ∇_θ log π_θ(aₜ|sₜ)
            // ∇_θ log softmax(θ)_a = δ(a,a*) − softmax(θ)_a  (policy gradient for softmax)
            for (int t = 0; t < T; t++) {
                int s = states[t], a = actions[t];
                double normalizedReturn = std > 1e-8 ? (returns[t] - mean) / std : returns[t];
                double[] probs = softmax(theta[s]);

                for (int aIdx = 0; aIdx < theta[s].length; aIdx++) {
                    // Gradient of log π w.r.t. θ[s][aIdx]
                    double grad = (aIdx == a ? 1.0 : 0.0) - probs[aIdx];
                    theta[s][aIdx] += alpha * normalizedReturn * grad;
                }
            }
        }

        private static double[] softmax(double[] logits) {
            double maxLogit = Double.NEGATIVE_INFINITY;
            for (double l : logits) maxLogit = Math.max(maxLogit, l);
            double sum = 0;
            double[] probs = new double[logits.length];
            for (int i = 0; i < logits.length; i++) {
                probs[i] = Math.exp(logits[i] - maxLogit);
                sum += probs[i];
            }
            for (int i = 0; i < probs.length; i++) probs[i] /= sum;
            return probs;
        }

        private static double mean(double[] arr) {
            double s = 0; for (double v : arr) s += v; return s / arr.length;
        }

        private static double std(double[] arr) {
            double m = mean(arr), s = 0;
            for (double v : arr) s += (v - m) * (v - m);
            return Math.sqrt(s / arr.length);
        }

        public double[][] getTheta() { return theta; }
    }

    // ── DQN Concept ────────────────────────────────────────────────────────────
    /**
     * Deep Q-Network (DQN) — conceptual description:
     *
     * DQN (Mnih et al., 2013 — "Playing Atari with Deep Reinforcement Learning")
     * extends Q-Learning to high-dimensional state spaces using neural networks.
     *
     * Key innovations over tabular Q-Learning:
     *
     * 1. Neural function approximation:
     *      Q(s, a; θ) ≈ Q*(s, a)
     *    Network takes state s as input, outputs Q-values for all actions.
     *
     * 2. Experience Replay:
     *    Store transitions (s, a, r, s') in replay buffer D.
     *    Sample random mini-batches to break correlation between consecutive samples.
     *    Stabilizes training — correlated updates diverge.
     *
     * 3. Target Network:
     *    Maintain separate "target network" θ⁻ updated every C steps.
     *    TD target: r + γ max_a' Q(s', a'; θ⁻)
     *    Prevents moving target (Q-target depends on current Q which is updating).
     *
     * 4. Reward Clipping:
     *    Clip rewards to [-1, +1] for stable training across different games.
     *
     * Loss: L(θ) = 𝔼[(r + γ max_a' Q(s',a';θ⁻) − Q(s,a;θ))²]
     *
     * Extensions: Double DQN, Dueling DQN, Prioritized Experience Replay, Rainbow.
     */
    public static class DQNConcepts {
        private final int bufferSize;
        private final List<Transition> replayBuffer = new ArrayList<>();
        private final Random rng = new Random(42L);

        // Transition tuple (s, a, r, s', done)
        record Transition(int state, int action, double reward, int nextState, boolean done) {}

        public DQNConcepts(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        /** Add transition to experience replay buffer (circular). */
        public void store(int s, int a, double r, int sPrime, boolean done) {
            if (replayBuffer.size() >= bufferSize) {
                replayBuffer.remove(0);   // evict oldest (FIFO)
            }
            replayBuffer.add(new Transition(s, a, r, sPrime, done));
        }

        /** Sample a random mini-batch of transitions for training. */
        public List<Transition> sampleBatch(int batchSize) {
            List<Transition> batch = new ArrayList<>(batchSize);
            List<Transition> copy  = new ArrayList<>(replayBuffer);
            Collections.shuffle(copy, rng);
            for (int i = 0; i < Math.min(batchSize, copy.size()); i++) {
                batch.add(copy.get(i));
            }
            return batch;
        }

        public int bufferSize() { return replayBuffer.size(); }
    }

    // ── Environment interface ──────────────────────────────────────────────────

    /** Minimal interface for an RL environment (Gym-like). */
    public interface Environment {
        int reset();                     // reset and return initial state
        StepResult step(int action);     // take action, return (nextState, reward, done)
        int numStates();
        int numActions();
    }

    /** Result of an environment step. */
    public record StepResult(int nextState, double reward, boolean done) {}

    // ── Simple GridWorld environment ───────────────────────────────────────────
    /**
     * 4×4 grid world:
     *   Start: (0,0), Goal: (3,3)
     *   Rewards: goal=+10, hole=-5, step=-0.1
     *   Actions: 0=UP, 1=DOWN, 2=LEFT, 3=RIGHT
     *
     * Demonstrates Q-Learning convergence on a simple discrete MDP.
     */
    public static class GridWorld implements Environment {
        private static final int ROWS = 4, COLS = 4;
        // Holes: cells where agent falls and gets negative reward
        private static final Set<Integer> HOLES = Set.of(5, 7, 11, 12);

        private int agentRow, agentCol;

        @Override
        public int reset() {
            agentRow = 0; agentCol = 0;
            return stateId(agentRow, agentCol);
        }

        @Override
        public StepResult step(int action) {
            int newRow = agentRow, newCol = agentCol;
            switch (action) {
                case 0 -> newRow = Math.max(0, agentRow - 1);        // UP
                case 1 -> newRow = Math.min(ROWS - 1, agentRow + 1); // DOWN
                case 2 -> newCol = Math.max(0, agentCol - 1);        // LEFT
                case 3 -> newCol = Math.min(COLS - 1, agentCol + 1); // RIGHT
            }
            agentRow = newRow; agentCol = newCol;
            int state = stateId(agentRow, agentCol);

            if (agentRow == ROWS - 1 && agentCol == COLS - 1) {
                return new StepResult(state, 10.0, true);   // goal
            }
            if (HOLES.contains(state)) {
                return new StepResult(state, -5.0, true);   // hole
            }
            return new StepResult(state, -0.1, false);      // step penalty
        }

        @Override public int numStates()  { return ROWS * COLS; }
        @Override public int numActions() { return 4; }

        private static int stateId(int row, int col) { return row * COLS + col; }
    }
}
