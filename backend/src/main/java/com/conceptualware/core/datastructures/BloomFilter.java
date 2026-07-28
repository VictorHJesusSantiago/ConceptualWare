package com.conceptualware.core.datastructures;

import java.util.BitSet;
import java.util.function.ToIntFunction;

/**
 * Concept #5 — Bloom Filter: estrutura probabilística de pertencimento a conjunto.
 *   Sem falsos negativos, com falsos positivos controlados pela taxa configurada.
 *   Usa k funções hash independentes (double hashing: h_i(x) = h1(x) + i*h2(x)).
 */
public class BloomFilter<T> {

    private final BitSet bits;
    private final int bitSize;
    private final int numHashes;
    private final ToIntFunction<T> hash1;
    private final ToIntFunction<T> hash2;

    public BloomFilter(int expectedInsertions, double falsePositiveRate) {
        this.bitSize = optimalBitSize(expectedInsertions, falsePositiveRate);
        this.numHashes = optimalHashCount(expectedInsertions, bitSize);
        this.bits = new BitSet(bitSize);
        this.hash1 = t -> Math.abs(t.hashCode());
        this.hash2 = t -> Math.abs(smear(t.hashCode()));
    }

    private static int smear(int h) {
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    static int optimalBitSize(int n, double p) {
        return (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    static int optimalHashCount(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    public void add(T item) {
        int h1 = hash1.applyAsInt(item);
        int h2 = hash2.applyAsInt(item);
        for (int i = 0; i < numHashes; i++) {
            int combined = (h1 + i * h2);
            bits.set(Math.floorMod(combined, bitSize));
        }
    }

    /** true = "possivelmente presente"; false = "com certeza ausente". */
    public boolean mightContain(T item) {
        int h1 = hash1.applyAsInt(item);
        int h2 = hash2.applyAsInt(item);
        for (int i = 0; i < numHashes; i++) {
            int combined = (h1 + i * h2);
            if (!bits.get(Math.floorMod(combined, bitSize))) return false;
        }
        return true;
    }

    public int bitSize() {
        return bitSize;
    }

    public int hashCount() {
        return numHashes;
    }

    public double approxFillRatio() {
        return (double) bits.cardinality() / bitSize;
    }
}
