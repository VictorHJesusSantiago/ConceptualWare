package com.conceptualware.core.ml;

import java.util.*;

public class TransformerAttention {

    public static double[][] scaledDotProductAttention(double[][] Q, double[][] K, double[][] V) {
        return scaledDotProductAttention(Q, K, V, null);
    }

    public static double[][] scaledDotProductAttention(double[][] Q, double[][] K, double[][] V, boolean[][] mask) {
        int seqLen = Q.length, dk = Q[0].length;
        double scale = 1.0 / Math.sqrt(dk);

        double[][] scores = new double[seqLen][seqLen];
        for (int i = 0; i < seqLen; i++)
            for (int j = 0; j < seqLen; j++) {
                for (int k = 0; k < dk; k++) scores[i][j] += Q[i][k] * K[j][k];
                scores[i][j] *= scale;
                if (mask != null && mask[i][j]) scores[i][j] = Double.NEGATIVE_INFINITY;
            }

        double[][] attnWeights = new double[seqLen][seqLen];
        for (int i = 0; i < seqLen; i++) attnWeights[i] = softmax(scores[i]);

        double[][] output = new double[seqLen][V[0].length];
        for (int i = 0; i < seqLen; i++)
            for (int j = 0; j < seqLen; j++)
                for (int v = 0; v < V[0].length; v++)
                    output[i][v] += attnWeights[i][j] * V[j][v];

        return output;
    }

    public static class MultiHeadAttention {
        private final int numHeads;
        private final int dModel;
        private final int dK;
        private final double[][] Wq, Wk, Wv, Wo;

        public MultiHeadAttention(int numHeads, int dModel, Random rng) {
            this.numHeads = numHeads;
            this.dModel   = dModel;
            this.dK       = dModel / numHeads;
            double scale = Math.sqrt(1.0 / dModel);
            Wq = randomMatrix(dModel, dModel, rng, scale);
            Wk = randomMatrix(dModel, dModel, rng, scale);
            Wv = randomMatrix(dModel, dModel, rng, scale);
            Wo = randomMatrix(dModel, dModel, rng, scale);
        }

        public double[][] forward(double[][] x) {
            return forward(x, x, x, null);
        }

        public double[][] forward(double[][] query, double[][] key, double[][] value, boolean[][] mask) {
            int seqLen = query.length;
            double[][] Q = project(query, Wq);
            double[][] K = project(key,   Wk);
            double[][] V = project(value, Wv);

            double[][][] headOutputs = new double[numHeads][seqLen][dK];
            for (int h = 0; h < numHeads; h++) {
                double[][] Qh = slice(Q, h * dK, dK);
                double[][] Kh = slice(K, h * dK, dK);
                double[][] Vh = slice(V, h * dK, dK);
                headOutputs[h] = scaledDotProductAttention(Qh, Kh, Vh, mask);
            }

            double[][] concatenated = new double[seqLen][dModel];
            for (int i = 0; i < seqLen; i++)
                for (int h = 0; h < numHeads; h++)
                    for (int k = 0; k < dK; k++)
                        concatenated[i][h * dK + k] = headOutputs[h][i][k];

            return project(concatenated, Wo);
        }

        private double[][] project(double[][] x, double[][] W) {
            int seq = x.length, out = W.length;
            double[][] result = new double[seq][out];
            for (int i = 0; i < seq; i++)
                for (int r = 0; r < out; r++)
                    for (int c = 0; c < x[0].length; c++)
                        result[i][r] += x[i][c] * W[r][c];
            return result;
        }

        private double[][] slice(double[][] x, int startCol, int numCols) {
            double[][] out = new double[x.length][numCols];
            for (int i = 0; i < x.length; i++) System.arraycopy(x[i], startCol, out[i], 0, numCols);
            return out;
        }

        private double[][] randomMatrix(int rows, int cols, Random rng, double scale) {
            double[][] m = new double[rows][cols];
            for (int i=0;i<rows;i++) for (int j=0;j<cols;j++) m[i][j] = rng.nextGaussian() * scale;
            return m;
        }
    }

    public static double[][] positionalEncoding(int maxSeqLen, int dModel) {
        double[][] PE = new double[maxSeqLen][dModel];
        for (int pos = 0; pos < maxSeqLen; pos++) {
            for (int i = 0; i < dModel / 2; i++) {
                double angle = pos / Math.pow(10000, 2.0 * i / dModel);
                PE[pos][2 * i]     = Math.sin(angle);
                PE[pos][2 * i + 1] = Math.cos(angle);
            }
        }
        return PE;
    }

    public static class FeedForward {
        private final double[][] W1, W2;
        private final double[]   b1, b2;

        public FeedForward(int dModel, Random rng) {
            int dFF = 4 * dModel;
            double s1 = Math.sqrt(2.0 / dModel), s2 = Math.sqrt(2.0 / dFF);
            W1 = randomMatrix(dFF,    dModel, rng, s1);
            W2 = randomMatrix(dModel, dFF,    rng, s2);
            b1 = new double[dFF];
            b2 = new double[dModel];
        }

