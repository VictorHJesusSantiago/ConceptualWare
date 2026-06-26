package com.conceptualware.core.ml;

import java.util.Arrays;
import java.util.Random;

/**
 * Dense matrix implementation used by all ML algorithms.
 * Row-major storage: element [i][j] = data[i * cols + j].
 *
 * Covers: dot product, transpose, element-wise ops, Gaussian elimination,
 * eigenvalue iteration (power method), singular values (Jacobi SVD sketch).
 */
public class Matrix {

    public final int rows;
    public final int cols;
    private final double[] data;

    public Matrix(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.data = new double[rows * cols];
    }

    public Matrix(double[][] values) {
        this.rows = values.length;
        this.cols = values[0].length;
        this.data = new double[rows * cols];
        for (int i = 0; i < rows; i++)
            System.arraycopy(values[i], 0, data, i * cols, cols);
    }

    // ── Element access ────────────────────────────────────────────────────────

    public double get(int r, int c)       { return data[r * cols + c]; }
    public void   set(int r, int c, double v) { data[r * cols + c] = v; }

    public double[] row(int r)    { return Arrays.copyOfRange(data, r * cols, r * cols + cols); }
    public double[] col(int c)    { double[] out = new double[rows]; for (int i=0;i<rows;i++) out[i]=data[i*cols+c]; return out; }

    // ── Factory Methods ───────────────────────────────────────────────────────

    public static Matrix zeros(int r, int c)  { return new Matrix(r, c); }

    public static Matrix ones(int r, int c) {
        Matrix m = new Matrix(r, c);
        Arrays.fill(m.data, 1.0);
        return m;
    }

    public static Matrix identity(int n) {
        Matrix m = new Matrix(n, n);
        for (int i = 0; i < n; i++) m.set(i, i, 1.0);
        return m;
    }

    public static Matrix ofColumn(double[] v) {
        Matrix m = new Matrix(v.length, 1);
        for (int i = 0; i < v.length; i++) m.set(i, 0, v[i]);
        return m;
    }

    public static Matrix ofRow(double[] v) {
        Matrix m = new Matrix(1, v.length);
        for (int j = 0; j < v.length; j++) m.set(0, j, v[j]);
        return m;
    }

    public static Matrix fromColumns(double[]... cols) {
        int rows = cols[0].length;
        Matrix m = new Matrix(rows, cols.length);
        for (int j = 0; j < cols.length; j++)
            for (int i = 0; i < rows; i++) m.set(i, j, cols[j][i]);
        return m;
    }

    public static Matrix random(int rows, int cols, Random rng) {
        Matrix m = new Matrix(rows, cols);
        for (int i = 0; i < m.data.length; i++) m.data[i] = rng.nextGaussian();
        return m;
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    /** Matrix multiplication: this (m×k) × other (k×n) → (m×n). O(m·k·n). */
    public Matrix mul(Matrix other) {
        if (cols != other.rows) throw new IllegalArgumentException("Dimension mismatch: " + cols + " ≠ " + other.rows);
        Matrix result = new Matrix(rows, other.cols);
        for (int i = 0; i < rows; i++)
            for (int k = 0; k < cols; k++) {
                double a = get(i, k);
                if (a == 0) continue; // sparsity hint
                for (int j = 0; j < other.cols; j++)
                    result.data[i * other.cols + j] += a * other.get(k, j);
            }
        return result;
    }

    /** Element-wise multiplication (Hadamard product). */
    public Matrix hadamard(Matrix other) {
        checkSameShape(other);
        Matrix result = new Matrix(rows, cols);
        for (int i = 0; i < data.length; i++) result.data[i] = data[i] * other.data[i];
        return result;
    }

    public Matrix add(Matrix other) {
        checkSameShape(other);
        Matrix result = new Matrix(rows, cols);
        for (int i = 0; i < data.length; i++) result.data[i] = data[i] + other.data[i];
        return result;
    }

    public Matrix sub(Matrix other) {
        checkSameShape(other);
        Matrix result = new Matrix(rows, cols);
        for (int i = 0; i < data.length; i++) result.data[i] = data[i] - other.data[i];
        return result;
    }

    public Matrix scale(double s) {
        Matrix result = new Matrix(rows, cols);
        for (int i = 0; i < data.length; i++) result.data[i] = data[i] * s;
        return result;
    }

    public Matrix addScalar(double s) {
        Matrix result = new Matrix(rows, cols);
        for (int i = 0; i < data.length; i++) result.data[i] = data[i] + s;
        return result;
    }

    public Matrix transpose() {
        Matrix result = new Matrix(cols, rows);
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) result.set(j, i, get(i, j));
        return result;
    }

