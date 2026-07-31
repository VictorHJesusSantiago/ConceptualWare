package com.conceptualware.core.ml;

import java.util.*;

public class ReinforcementLearning {

    public static class QLearning {
        private final double[][][] Q;
        private final int numStates;
        private final int numActions;
        private final double alpha;
        private final double gamma;
        private final double epsilon;
        private final Random rng;

        public QLearning(int numStates, int numActions,
                         double alpha, double gamma, double epsilon, long seed) {
            this.numStates  = numStates;
            this.numActions = numActions;
            this.alpha      = alpha;
            this.gamma      = gamma;
            this.epsilon    = epsilon;
            this.rng        = new Random(seed);

            this.Q = new double[numStates][1][numActions];
        }

        public int selectAction(int state) {
            if (rng.nextDouble() < epsilon) {
                return rng.nextInt(numActions);
            }
            return greedyAction(state);
        }

        public int greedyAction(int state) {
            int best = 0;
            for (int a = 1; a < numActions; a++) {
                if (Q[state][0][a] > Q[state][0][best]) best = a;
            }
            return best;
        }

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

    public static class PolicyGradient {
        private final double[][] theta;
        private final double alpha;
        private final double gamma;
        private final Random rng;

        public PolicyGradient(int numStates, int numActions, double alpha, double gamma, long seed) {
            this.theta = new double[numStates][numActions];
            this.alpha = alpha;
            this.gamma = gamma;
            this.rng   = new Random(seed);
        }

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

        public void updateEpisode(int[] states, int[] actions, double[] rewards) {
            int T = rewards.length;

            double[] returns = new double[T];
            double G = 0;
            for (int t = T - 1; t >= 0; t--) {
                G = rewards[t] + gamma * G;
                returns[t] = G;
            }

            double mean = mean(returns), std = std(returns);

            for (int t = 0; t < T; t++) {
                int s = states[t], a = actions[t];
                double normalizedReturn = std > 1e-8 ? (returns[t] - mean) / std : returns[t];
                double[] probs = softmax(theta[s]);

                for (int aIdx = 0; aIdx < theta[s].length; aIdx++) {
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

    public static class DQNConcepts {
        private final int bufferSize;
        private final List<Transition> replayBuffer = new ArrayList<>();
        private final Random rng = new Random(42L);

        record Transition(int state, int action, double reward, int nextState, boolean done) {}

        public DQNConcepts(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public void store(int s, int a, double r, int sPrime, boolean done) {
            if (replayBuffer.size() >= bufferSize) {
                replayBuffer.remove(0);
            }
            replayBuffer.add(new Transition(s, a, r, sPrime, done));
        }

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

    public interface Environment {
        int reset();
        StepResult step(int action);
        int numStates();
        int numActions();
    }

    public record StepResult(int nextState, double reward, boolean done) {}

    public static class GridWorld implements Environment {
        private static final int ROWS = 4, COLS = 4;
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
                case 0 -> newRow = Math.max(0, agentRow - 1);
                case 1 -> newRow = Math.min(ROWS - 1, agentRow + 1);
                case 2 -> newCol = Math.max(0, agentCol - 1);
                case 3 -> newCol = Math.min(COLS - 1, agentCol + 1);
            }
            agentRow = newRow; agentCol = newCol;
            int state = stateId(agentRow, agentCol);

            if (agentRow == ROWS - 1 && agentCol == COLS - 1) {
                return new StepResult(state, 10.0, true);
            }
            if (HOLES.contains(state)) {
                return new StepResult(state, -5.0, true);
            }
            return new StepResult(state, -0.1, false);
        }

        @Override public int numStates()  { return ROWS * COLS; }
        @Override public int numActions() { return 4; }

        private static int stateId(int row, int col) { return row * COLS + col; }
    }
}
