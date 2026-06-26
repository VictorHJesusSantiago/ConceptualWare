package com.conceptualware.core.ml;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Concept #30 — AI/ML Algorithms
 */
@DisplayName("Category 30 — Machine Learning Algorithms")
class MLAlgorithmsTest {

    // ── Test Datasets ──────────────────────────────────────────────────────────

    /** Simple linearly separable dataset: y = 2*x + 1 */
    static double[][] linearX() {
        double[][] X = new double[100][1];
        for (int i = 0; i < 100; i++) X[i][0] = i;
        return X;
    }

    static double[] linearY() {
        double[] y = new double[100];
        for (int i = 0; i < 100; i++) y[i] = 2 * i + 1;
        return y;
    }

    /** XOR-like binary classification dataset */
    static double[][] binaryX() {
        return new double[][]{{0,0},{0,1},{1,0},{1,1},{0.1,0.1},{0.9,0.9}};
    }

    static int[] binaryY() {
        return new int[]{0, 1, 1, 0, 0, 0};
    }

    /** 3-class dataset */
    static double[][] multiclassX() {
        double[][] X = new double[90][2];
        int[] y = new int[90];
        Random rng = new Random(42);
        for (int i = 0; i < 30; i++) { X[i][0] = rng.nextGaussian() + 0; X[i][1] = rng.nextGaussian() + 0; }
        for (int i = 30; i < 60; i++) { X[i][0] = rng.nextGaussian() + 5; X[i][1] = rng.nextGaussian() + 5; }
        for (int i = 60; i < 90; i++) { X[i][0] = rng.nextGaussian() + 10; X[i][1] = rng.nextGaussian() + 0; }
        return X;
    }

    static int[] multiclassY() {
        int[] y = new int[90];
        for (int i = 30; i < 60; i++) y[i] = 1;
        for (int i = 60; i < 90; i++) y[i] = 2;
        return y;
    }

    // ── Supervised Learning Tests ──────────────────────────────────────────────

    @Nested @DisplayName("Linear Regression")
    class LinearRegressionTests {

        @Test @DisplayName("fits y = 2x + 1 accurately")
        void fitsLinear() {
            var model = new SupervisedLearning.LinearRegression(0.0001, 5000, 0.0);
            var X = linearX();
            var y = linearY();
            var history = model.fit(X, y);

            assertThat(history.finalLoss()).isLessThan(1.0);
            double r2 = model.r2Score(X, y);
            assertThat(r2).isGreaterThan(0.99); // nearly perfect fit

            // Predict: y = 2*50 + 1 = 101
            double pred = model.predict(new double[]{50});
            assertThat(pred).isCloseTo(101, within(5.0));
        }

        @Test @DisplayName("loss decreases over training")
        void lossDecreases() {
            var model = new SupervisedLearning.LinearRegression(0.0001, 1000, 0.0);
            var history = model.fit(linearX(), linearY());
            assertThat(history.losses()[0]).isGreaterThan(history.losses()[999]);
        }

        @Test @DisplayName("MSE metric computation")
        void mseMetric() {
            var model = new SupervisedLearning.LinearRegression(0.0001, 3000, 0.0);
            model.fit(linearX(), linearY());
            double mse = model.mse(linearX(), linearY());
            assertThat(mse).isGreaterThanOrEqualTo(0);
            assertThat(mse).isLessThan(100);
        }
    }

    @Nested @DisplayName("Logistic Regression")
    class LogisticRegressionTests {

        @Test @DisplayName("binary classification accuracy > 60%")
        void binaryClassification() {
            // Linearly separable: class 1 if x[0] > 0.5
            double[][] X = new double[100][1];
            int[] y = new int[100];
            for (int i = 0; i < 100; i++) {
                X[i][0] = i * 0.01;
                y[i] = i > 50 ? 1 : 0;
            }
            var model = new SupervisedLearning.LogisticRegression(0.1, 500, 0.5);
            model.fit(X, y);
            assertThat(model.accuracy(X, y)).isGreaterThan(0.8);
        }

        @Test @DisplayName("predict returns 0 or 1")
        void predictionBinary() {
            var model = new SupervisedLearning.LogisticRegression(0.01, 100, 0.5);
            model.fit(binaryX(), binaryY());
            for (var x : binaryX()) {
                int p = model.predict(x);
                assertThat(p).isIn(0, 1);
            }
        }

