package com.conceptualware.core.datastructures;

import com.conceptualware.core.datastructures.tree.FenwickTree;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FenwickTreePropertyTest {

    @Property
    void rangeSumMatchesNaiveSumForAnyRange(
            @ForAll("boundedValues") long[] values,
            @ForAll("validRangePairs") int[] range
    ) {
        int a = Math.floorMod(range[0], values.length);
        int b = Math.floorMod(range[1], values.length);
        int l = Math.min(a, b);
        int r = Math.max(a, b);

        FenwickTree bit = FenwickTree.fromArray(values);

        long naive = 0;
        for (int i = l; i <= r; i++) naive += values[i];

        assertEquals(naive, bit.rangeSum(l, r),
            "rangeSum(" + l + "," + r + ") deveria bater com soma ingênua para values=" + java.util.Arrays.toString(values));
    }

    @Property
    void prefixSumIsMonotonicNonDecreasingForNonNegativeValues(
            @ForAll("nonNegativeValues") long[] values
    ) {
        FenwickTree bit = FenwickTree.fromArray(values);
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < values.length; i++) {
            long sum = bit.prefixSum(i);
            assertTrue(sum >= previous,
                "prefixSum deve ser não-decrescente para valores não-negativos");
            previous = sum;
        }
    }

    @Provide
    Arbitrary<long[]> boundedValues() {
        return Arbitraries.longs().between(-1_000_000, 1_000_000)
            .array(long[].class).ofMinSize(1).ofMaxSize(200);
    }

    @Provide
    Arbitrary<long[]> nonNegativeValues() {
        return Arbitraries.longs().between(0, 1_000_000)
            .array(long[].class).ofMinSize(1).ofMaxSize(100);
    }

    @Provide
    Arbitrary<int[]> validRangePairs() {
        return Arbitraries.integers().between(0, 1_000_000).array(int[].class).ofSize(2);
    }
}
