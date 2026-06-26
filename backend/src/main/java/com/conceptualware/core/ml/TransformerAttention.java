package com.conceptualware.core.ml;

import java.util.*;

/**
 * Concept #30 — Transformers & Attention Mechanism:
 *
 *   Introduced in "Attention Is All You Need" (Vaswani et al., 2017).
 *   Revolutionized NLP → led to BERT, GPT, T5, LLaMA, Claude.
 *
 *   SELF-ATTENTION (Scaled Dot-Product Attention):
 *     Given: Query Q, Key K, Value V (each [seq_len × d_k])
 *     Attention(Q,K,V) = softmax(QKᵀ / √d_k) · V
 *
 *     Intuition:
 *       Q: what I'm looking for
 *       K: what I have to offer
 *       V: what I'll contribute if matched
 *       QKᵀ: compatibility score (similarity) — scaled by √d_k to prevent gradient vanishing
 *       softmax: normalize scores to probability distribution (attention weights)
 *       × V: weighted average of values
 *
 *   MULTI-HEAD ATTENTION:
 *     Split d_model into h heads of size d_k = d_model/h.
 *     Each head attends to different representation subspaces:
 *       headᵢ = Attention(QWᵢQ, KWᵢK, VWᵢV)
 *     Concatenate and project: MultiHead(Q,K,V) = Concat(head₁,...,headₕ) Wᴼ
 *
 *   POSITIONAL ENCODING:
 *     Transformers have no inherent sequence order (unlike RNNs).
 *     Add positional signal PE to input embeddings:
 *       PE(pos, 2i)   = sin(pos / 10000^(2i/d_model))
 *       PE(pos, 2i+1) = cos(pos / 10000^(2i/d_model))
 *     Sinusoidal: unique for each position, generalizes to unseen lengths.
 *     Learned alternatives: RoPE (rotary), ALiBi (linear bias).
 *
 *   TRANSFORMER BLOCK:
 *     x → LayerNorm → MultiHeadAttn + x → LayerNorm → FFN + x
 *     (Pre-norm variant; original paper used post-norm)
 *     FFN: Linear(d_model → 4·d_model) → ReLU → Linear(4·d_model → d_model)
 *
 *   LLM TOKEN TYPES:
 *     BPE (Byte Pair Encoding): merge frequent byte pairs iteratively → vocabulary
 *     WordPiece, SentencePiece: similar subword tokenization strategies
 *
 *   EMBEDDINGS & RAG:
 *     Embeddings: dense vector representations of tokens/sentences
 *     Cosine similarity: sim(a,b) = a·b / (||a||·||b||)
 *     RAG (Retrieval Augmented Generation):
 *       1. Encode query → embedding
 *       2. Retrieve top-k similar documents from vector store
 *       3. Concatenate docs + query → feed to LLM → grounded answer
 */
public class TransformerAttention {

    // ── Scaled Dot-Product Attention ──────────────────────────────────────────

    /**
     * Attention(Q, K, V) = softmax(QKᵀ / √d_k) · V
     *
     * @param Q [seqLen × d_k]
     * @param K [seqLen × d_k]
     * @param V [seqLen × d_v]
     * @return  [seqLen × d_v]
     */
    public static double[][] scaledDotProductAttention(double[][] Q, double[][] K, double[][] V) {
        return scaledDotProductAttention(Q, K, V, null);
    }

    public static double[][] scaledDotProductAttention(double[][] Q, double[][] K, double[][] V, boolean[][] mask) {
        int seqLen = Q.length, dk = Q[0].length;
        double scale = 1.0 / Math.sqrt(dk);

        // Compute scores: QKᵀ [seqLen × seqLen]
        double[][] scores = new double[seqLen][seqLen];
        for (int i = 0; i < seqLen; i++)
            for (int j = 0; j < seqLen; j++) {
                for (int k = 0; k < dk; k++) scores[i][j] += Q[i][k] * K[j][k];
                scores[i][j] *= scale;
                // Causal mask: prevents attending to future positions
                if (mask != null && mask[i][j]) scores[i][j] = Double.NEGATIVE_INFINITY;
            }

        // Apply softmax row-wise
        double[][] attnWeights = new double[seqLen][seqLen];
        for (int i = 0; i < seqLen; i++) attnWeights[i] = softmax(scores[i]);

        // Weighted sum of values
        double[][] output = new double[seqLen][V[0].length];
        for (int i = 0; i < seqLen; i++)
            for (int j = 0; j < seqLen; j++)
                for (int v = 0; v < V[0].length; v++)
                    output[i][v] += attnWeights[i][j] * V[j][v];

        return output;
    }

