package com.conceptualware.core.datastructures;

import com.conceptualware.core.datastructures.tree.FenwickTree;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concept #19 — Property-Based Testing (jqwik): em vez de exemplos fixos,
 * declaramos uma propriedade que deve valer para QUALQUER entrada válida —
 * jqwik gera centenas de casos aleatórios (incluindo edge cases: array vazio,
 * valores negativos, tamanho 1) automaticamente.
 */
class FenwickTreePropertyTest {

    @Property
    void rangeSumMatchesNaiveSumForAnyRange(
            @ForAll("boundedValues") long[] values,
            @ForAll("validRangePairs") int[] range
    ) {
        // Aplicar '%' ANTES de escolher min/max — fazer o inverso (min/max primeiro,
        // '%' depois) quebra a ordenação, pois o módulo não preserva relação de ordem
        // entre dois números diferentes (ex.: 347 % 27 pode ser maior que 774766 % 27).
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

    // Ranges explícitos via @Provide — anotações @LongRange empilhadas em long[]
    // não restringem elementos de forma confiável em todas as versões do jqwik
    // (edge-case injection pode ainda gerar Long.MAX_VALUE/MIN_VALUE). Um
    // Arbitrary construído explicitamente é inequívoco.

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