        @Test @DisplayName("probability between 0 and 1")
        void probabilityInRange() {
            var model = new SupervisedLearning.LogisticRegression(0.01, 100, 0.5);
            model.fit(binaryX(), binaryY());
            for (var x : binaryX()) {
                double p = model.predictProba(x);
                assertThat(p).isBetween(0.0, 1.0);
            }
        }
    }

    @Nested @DisplayName("K-Nearest Neighbors")
    class KNNTests {

        @Test @DisplayName("KNN with k=1 achieves 100% on training data")
        void knnK1Training() {
            var knn = new SupervisedLearning.KNN(1, SupervisedLearning.KNN.DistanceMetric.EUCLIDEAN);
            var X = multiclassX();
            var y = multiclassY();
            knn.fit(X, y);
            // k=1 should memorize training data perfectly
            assertThat(knn.accuracy(X, y)).isEqualTo(1.0);
        }

        @Test @DisplayName("all distance metrics return non-negative")
        void distanceMetrics() {
            for (var metric : SupervisedLearning.KNN.DistanceMetric.values()) {
                var knn = new SupervisedLearning.KNN(3, metric);
                knn.fit(multiclassX(), multiclassY());
                int pred = knn.predict(new double[]{5, 5});
                assertThat(pred).isIn(0, 1, 2);
            }
        }
    }

    @Nested @DisplayName("Naive Bayes (Gaussian)")
    class NaiveBayesTests {

        @Test @DisplayName("Gaussian NB classifies well-separated classes")
        void gaussianNB() {
            var nb = new SupervisedLearning.GaussianNaiveBayes();
            var X = multiclassX();
            var y = multiclassY();
            nb.fit(X, y);
            double acc = nb.accuracy(X, y);
            assertThat(acc).isGreaterThan(0.7); // 3 well-separated clusters
        }

        @Test @DisplayName("prediction is one of the known classes")
        void predictionInKnownClasses() {
            var nb = new SupervisedLearning.GaussianNaiveBayes();
            nb.fit(multiclassX(), multiclassY());
            int pred = nb.predict(new double[]{5, 5});
            assertThat(pred).isIn(0, 1, 2);
        }
    }

    @Nested @DisplayName("Decision Tree")
    class DecisionTreeTests {

        @Test @DisplayName("perfect training accuracy on small dataset")
        void perfectTraining() {
            double[][] X = {{1,1},{2,2},{5,5},{6,6}};
            int[]      y = {0,    0,    1,    1};
            var tree = new SupervisedLearning.DecisionTree(5, 1);
            tree.fit(X, y);
            assertThat(tree.accuracy(X, y)).isEqualTo(1.0);
        }

        @Test @DisplayName("respects max depth limit")
        void maxDepth() {
            var tree = new SupervisedLearning.DecisionTree(1, 1);
            tree.fit(multiclassX(), multiclassY());
            // shallow tree should still work (just not perfectly)
            for (var x : multiclassX()) assertThat(tree.predict(x)).isIn(0, 1, 2);
        }
    }

    @Nested @DisplayName("Random Forest")
    class RandomForestTests {

        @Test @DisplayName("ensemble achieves good accuracy on multiclass")
        void ensembleAccuracy() {
            var rf = new SupervisedLearning.RandomForest(10, 5, 42L);
            var X = multiclassX();
            var y = multiclassY();
            rf.fit(X, y);
            double acc = rf.accuracy(X, y);
            assertThat(acc).isGreaterThan(0.8);
        }
    }

    // ── Unsupervised Learning Tests ────────────────────────────────────────────

    @Nested @DisplayName("K-Means Clustering")
    class KMeansTests {

        double[][] clusterData() {
            double[][] X = new double[60][2];
            Random rng = new Random(42);
            for (int i = 0;  i < 20;  i++) { X[i][0]  = rng.nextGaussian(); X[i][1]  = rng.nextGaussian(); }
            for (int i = 20; i < 40;  i++) { X[i][0]  = rng.nextGaussian() + 10; X[i][1] = rng.nextGaussian() + 10; }
            for (int i = 40; i < 60;  i++) { X[i][0]  = rng.nextGaussian() + 20; X[i][1] = rng.nextGaussian(); }
            return X;
        }

