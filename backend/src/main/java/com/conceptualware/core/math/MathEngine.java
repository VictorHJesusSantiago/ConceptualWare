package com.conceptualware.core.math;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.IntStream;

/**
 * MathEngine — mathematical foundations for computer science.
 *
 * Covers Concept #28:
 *   - Sistemas de numeração (binário, octal, decimal, hexadecimal)
 *   - IEEE 754, overflow, rounding errors
 *   - Teoria dos números: MDC, MMC, primos, aritmética modular
 *   - Combinatória: permutação, combinação, arranjo
 *   - Probabilidade & estatística
 *   - Teoria dos grafos (matemática)
 *   - Teoria da informação: entropia de Shannon
 *   - Código de Hamming
 *   - Autômatos, P vs NP (conceitos)
 *   - Descida do gradiente (para ML — Concept #30)
 */
@Component
public class MathEngine {

    // ─────────────────────────────────────────────────────────────────────────
    // §1 — Sistemas de Numeração
    // ─────────────────────────────────────────────────────────────────────────

    public String decimalToBinary(long n)  { return Long.toBinaryString(n); }
    public String decimalToOctal(long n)   { return Long.toOctalString(n); }
    public String decimalToHex(long n)     { return Long.toHexString(n).toUpperCase(); }

    public long binaryToDecimal(String bin)  { return Long.parseLong(bin, 2); }
    public long octalToDecimal(String oct)   { return Long.parseLong(oct, 8); }
    public long hexToDecimal(String hex)     { return Long.parseLong(hex, 16); }

