package com.conceptualware.core.ml;

import java.util.*;

public class ModelEvaluator {

    public static int[][] confusionMatrix(int[] actual, int[] predicted, int numClasses) {
        int[][] cm = new int[numClasses][numClasses];
        for (int i = 0; i < actual.length; i++) cm[actual[i]][predicted[i]]++;
        return cm;
    }

    public static String formatConfusionMatrix(int[][] cm) {
        var sb = new StringBuilder("Confusion Matrix (rows=actual, cols=predicted):\n");
        for (int[] row : cm) {
            for (int v : row) sb.append(String.format("%6d", v));
            sb.append("\n");
        }
        return sb.toString();
    }

    public record ClassificationReport(
        double accuracy, double precision, double recall, double f1,
        double rocAuc, double mcc, int[][] confusionMatrix
    ) {
        @Override public String toString() {
            return """
                ═══════════════════════════════════════
                  Classification Report
                ═══════════════════════════════════════
                  Accuracy:  %.4f
                  Precision: %.4f (macro avg)
                  Recall:    %.4f (macro avg)
                  F1 Score:  %.4f (macro avg)
                  ROC AUC:   %.4f
                  MCC:       %.4f
                ═══════════════════════════════════════
                """.formatted(accuracy, precision, recall, f1, rocAuc, mcc);
        }
    }

    public static ClassificationReport evaluate(int[] actual, int[] predicted, double[] probabilities) {
        int n = actual.length;
        int numClasses = Arrays.stream(actual).max().orElse(0) + 1;
        int[][] cm = confusionMatrix(actual, predicted, numClasses);

        double[] tp = new double[numClasses], fp = new double[numClasses], fn = new double[numClasses];
        for (int c = 0; c < numClasses; c++) {
            for (int i = 0; i < numClasses; i++) {
                if (i == c) tp[c] = cm[c][c];
                else { fp[c] += cm[i][c]; fn[c] += cm[c][i]; }
            }
        }

        double precisionSum = 0, recallSum = 0, f1Sum = 0;
        for (int c = 0; c < numClasses; c++) {
            double p = tp[c] + fp[c] == 0 ? 0 : tp[c] / (tp[c] + fp[c]);
            double r = tp[c] + fn[c] == 0 ? 0 : tp[c] / (tp[c] + fn[c]);
            precisionSum += p; recallSum += r;
            f1Sum += (p + r == 0) ? 0 : 2 * p * r / (p + r);
        }

        double accuracy  = (double) Arrays.stream(cm).mapToInt(r -> r[Arrays.stream(r).max().getAsInt()]).sum() / n;
        accuracy = 0;
        for (int i = 0; i < n; i++) if (actual[i] == predicted[i]) accuracy++;
        accuracy /= n;

        double precision = precisionSum / numClasses;
        double recall    = recallSum    / numClasses;
        double f1        = f1Sum        / numClasses;

        double rocAuc = probabilities != null ? computeRocAuc(actual, probabilities) : Double.NaN;

        double mcc = numClasses == 2 ? computeMCC(cm) : Double.NaN;

        return new ClassificationReport(accuracy, precision, recall, f1, rocAuc, mcc, cm);
    }

    public static double computeRocAuc(int[] actual, double[] proba) {
        Integer[] indices = new Integer[actual.length];
        for (int i = 0; i < actual.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(proba[b], proba[a]));

        int pos = (int) Arrays.stream(actual).filter(v -> v == 1).count();
        int neg = actual.length - pos;
        if (pos == 0 || neg == 0) return Double.NaN;

        double auc = 0, tpPrev = 0, fpPrev = 0, tp = 0, fp = 0;
        int i = 0;
        while (i < indices.length) {
            int j = i;
            double currentProba = proba[indices[i]];
            while (j < indices.length && proba[indices[j]] == currentProba) {
                if (actual[indices[j]] == 1) tp++;
                else                         fp++;
                j++;
            }
            double tpr = tp / pos;
            double fpr = fp / neg;
            auc += (fpr - fpPrev/neg) * (tpr + tpPrev/pos) / 2;
            tpPrev = tp; fpPrev = fp;
            i = j;
        }
        return auc;
    }

    public static double[][] rocCurve(int[] actual, double[] proba, int steps) {
        double[] fprs = new double[steps + 1];
        double[] tprs = new double[steps + 1];
        int pos = (int) Arrays.stream(actual).filter(v -> v == 1).count();
        int neg = actual.length - pos;

        for (int s = 0; s <= steps; s++) {
            double threshold = (double) s / steps;
            int tp = 0, fp = 0;
            for (int i = 0; i < actual.length; i++) {
                if (proba[i] >= threshold) { if (actual[i] == 1) tp++; else fp++; }
            }
            fprs[s] = (double) fp / neg;
            tprs[s] = (double) tp / pos;
        }
        return new double[][]{fprs, tprs};
    }

    private static double computeMCC(int[][] cm) {
        double tp = cm[1][1], tn = cm[0][0], fp = cm[0][1], fn = cm[1][0];
        double denom = Math.sqrt((tp+fp)*(tp+fn)*(tn+fp)*(tn+fn));
        return denom == 0 ? 0 : (tp*tn - fp*fn) / denom;
    }