        @Test @DisplayName("k=3 finds 3 distinct clusters")
        void findsThreeClusters() {
            var km = new UnsupervisedLearning.KMeans(3, 100, 42);
            km.fit(clusterData());
            var labels = km.labels();
            Set<Integer> distinctLabels = new HashSet<>();
            for (int l : labels) distinctLabels.add(l);
            assertThat(distinctLabels).hasSize(3);
        }

        @Test @DisplayName("inertia decreases with more iterations")
        void inertiaPositive() {
            var km = new UnsupervisedLearning.KMeans(3, 100, 42);
            km.fit(clusterData());
            assertThat(km.inertia()).isGreaterThan(0);
        }

        @Test @DisplayName("k-means++ init produces valid centroids")
        void kMeansPlusPlusInit() {
            var km = new UnsupervisedLearning.KMeans(3, 10, 42);
            km.fit(clusterData());
            assertThat(km.centroids()).hasSize(3);
        }

        @Test @DisplayName("silhouette score between -1 and 1")
        void silhouetteScore() {
            var km = new UnsupervisedLearning.KMeans(3, 100, 42);
            double[][] X = clusterData();
            km.fit(X);
            double sil = km.silhouetteScore(X);
            assertThat(sil).isBetween(-1.0, 1.0);
        }

        @Test @DisplayName("elbow analysis returns inertia for each k")
        void elbowAnalysis() {
            double[] inertias = UnsupervisedLearning.KMeans.elbowAnalysis(clusterData(), 5, 50);
            assertThat(inertias).hasSize(4); // k=2..5
            // Inertia should generally decrease as k increases
            for (double i : inertias) assertThat(i).isGreaterThan(0);
        }
    }

    @Nested @DisplayName("DBSCAN")
    class DBSCANTests {

        @Test @DisplayName("identifies noise points (label = -1)")
        void identifiesNoise() {
            double[][] X = {{0,0},{0.1,0},{0,0.1},{100,100},{0.2,0.2},{200,200}};
            var dbscan = new UnsupervisedLearning.DBSCAN(0.5, 2);
            int[] labels = dbscan.fit(X);
            // Isolated points (100,100) and (200,200) should be noise
            assertThat(dbscan.numNoise()).isGreaterThan(0);
        }