        public double[] forward(double[] x) {
            double[] h = new double[W1.length];
            for (int i = 0; i < W1.length; i++) {
                h[i] = b1[i];
                for (int j = 0; j < x.length; j++) h[i] += W1[i][j] * x[j];
                h[i] = Math.max(0, h[i]);
            }
            double[] out = new double[W2.length];
            for (int i = 0; i < W2.length; i++) {
                out[i] = b2[i];
                for (int j = 0; j < h.length; j++) out[i] += W2[i][j] * h[j];
            }
            return out;
        }

        private double[][] randomMatrix(int rows, int cols, Random rng, double scale) {
            double[][] m = new double[rows][cols];
            for (int i=0;i<rows;i++) for (int j=0;j<cols;j++) m[i][j] = rng.nextGaussian() * scale;
            return m;
        }
    }

    public static double[] layerNorm(double[] x, double[] gamma, double[] beta, double eps) {
        double mean = 0, var = 0;
        for (double v : x) mean += v;
        mean /= x.length;
        for (double v : x) var += Math.pow(v - mean, 2);
        var /= x.length;

        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) out[i] = gamma[i] * (x[i] - mean) / Math.sqrt(var + eps) + beta[i];
        return out;
    }

    public static class BPETokenizer {
        private final Map<String, Integer> vocab    = new LinkedHashMap<>();
        private final List<String[]>       mergeRules = new ArrayList<>();

        public void train(String[] corpus, int targetVocabSize) {
            Set<Character> chars = new LinkedHashSet<>();
            for (String s : corpus) for (char c : s.toCharArray()) chars.add(c);
            int id = 0;
            for (char c : chars) vocab.put(String.valueOf(c), id++);

            List<List<String>> tokenized = new ArrayList<>();
            for (String s : corpus) {
                List<String> tokens = new ArrayList<>();
                for (char c : s.toCharArray()) tokens.add(String.valueOf(c));
                if (!tokens.isEmpty()) tokens.set(tokens.size()-1, tokens.get(tokens.size()-1) + "</w>");
                tokenized.add(tokens);
            }

            final String PAIR_DELIM = "";

            while (vocab.size() < targetVocabSize) {
                Map<String, Integer> pairCounts = new HashMap<>();
                for (var word : tokenized) {
                    for (int i = 0; i < word.size() - 1; i++) {
                        String pair = word.get(i) + PAIR_DELIM + word.get(i+1);
                        pairCounts.merge(pair, 1, Integer::sum);
                    }
                }
                if (pairCounts.isEmpty()) break;

                String bestPair = pairCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).get().getKey();
                String[] parts = bestPair.split(PAIR_DELIM, -1);
                String merged = parts[0] + parts[1];
                vocab.put(merged, id++);
                mergeRules.add(parts);

                for (var word : tokenized) {
                    for (int i = 0; i < word.size() - 1; i++) {
                        if (word.get(i).equals(parts[0]) && word.get(i+1).equals(parts[1])) {
                            word.set(i, merged);
                            word.remove(i+1);
                        }
                    }
                }
            }
        }

        public List<String> tokenize(String text) {
            List<String> tokens = new ArrayList<>();
            for (char c : text.toCharArray()) tokens.add(String.valueOf(c));

            for (String[] rule : mergeRules) {
                for (int i = 0; i < tokens.size() - 1; i++) {
                    if (tokens.get(i).equals(rule[0]) && tokens.get(i+1).equals(rule[1])) {
                        tokens.set(i, rule[0] + rule[1]);
                        tokens.remove(i+1);
                    }
                }
            }
            return tokens;
        }

        public int vocabSize() { return vocab.size(); }
    }

    public static class SimpleVectorStore {
        private final List<String>   documents  = new ArrayList<>();
        private final List<double[]> embeddings = new ArrayList<>();
        private final int            embeddingDim;
        private final Random         rng;

        public SimpleVectorStore(int embeddingDim, long seed) {
            this.embeddingDim = embeddingDim;
            this.rng = new Random(seed);
        }

        public void addDocument(String doc, double[] embedding) {
            documents.add(doc);
            embeddings.add(embedding);
        }

        public double[] simulateEmbedding(String text) {
            rng.setSeed(text.hashCode());
            double[] v = new double[embeddingDim];
            double norm = 0;
            for (int i = 0; i < embeddingDim; i++) { v[i] = rng.nextGaussian(); norm += v[i]*v[i]; }
            norm = Math.sqrt(norm);
            for (int i = 0; i < embeddingDim; i++) v[i] /= norm;
            return v;
        }

        public List<String> retrieve(double[] queryEmbedding, int k) {
            List<Map.Entry<Integer, Double>> scored = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++)
                scored.add(Map.entry(i, cosineSim(queryEmbedding, embeddings.get(i))));
            scored.sort(Map.Entry.<Integer, Double>comparingByValue().reversed());
            return scored.stream().limit(k).map(e -> documents.get(e.getKey())).toList();
        }

        private double cosineSim(double[] a, double[] b) {
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < a.length; i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
            return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
        }
    }

    private static double[] softmax(double[] z) {
        double max = Arrays.stream(z).max().orElse(0);
        double[] exp = new double[z.length];
        double sum = 0;
        for (int i = 0; i < z.length; i++) { exp[i] = Math.exp(z[i] - max); sum += exp[i]; }
        for (int i = 0; i < z.length; i++) exp[i] /= sum;
        return exp;
    }
}