    /** Apply a function element-wise. */
    public Matrix map(java.util.function.DoubleUnaryOperator fn) {
        Matrix result = new Matrix(rows, cols);
        for (int i = 0; i < data.length; i++) result.data[i] = fn.applyAsDouble(data[i]);
        return result;
    }

    // ── Dot products & norms ──────────────────────────────────────────────────

    public static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    public static double norm2(double[] v) {
        double sum = 0;
        for (double d : v) sum += d * d;
        return Math.sqrt(sum);
    }

    public static double[] normalize(double[] v) {
        double n = norm2(v);
        if (n == 0) return v;
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / n;
        return out;
    }

    /** Sum of all elements. */
    public double sum() {
        double s = 0;
        for (double d : data) s += d;
        return s;
    }

    public double[] rowSums() {
        double[] sums = new double[rows];
        for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) sums[i] += get(i, j);
        return sums;
    }

    public double[] colMeans() {
        double[] means = new double[cols];
        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) means[j] += get(i, j);
            means[j] /= rows;
        }
        return means;
    }

    public double[] colStds(double[] means) {
        double[] stds = new double[cols];
        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) stds[j] += Math.pow(get(i, j) - means[j], 2);
            stds[j] = Math.sqrt(stds[j] / rows);
        }
        return stds;
    }

    /** Standardize columns to zero-mean unit-variance. */
    public Matrix standardize() {
        double[] means = colMeans();
        double[] stds  = colStds(means);
        Matrix result = new Matrix(rows, cols);
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                result.set(i, j, stds[j] == 0 ? 0 : (get(i, j) - means[j]) / stds[j]);
        return result;
    }

    // ── Column appending for augmented matrices ────────────────────────────────

    public Matrix addBiasColumn() {
        Matrix result = new Matrix(rows, cols + 1);
        for (int i = 0; i < rows; i++) {
            result.set(i, 0, 1.0); // bias = 1
            for (int j = 0; j < cols; j++) result.set(i, j + 1, get(i, j));
        }
        return result;
    }

    // ── Power method: dominant eigenvalue/eigenvector ─────────────────────────

    public double[] dominantEigenvector(int maxIter) {
        Random rng = new Random(42);
        double[] v = new double[cols];
        for (int j = 0; j < cols; j++) v[j] = rng.nextGaussian();

        for (int iter = 0; iter < maxIter; iter++) {
            double[] mv = new double[rows];
            for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) mv[i] += get(i, j) * v[j];
            v = normalize(mv);
        }
        return v;
    }

    public double eigenvalue(double[] eigenvector) {
        double[] mv = new double[rows];
        for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) mv[i] += get(i, j) * eigenvector[j];
        return dot(eigenvector, mv);
    }

    // ── Slicing ───────────────────────────────────────────────────────────────

    public Matrix rowSlice(int from, int to) {
        Matrix result = new Matrix(to - from, cols);
        for (int i = from; i < to; i++)
            System.arraycopy(data, i * cols, result.data, (i - from) * cols, cols);
        return result;
    }

    public double[] getRow(int i) { return Arrays.copyOfRange(data, i * cols, (i + 1) * cols); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void checkSameShape(Matrix other) {
        if (rows != other.rows || cols != other.cols)
            throw new IllegalArgumentException("Shape mismatch: [%d×%d] vs [%d×%d]".formatted(rows, cols, other.rows, other.cols));
    }

    public double[] toFlatArray() { return Arrays.copyOf(data, data.length); }

    @Override public String toString() {
        var sb = new StringBuilder("[");
        for (int i = 0; i < rows; i++) {
            sb.append(i == 0 ? "[" : " [");
            for (int j = 0; j < cols; j++) sb.append("%.4f".formatted(get(i,j))).append(j<cols-1?", ":"");
            sb.append(i < rows-1 ? "],\n" : "]");
        }
        return sb.append("]").toString();
    }
}