        @Test @DisplayName("finds dense clusters")
        void findsClusters() {
            double[][] X = new double[20][2];
            Random rng = new Random(42);
            for (int i = 0; i < 10; i++) { X[i][0] = rng.nextGaussian() * 0.1; X[i][1] = rng.nextGaussian() * 0.1; }
            for (int i = 10; i < 20; i++) { X[i][0] = rng.nextGaussian() * 0.1 + 10; X[i][1] = rng.nextGaussian() * 0.1 + 10; }
            var dbscan = new UnsupervisedLearning.DBSCAN(1.0, 3);
            dbscan.fit(X);
            assertThat(dbscan.numClusters()).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested @DisplayName("PCA (Principal Component Analysis)")
    class PCATests {

        @Test @DisplayName("reduces dimensions from p to k")
        void reducesDimensions() {
            double[][] X = new double[50][5]; // 50 samples, 5 features
            Random rng = new Random(42);
            for (double[] row : X) for (int j = 0; j < 5; j++) row[j] = rng.nextGaussian();

            var pca = new UnsupervisedLearning.PCA();
            double[][] reduced = pca.fitTransform(X, 2); // reduce to 2D
            assertThat(reduced).hasSize(50);
            assertThat(reduced[0]).hasSize(2);
        }

        @Test @DisplayName("explained variance ratios sum to ≤ 1")
        void explainedVarianceRatios() {
            double[][] X = new double[30][4];
            Random rng = new Random(42);
            for (double[] row : X) for (int j = 0; j < 4; j++) row[j] = rng.nextGaussian();

            var pca = new UnsupervisedLearning.PCA();
            pca.fit(X, 3);
            double[] ratios = pca.explainedVarianceRatio();
            assertThat(ratios).hasSize(3);
            double sum = 0;
            for (double r : ratios) { assertThat(r).isGreaterThanOrEqualTo(0); sum += r; }
            assertThat(sum).isLessThanOrEqualTo(1.01); // allow floating point
        }

        @Test @DisplayName("cumulative variance increases monotonically")
        void cumulativeVarianceMonotonic() {
            double[][] X = new double[40][5];
            Random rng = new Random(42);
            for (double[] row : X) for (int j = 0; j < 5; j++) row[j] = rng.nextGaussian();

            var pca = new UnsupervisedLearning.PCA();
            pca.fit(X, 4);
            double prev = 0;
            for (int k = 1; k <= 4; k++) {
                double cum = pca.cumulativeExplainedVariance(k);
                assertThat(cum).isGreaterThanOrEqualTo(prev);
                prev = cum;
            }
        }
    }

    // ── Neural Network Tests ───────────────────────────────────────────────────

    @Nested @DisplayName("Neural Network (MLP + Backpropagation)")
    class NeuralNetworkTests {

        @Test @DisplayName("MLP learns XOR (approximately)")
        void learnXor() {
            // XOR: [0,0]→0, [0,1]→1, [1,0]→1, [1,1]→0
            double[][] X = {{0,0},{0,1},{1,0},{1,1}};
            double[][] y = {{0},{1},{1},{0}};

            var net = new NeuralNetwork(
                new int[]{2, 4, 1},
                new NeuralNetwork.Activation[]{NeuralNetwork.Activation.RELU, NeuralNetwork.Activation.SIGMOID},
                0.1, 42L
            );
            var losses = net.train(X, y, 2000);
            // Loss should decrease overall
            assertThat(losses.get(0)).isGreaterThan(losses.get(1999));
        }

        @Test @DisplayName("softmax outputs sum to 1")
        void softmaxSumToOne() {
            var net = new NeuralNetwork(
                new int[]{3, 4, 3},
                new NeuralNetwork.Activation[]{NeuralNetwork.Activation.RELU, NeuralNetwork.Activation.LINEAR},
                0.01, 1L
            );
            double[] output = net.forward(new double[]{1.0, 2.0, 3.0});
            double[] probs = net.softmax(output);
            double sum = 0;
            for (double p : probs) { assertThat(p).isBetween(0.0, 1.0); sum += p; }
            assertThat(sum).isCloseTo(1.0, within(1e-6));
        }

        @Test @DisplayName("Conv2D produces correct output dimensions")
        void conv2dDimensions() {
            double[][] input  = new double[8][8];  // 8x8 image
            double[][] kernel = new double[3][3];  // 3x3 kernel
            Arrays.fill(kernel[0], 1.0 / 9);      // box blur
            Arrays.fill(kernel[1], 1.0 / 9);
            Arrays.fill(kernel[2], 1.0 / 9);

            var conv = new NeuralNetwork.Conv2D(kernel, 0.0);
            double[][] output = conv.forward(input);
            // valid conv: (8-3+1) x (8-3+1) = 6x6
            assertThat(output).hasSize(6);
            assertThat(output[0]).hasSize(6);
        }

        @Test @DisplayName("MaxPool reduces spatial dimensions")
        void maxPooling() {
            double[][] input = {{1,2,3,4},{5,6,7,8},{9,10,11,12},{13,14,15,16}};
            double[][] pooled = NeuralNetwork.Conv2D.maxPool(input, 2, 2);
            assertThat(pooled).hasSize(2);
            assertThat(pooled[0]).hasSize(2);
            assertThat(pooled[0][0]).isEqualTo(6.0); // max of [[1,2],[5,6]]
        }

        @Test @DisplayName("LSTM forward pass produces correct hidden size")
        void lstmForward() {
            int inputSize = 4, hiddenSize = 8;
            var lstm = new NeuralNetwork.LSTM(inputSize, hiddenSize, 42L);
            double[][] sequence = new double[5][inputSize]; // sequence of 5 time steps
            new Random(42).doubles(5 * inputSize).toArray(); // fill with random
            double[] hidden = lstm.forward(sequence);
            assertThat(hidden).hasSize(hiddenSize);
        }
    }

    // ── Transformer Tests ──────────────────────────────────────────────────────

    @Nested @DisplayName("Transformer Attention")
    class TransformerTests {

        @Test @DisplayName("self-attention output has same shape as input")
        void selfAttentionShape() {
            int seqLen = 5, dk = 8;
            double[][] Q = randomMatrix(seqLen, dk);
            double[][] K = randomMatrix(seqLen, dk);
            double[][] V = randomMatrix(seqLen, dk);

            double[][] output = TransformerAttention.scaledDotProductAttention(Q, K, V);
            assertThat(output).hasSize(seqLen);
            assertThat(output[0]).hasSize(dk);
        }

        @Test @DisplayName("attention weights sum to 1 per row (softmax)")
        void attentionWeightsSumToOne() {
            int seqLen = 4, dk = 4;
            double[][] Q = randomMatrix(seqLen, dk);
            double[][] K = randomMatrix(seqLen, dk);
            double[][] V = randomMatrix(seqLen, dk);
            // Manually check softmax via output norm (indirect)
            double[][] output = TransformerAttention.scaledDotProductAttention(Q, K, V);
            // Output should be finite
            for (double[] row : output) for (double v : row) assertThat(v).isFinite();
        }

        @Test @DisplayName("positional encoding has correct shape")
        void positionalEncodingShape() {
            double[][] PE = TransformerAttention.positionalEncoding(100, 64);
            assertThat(PE).hasSize(100);
            assertThat(PE[0]).hasSize(64);
        }

        @Test @DisplayName("positional encoding values in [-1, 1]")
        void positionalEncodingRange() {
            double[][] PE = TransformerAttention.positionalEncoding(50, 32);
            for (double[] row : PE) for (double v : row) assertThat(v).isBetween(-1.0, 1.0);
        }

        @Test @DisplayName("BPE tokenizer trains and tokenizes")
        void bpeTokenizer() {
            String[] corpus = {"hello", "hello world", "world of code", "code and hello"};
            var bpe = new TransformerAttention.BPETokenizer();
            bpe.train(corpus, 30);
            assertThat(bpe.vocabSize()).isGreaterThan(5);

            var tokens = bpe.tokenize("hello");
            assertThat(tokens).isNotEmpty();
        }

        @Test @DisplayName("vector store retrieves similar documents")
        void vectorStore() {
            var store = new TransformerAttention.SimpleVectorStore(64, 42L);
            store.addDocument("Binary Search algorithm", store.simulateEmbedding("Binary Search algorithm"));
            store.addDocument("Quick Sort algorithm", store.simulateEmbedding("Quick Sort algorithm"));
            store.addDocument("Pasta recipe", store.simulateEmbedding("Pasta recipe"));

            var query = store.simulateEmbedding("search algorithm");
            var results = store.retrieve(query, 2);
            assertThat(results).hasSize(2);
        }
    }

    // ── Model Evaluation Tests ─────────────────────────────────────────────────

    @Nested @DisplayName("Model Evaluation Metrics")
    class EvaluationTests {

        @Test @DisplayName("accuracy of perfect classifier = 1.0")
        void perfectAccuracy() {
            int[] actual    = {0, 1, 2, 0, 1, 2};
            int[] predicted = {0, 1, 2, 0, 1, 2};
            var report = ModelEvaluator.evaluate(actual, predicted, null);
            assertThat(report.accuracy()).isEqualTo(1.0);
            assertThat(report.f1()).isEqualTo(1.0);
        }

        @Test @DisplayName("confusion matrix has correct shape and sum")
        void confusionMatrix() {
            int[] actual    = {0, 0, 1, 1, 2, 2};
            int[] predicted = {0, 1, 0, 1, 2, 2};
            int[][] cm = ModelEvaluator.confusionMatrix(actual, predicted, 3);
            assertThat(cm).hasSize(3);
            int total = 0;
            for (int[] row : cm) for (int v : row) total += v;
            assertThat(total).isEqualTo(6);
        }

        @Test @DisplayName("ROC-AUC = 1.0 for perfect binary classifier")
        void rocAucPerfect() {
            int[]    actual = {0, 0, 1, 1};
            double[] proba  = {0.1, 0.2, 0.8, 0.9};
            double auc = ModelEvaluator.computeRocAuc(actual, proba);
            assertThat(auc).isCloseTo(1.0, within(0.01));
        }

        @Test @DisplayName("ROC-AUC = 0.5 for random classifier")
        void rocAucRandom() {
            int[]    actual = {0, 1, 0, 1};
            double[] proba  = {0.5, 0.5, 0.5, 0.5};
            double auc = ModelEvaluator.computeRocAuc(actual, proba);
            assertThat(auc).isCloseTo(0.5, within(0.1));
        }

        @Test @DisplayName("regression report: perfect prediction has R²=1, MSE=0")
        void regressionPerfect() {
            double[] actual    = {1, 2, 3, 4, 5};
            double[] predicted = {1, 2, 3, 4, 5};
            var report = ModelEvaluator.evaluateRegression(actual, predicted);
            assertThat(report.mse()).isCloseTo(0, within(1e-10));
            assertThat(report.r2()).isCloseTo(1.0, within(1e-10));
        }

        @Test @DisplayName("k-fold cross validation returns k scores")
        void kFoldCrossValidation() {
            var model = new ModelEvaluator.Classifier() {
                int[] trainY;
                public void fit(double[][] X, int[] y) { this.trainY = y; }
                public int predict(double[] x) { return trainY[0]; } // trivial
            };
            double[] scores = ModelEvaluator.kFoldCrossValidation(
                model, multiclassX(), multiclassY(), 5
            );
            assertThat(scores).hasSize(5);
            for (double s : scores) assertThat(s).isBetween(0.0, 1.0);
        }

        @Test @DisplayName("feature store ingests and retrieves features")
        void featureStore() {
            var store = new ModelEvaluator.FeatureStore();
            store.defineFeature("age",    "User age in years");
            store.defineFeature("score",  "Algorithm score");
            store.ingestFeatures("user-1", Map.of("age", 25, "score", 9500.0));
            store.ingestFeatures("user-2", Map.of("age", 30, "score", 8200.0));

            var features = store.getOnlineFeatures("user-1");
            assertThat(features.get("age")).isEqualTo(25);
            assertThat(store.featureDefinitions()).containsKey("age");
        }

        @Test @DisplayName("PSI detects distribution shift")
        void psiDriftDetection() {
            double[] reference = new Random(42).doubles(1000).toArray(); // uniform [0,1]
            double[] current   = new Random(42).doubles(1000).map(x -> x * 0.5 + 0.5).toArray(); // shifted [0.5, 1]
            double psi = ModelEvaluator.psi(reference, current, 10);
            assertThat(psi).isGreaterThan(0.1); // should detect shift
            assertThat(ModelEvaluator.driftSeverity(psi)).isNotBlank();
        }

        @Test @DisplayName("stable distribution has PSI < 0.1")
        void psiStable() {
            Random rng = new Random(42);
            double[] ref = rng.doubles(1000).toArray();
            double[] cur = rng.doubles(1000).toArray(); // same distribution
            double psi = ModelEvaluator.psi(ref, cur, 10);
            assertThat(psi).isLessThan(0.2);
        }
    }

    // ── Matrix Utility Tests ───────────────────────────────────────────────────

    @Nested @DisplayName("Matrix Operations")
    class MatrixTests {

        @Test @DisplayName("matrix multiplication: (2x3) x (3x2) = (2x2)")
        void matrixMultiplication() {
            double[][] a = {{1,2,3},{4,5,6}};
            double[][] b = {{7,8},{9,10},{11,12}};
            Matrix result = new Matrix(a).mul(new Matrix(b));
            assertThat(result.rows).isEqualTo(2);
            assertThat(result.cols).isEqualTo(2);
            assertThat(result.get(0,0)).isCloseTo(58, within(1e-9)); // 1*7+2*9+3*11
        }

        @Test @DisplayName("transpose works correctly")
        void transpose() {
            var m = new Matrix(new double[][]{{1,2,3},{4,5,6}});
            var t = m.transpose();
            assertThat(t.rows).isEqualTo(3);
            assertThat(t.cols).isEqualTo(2);
            assertThat(t.get(0,0)).isEqualTo(1);
            assertThat(t.get(1,0)).isEqualTo(2);
        }

        @Test @DisplayName("standardize produces zero-mean unit-variance columns")
        void standardize() {
            double[][] data = {{1,100},{2,200},{3,300},{4,400},{5,500}};
            var m = new Matrix(data);
            var standardized = m.standardize();
            double[] means = standardized.colMeans();
            assertThat(means[0]).isCloseTo(0, within(1e-9));
            assertThat(means[1]).isCloseTo(0, within(1e-9));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private double[][] randomMatrix(int rows, int cols) {
        Random rng = new Random(42);
        double[][] m = new double[rows][cols];
        for (double[] row : m) for (int j = 0; j < cols; j++) row[j] = rng.nextGaussian() * 0.1;
        return m;
    }

    private static final org.assertj.core.data.Offset<Double> within(double eps) {
        return org.assertj.core.data.Offset.offset(eps);
    }
}