    // Convert between any two bases
    public String convertBase(String number, int fromBase, int toBase) {
        long decimal = Long.parseLong(number, fromBase);
        return Long.toString(decimal, toBase).toUpperCase();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §2 — IEEE 754 Ponto Flutuante
    // ─────────────────────────────────────────────────────────────────────────

    /** Demonstrates floating-point rounding error (IEEE 754). */
    public boolean floatingPointError() {
        double result = 0.1 + 0.2; // Should be 0.3 but isn't in IEEE 754
        return result == 0.3; // false — this is the classic FP error
    }

    /** Safe float comparison using epsilon. */
    public boolean floatEquals(double a, double b, double epsilon) {
        return Math.abs(a - b) < epsilon;
    }

    public String ieee754Representation(float f) {
        int bits = Float.floatToIntBits(f);
        int sign = (bits >>> 31) & 1;
        int exponent = (bits >>> 23) & 0xFF;
        int mantissa = bits & 0x7FFFFF;
        return String.format("sign=%d exp=%d(%d) mantissa=%s",
            sign, exponent, exponent - 127, Integer.toBinaryString(mantissa));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §3 — Teoria dos Números
    // ─────────────────────────────────────────────────────────────────────────

    /** Greatest Common Divisor — Euclidean Algorithm. */
    public long gcd(long a, long b) {
        while (b != 0) { long t = b; b = a % b; a = t; }
        return a;
    }

    /** Least Common Multiple. */
    public long lcm(long a, long b) {
        return a / gcd(a, b) * b;
    }

    /** Sieve of Eratosthenes — find all primes up to n. */
    public List<Integer> sieveOfEratosthenes(int n) {
        boolean[] isComposite = new boolean[n + 1];
        List<Integer> primes = new ArrayList<>();
        for (int i = 2; i <= n; i++) {
            if (!isComposite[i]) {
                primes.add(i);
                for (long j = (long) i * i; j <= n; j += i) {
                    isComposite[(int) j] = true;
                }
            }
        }
        return primes;
    }

    public boolean isPrime(long n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (long i = 3; i * i <= n; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }

    /** Fast modular exponentiation: base^exp mod m. */
    public long modPow(long base, long exp, long mod) {
        long result = 1;
        base %= mod;
        while (exp > 0) {
            if ((exp & 1) == 1) result = result * base % mod;
            base = base * base % mod;
            exp >>= 1;
        }
        return result;
    }

    /** Modular inverse using Fermat's little theorem (mod must be prime). */
    public long modInverse(long a, long mod) {
        return modPow(a, mod - 2, mod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §4 — Combinatória
    // ─────────────────────────────────────────────────────────────────────────

    public long factorial(int n) {
        if (n < 0) throw new IllegalArgumentException("Factorial of negative");
        long result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }

    /** C(n,k) — combination (binomial coefficient). */
    public long combination(int n, int k) {
        if (k > n || k < 0) return 0;
        if (k == 0 || k == n) return 1;
        k = Math.min(k, n - k); // symmetry
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /** P(n,k) — permutation. */
    public long permutation(int n, int k) {
        if (k > n) return 0;
        long result = 1;
        for (int i = n; i > n - k; i--) result *= i;
        return result;
    }

    /** Inclusion-exclusion principle: |A ∪ B| = |A| + |B| - |A ∩ B|. */
    public int inclusionExclusion(int sizeA, int sizeB, int sizeIntersection) {
        return sizeA + sizeB - sizeIntersection;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §5 — Estatística
    // ─────────────────────────────────────────────────────────────────────────

    public record Statistics(double mean, double median, double mode,
                              double variance, double stdDev, double min, double max) {}

    public Statistics computeStatistics(double[] data) {
        if (data == null || data.length == 0)
            throw new IllegalArgumentException("Empty dataset");

        double sum = 0, min = data[0], max = data[0];
        for (double d : data) { sum += d; min = Math.min(min, d); max = Math.max(max, d); }
        double mean = sum / data.length;

        double[] sorted = data.clone();
        Arrays.sort(sorted);
        double median = sorted.length % 2 == 0
            ? (sorted[sorted.length/2 - 1] + sorted[sorted.length/2]) / 2.0
            : sorted[sorted.length/2];

        double varianceSum = 0;
        for (double d : data) varianceSum += (d - mean) * (d - mean);
        double variance = varianceSum / data.length;

        // Mode: value that appears most
        Map<Double, Long> freq = new HashMap<>();
        for (double d : data) freq.merge(d, 1L, Long::sum);
        double mode = freq.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(mean);

        return new Statistics(mean, median, mode, variance, Math.sqrt(variance), min, max);
    }

    /** Pearson correlation coefficient. */
    public double correlation(double[] x, double[] y) {
        if (x.length != y.length) throw new IllegalArgumentException("Arrays must have same length");
        Statistics sx = computeStatistics(x);
        Statistics sy = computeStatistics(y);
        double cov = 0;
        for (int i = 0; i < x.length; i++) cov += (x[i] - sx.mean()) * (y[i] - sy.mean());
        cov /= x.length;
        return cov / (sx.stdDev() * sy.stdDev());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §6 — Teoria da Informação: Entropia de Shannon
    // ─────────────────────────────────────────────────────────────────────────

    /** Shannon entropy H(X) = -Σ p(x) * log2(p(x)). */
    public double shannonEntropy(Map<String, Double> probabilities) {
        double entropy = 0;
        for (double p : probabilities.values()) {
            if (p > 0) entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    /** Compute symbol frequencies and normalize to probabilities. */
    public Map<Character, Double> computeProbabilities(String text) {
        Map<Character, Integer> freq = new LinkedHashMap<>();
        for (char c : text.toCharArray()) freq.merge(c, 1, Integer::sum);
        int total = text.length();
        Map<Character, Double> probs = new LinkedHashMap<>();
        freq.forEach((k, v) -> probs.put(k, (double) v / total));
        return probs;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §7 — Código de Hamming (detecção e correção de erros)
    // ─────────────────────────────────────────────────────────────────────────

    /** Encode data bits with Hamming error-correction parity bits. */
    public int[] hammingEncode(int[] dataBits) {
        int m = dataBits.length;
        int r = 0;
        while ((1 << r) < m + r + 1) r++;

        int totalBits = m + r;
        int[] encoded = new int[totalBits + 1]; // 1-indexed
        int dataIdx = 0;

        for (int i = 1; i <= totalBits; i++) {
            if ((i & (i - 1)) != 0) { // Not a power of 2 → data bit
                encoded[i] = dataBits[dataIdx++];
            }
        }
        for (int i = 0; (1 << i) <= totalBits; i++) {
            int parityPos = 1 << i;
            int parity = 0;
            for (int j = 1; j <= totalBits; j++) {
                if ((j & parityPos) != 0) parity ^= encoded[j];
            }
            encoded[parityPos] = parity;
        }
        return encoded;
    }

    /** Detect and correct single-bit error in Hamming-encoded word. */
    public int detectAndCorrect(int[] encoded) {
        int syndrome = 0;
        for (int i = 0; (1 << i) < encoded.length; i++) {
            int parityPos = 1 << i;
            int parity = 0;
            for (int j = 1; j < encoded.length; j++) {
                if ((j & parityPos) != 0) parity ^= encoded[j];
            }
            if (parity != 0) syndrome += parityPos;
        }
        if (syndrome > 0 && syndrome < encoded.length) {
            encoded[syndrome] ^= 1; // Flip the erroneous bit
        }
        return syndrome;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §8 — Descida do Gradiente (Concept 30 — ML)
    // ─────────────────────────────────────────────────────────────────────────

    public record GradientDescentResult(double[] weights, double finalLoss, int iterations) {}

    /** Simple gradient descent for linear regression: y = w0 + w1*x. */
    public GradientDescentResult gradientDescent(double[] x, double[] y,
                                                   double learningRate, int maxIterations,
                                                   double tolerance) {
        double w0 = 0, w1 = 0;
        int n = x.length;
        double prevLoss = Double.MAX_VALUE;
        int iter = 0;

        for (; iter < maxIterations; iter++) {
            double grad0 = 0, grad1 = 0, loss = 0;
            for (int i = 0; i < n; i++) {
                double prediction = w0 + w1 * x[i];
                double error = prediction - y[i];
                grad0 += error;
                grad1 += error * x[i];
                loss += error * error;
            }
            loss /= n;
            grad0 /= n;
            grad1 /= n;
            w0 -= learningRate * grad0;
            w1 -= learningRate * grad1;
            if (Math.abs(prevLoss - loss) < tolerance) break;
            prevLoss = loss;
        }
        return new GradientDescentResult(new double[]{w0, w1}, prevLoss, iter);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §9 — Álgebra Linear (base para ML/IA — Concept 28 & 30)
    // ─────────────────────────────────────────────────────────────────────────

    /** Matrix multiplication: C = A × B. */
    public double[][] matMul(double[][] a, double[][] b) {
        int rows = a.length, cols = b[0].length, inner = b.length;
        double[][] c = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int k = 0; k < inner; k++)
                for (int j = 0; j < cols; j++)
                    c[i][j] += a[i][k] * b[k][j];
        return c;
    }

    /** Dot product of two vectors. */
    public double dotProduct(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    /** L2 norm (Euclidean) of a vector. */
    public double norm(double[] v) {
        double sum = 0;
        for (double d : v) sum += d * d;
        return Math.sqrt(sum);
    }

    /** Cosine similarity between two vectors. */
    public double cosineSimilarity(double[] a, double[] b) {
        return dotProduct(a, b) / (norm(a) * norm(b));
    }
}