    public record RegressionReport(double mse, double rmse, double mae, double mape, double r2) {
        @Override public String toString() {
            return """
                ═══════════════════════════════════════
                  Regression Report
                ═══════════════════════════════════════
                  MSE:   %.6f
                  RMSE:  %.6f
                  MAE:   %.6f
                  MAPE:  %.2f%%
                  R²:    %.4f
                ═══════════════════════════════════════
                """.formatted(mse, rmse, mae, mape * 100, r2);
        }
    }

    public static RegressionReport evaluateRegression(double[] actual, double[] predicted) {
        int n = actual.length;
        double mean = Arrays.stream(actual).average().orElse(0);
        double sse = 0, ssTot = 0, mse = 0, mae = 0, mape = 0;
        for (int i = 0; i < n; i++) {
            double err = predicted[i] - actual[i];
            mse   += err * err;
            mae   += Math.abs(err);
            mape  += actual[i] != 0 ? Math.abs(err / actual[i]) : 0;
            sse   += err * err;
            ssTot += Math.pow(actual[i] - mean, 2);
        }
        mse /= n; mae /= n; mape /= n;
        return new RegressionReport(mse, Math.sqrt(mse), mae, mape, ssTot == 0 ? 1 : 1 - sse/ssTot);
    }

    public interface Classifier {
        void fit(double[][] X, int[] y);
        int predict(double[] x);
    }

    public static double[] kFoldCrossValidation(Classifier model, double[][] X, int[] y, int k) {
        int n = X.length;
        double[] scores = new double[k];
        int foldSize = n / k;

        for (int fold = 0; fold < k; fold++) {
            int testStart = fold * foldSize;
            int testEnd   = (fold == k - 1) ? n : testStart + foldSize;

            List<Integer> trainIdx = new ArrayList<>(), testIdx = new ArrayList<>();
            for (int i = 0; i < n; i++) (i >= testStart && i < testEnd ? testIdx : trainIdx).add(i);

            double[][] trainX = trainIdx.stream().map(i -> X[i]).toArray(double[][]::new);
            int[]      trainY = trainIdx.stream().mapToInt(i -> y[i]).toArray();
            double[][] testX  = testIdx.stream().map(i -> X[i]).toArray(double[][]::new);
            int[]      testY  = testIdx.stream().mapToInt(i -> y[i]).toArray();

            model.fit(trainX, trainY);
            int correct = 0;
            for (int i = 0; i < testX.length; i++) if (model.predict(testX[i]) == testY[i]) correct++;
            scores[fold] = (double) correct / testX.length;
        }
        return scores;
    }

    public static class FeatureStore {
        private final Map<String, Map<String, Object>> onlineStore = new HashMap<>();
        private final List<Map<String, Object>> offlineStore = new ArrayList<>();
        private final Map<String, String> featureDefinitions = new LinkedHashMap<>();

        public void defineFeature(String name, String description) {
            featureDefinitions.put(name, description);
        }

        public void ingestFeatures(String entityId, Map<String, Object> features) {
            onlineStore.put(entityId, new HashMap<>(features));
            Map<String, Object> record = new HashMap<>(features);
            record.put("entity_id", entityId);
            record.put("timestamp", System.currentTimeMillis());
            offlineStore.add(record);
        }

        public Map<String, Object> getOnlineFeatures(String entityId) {
            return onlineStore.getOrDefault(entityId, Map.of());
        }

        public List<Map<String, Object>> getHistoricalFeatures(String entityId) {
            return offlineStore.stream().filter(r -> entityId.equals(r.get("entity_id"))).toList();
        }

        public Map<String, String> featureDefinitions() { return Map.copyOf(featureDefinitions); }
    }

    public static double psi(double[] reference, double[] current, int bins) {
        double min = Math.min(min(reference), min(current));
        double max = Math.max(max(reference), max(current));
        double width = (max - min) / bins;

        double[] refBins = new double[bins], curBins = new double[bins];
        for (double v : reference) refBins[Math.min((int)((v-min)/width), bins-1)]++;
        for (double v : current)   curBins[Math.min((int)((v-min)/width), bins-1)]++;

        double psi = 0;
        for (int i = 0; i < bins; i++) {
            double refPct = (refBins[i] + 1e-10) / reference.length;
            double curPct = (curBins[i] + 1e-10) / current.length;
            psi += (curPct - refPct) * Math.log(curPct / refPct);
        }
        return psi;
    }

    public static String driftSeverity(double psi) {
        if (psi < 0.1)  return "STABLE (PSI=%.4f)".formatted(psi);
        if (psi < 0.2)  return "SLIGHT_SHIFT (PSI=%.4f) — monitor".formatted(psi);
        return "SIGNIFICANT_DRIFT (PSI=%.4f) — retrain required".formatted(psi);
    }

    private static double min(double[] a) { return Arrays.stream(a).min().orElse(0); }
    private static double max(double[] a) { return Arrays.stream(a).max().orElse(0); }
}