    // ── Multi-Head Attention ──────────────────────────────────────────────────

    public static class MultiHeadAttention {
        private final int numHeads;
        private final int dModel;
        private final int dK;     // d_model / num_heads
        private final double[][] Wq, Wk, Wv, Wo; // projection matrices

        public MultiHeadAttention(int numHeads, int dModel, Random rng) {
            this.numHeads = numHeads;
            this.dModel   = dModel;
            this.dK       = dModel / numHeads;
            // [dModel × dModel] projection matrices
            double scale = Math.sqrt(1.0 / dModel);
            Wq = randomMatrix(dModel, dModel, rng, scale);
            Wk = randomMatrix(dModel, dModel, rng, scale);
            Wv = randomMatrix(dModel, dModel, rng, scale);
            Wo = randomMatrix(dModel, dModel, rng, scale);
        }

        /**
         * @param x [seqLen × dModel] input
         * @return  [seqLen × dModel] output
         */
        public double[][] forward(double[][] x) {
            return forward(x, x, x, null); // self-attention
        }

        public double[][] forward(double[][] query, double[][] key, double[][] value, boolean[][] mask) {
            int seqLen = query.length;
            double[][] Q = project(query, Wq);
            double[][] K = project(key,   Wk);
            double[][] V = project(value, Wv);

            // Split into heads
            double[][][] headOutputs = new double[numHeads][seqLen][dK];
            for (int h = 0; h < numHeads; h++) {
                double[][] Qh = slice(Q, h * dK, dK);
                double[][] Kh = slice(K, h * dK, dK);
                double[][] Vh = slice(V, h * dK, dK);
                headOutputs[h] = scaledDotProductAttention(Qh, Kh, Vh, mask);
            }

            // Concatenate heads [seqLen × (numHeads × dK)] = [seqLen × dModel]
            double[][] concatenated = new double[seqLen][dModel];
            for (int i = 0; i < seqLen; i++)
                for (int h = 0; h < numHeads; h++)
                    for (int k = 0; k < dK; k++)
                        concatenated[i][h * dK + k] = headOutputs[h][i][k];

            // Output projection
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

    // ── Positional Encoding ───────────────────────────────────────────────────

    /**
     * Sinusoidal positional encoding.
     *   PE(pos, 2i)   = sin(pos / 10000^(2i/dModel))
     *   PE(pos, 2i+1) = cos(pos / 10000^(2i/dModel))
     *
     * @param maxSeqLen maximum sequence length
     * @param dModel    embedding dimension
     * @return [maxSeqLen × dModel] positional encoding matrix
     */
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

    // ── Feed-Forward Network (FFN) within Transformer block ──────────────────

    public static class FeedForward {
        private final double[][] W1, W2;
        private final double[]   b1, b2;

        public FeedForward(int dModel, Random rng) {
            int dFF = 4 * dModel; // typical expansion factor
            double s1 = Math.sqrt(2.0 / dModel), s2 = Math.sqrt(2.0 / dFF);
            W1 = randomMatrix(dFF,    dModel, rng, s1);
            W2 = randomMatrix(dModel, dFF,    rng, s2);
            b1 = new double[dFF];
            b2 = new double[dModel];
        }

        public double[] forward(double[] x) {
            // Layer 1 + ReLU
            double[] h = new double[W1.length];
            for (int i = 0; i < W1.length; i++) {
                h[i] = b1[i];
                for (int j = 0; j < x.length; j++) h[i] += W1[i][j] * x[j];
                h[i] = Math.max(0, h[i]); // ReLU
            }
            // Layer 2
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

    // ── Layer Normalization ───────────────────────────────────────────────────

    /** LayerNorm: normalize over feature dimension (not batch). */
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

    // ── BPE Tokenization (conceptual) ─────────────────────────────────────────
    /**
     * Byte Pair Encoding (BPE):
     *   Used by GPT-2/3/4, LLAMA, Claude, etc.
     *
     *   Algorithm:
     *     1. Start with character-level vocab
     *     2. Count all adjacent pairs in corpus
     *     3. Merge the most frequent pair → new token
     *     4. Repeat until vocab reaches target size
     *
     *   This implementation: simplified BPE on a small corpus for demonstration.
     */
    public static class BPETokenizer {
        private final Map<String, Integer> vocab    = new LinkedHashMap<>();
        private final List<String[]>       mergeRules = new ArrayList<>();

        public void train(String[] corpus, int targetVocabSize) {
            // Initialize char-level vocab
            Set<Character> chars = new LinkedHashSet<>();
            for (String s : corpus) for (char c : s.toCharArray()) chars.add(c);
            int id = 0;
            for (char c : chars) vocab.put(String.valueOf(c), id++);

            // Tokenize corpus into characters with end-of-word marker
            List<List<String>> tokenized = new ArrayList<>();
            for (String s : corpus) {
                List<String> tokens = new ArrayList<>();
                for (char c : s.toCharArray()) tokens.add(String.valueOf(c));
                if (!tokens.isEmpty()) tokens.set(tokens.size()-1, tokens.get(tokens.size()-1) + "</w>");
                tokenized.add(tokens);
            }

            while (vocab.size() < targetVocabSize) {
                // Count pairs
                Map<String, Integer> pairCounts = new HashMap<>();
                for (var word : tokenized) {
                    for (int i = 0; i < word.size() - 1; i++) {
                        String pair = word.get(i) + " " + word.get(i+1);
                        pairCounts.merge(pair, 1, Integer::sum);
                    }
                }
                if (pairCounts.isEmpty()) break;

                // Find best pair
                String bestPair = pairCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).get().getKey();
                String[] parts = bestPair.split(" ");
                String merged = parts[0] + parts[1];
                vocab.put(merged, id++);
                mergeRules.add(parts);

                // Apply merge to all words
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

            // Apply merge rules in order
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

    // ── RAG (Retrieval-Augmented Generation) Conceptual ──────────────────────
    /**
     * RAG allows LLMs to access external knowledge without fine-tuning:
     *   1. Documents indexed as dense embeddings in a vector database
     *   2. Query encoded with same embedding model
     *   3. Top-k documents retrieved by cosine similarity
     *   4. Retrieved context + query prepended to LLM prompt
     *   5. LLM generates answer grounded in retrieved documents
     */
    public static class SimpleVectorStore {
        private final List<String>   documents  = new ArrayList<>();
        private final List<double[]> embeddings = new ArrayList<>();
        private final int            embeddingDim;
        private final Random         rng;

        public SimpleVectorStore(int embeddingDim, long seed) {
            this.embeddingDim = embeddingDim;
            this.rng = new Random(seed);
        }

        /** Index document with simulated embedding (in production: use real encoder). */
        public void addDocument(String doc, double[] embedding) {
            documents.add(doc);
            embeddings.add(embedding);
        }

        /** Simulate embedding with random vector (placeholder for real encoder). */
        public double[] simulateEmbedding(String text) {
            rng.setSeed(text.hashCode()); // deterministic for same text
            double[] v = new double[embeddingDim];
            double norm = 0;
            for (int i = 0; i < embeddingDim; i++) { v[i] = rng.nextGaussian(); norm += v[i]*v[i]; }
            norm = Math.sqrt(norm);
            for (int i = 0; i < embeddingDim; i++) v[i] /= norm;
            return v;
        }

        /** Retrieve top-k documents by cosine similarity. */
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

    // ── Helper: Softmax ───────────────────────────────────────────────────────

    private static double[] softmax(double[] z) {
        double max = Arrays.stream(z).max().orElse(0);
        double[] exp = new double[z.length];
        double sum = 0;
        for (int i = 0; i < z.length; i++) { exp[i] = Math.exp(z[i] - max); sum += exp[i]; }
        for (int i = 0; i < z.length; i++) exp[i] /= sum;
        return exp;
    }
}
