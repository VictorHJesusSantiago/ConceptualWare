package com.conceptualware.core.logic;

import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — TDD: Red-Green-Refactor cycle
 *   Teste unitário, AAA, Given-When-Then, Test isolation
 */
@DisplayName("Logic Engine — Boolean Algebra & Propositional Logic")
class LogicEngineTest {

    private LogicEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LogicEngine();
    }

    // ── Boolean Operators ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Boolean Operators")
    class BooleanOperatorTests {

        @Test void andTrueTrue()   { assertThat(engine.and(true, true)).isTrue(); }
        @Test void andTrueFalse()  { assertThat(engine.and(true, false)).isFalse(); }
        @Test void andFalseTrue()  { assertThat(engine.and(false, true)).isFalse(); }
        @Test void andFalseFalse() { assertThat(engine.and(false, false)).isFalse(); }

        @Test void orTrueTrue()    { assertThat(engine.or(true, true)).isTrue(); }
        @Test void orFalseFalse()  { assertThat(engine.or(false, false)).isFalse(); }

        @Test void notTrue()       { assertThat(engine.not(true)).isFalse(); }
        @Test void notFalse()      { assertThat(engine.not(false)).isTrue(); }

        @Test void xorSameValues() { assertThat(engine.xor(true, true)).isFalse(); }
        @Test void xorDiff()       { assertThat(engine.xor(true, false)).isTrue(); }

        @Test void nandFalseWhenBothTrue()  { assertThat(engine.nand(true, true)).isFalse(); }
        @Test void nandTrueWhenEitherFalse(){ assertThat(engine.nand(true, false)).isTrue(); }

        @Test void norTrueWhenBothFalse()   { assertThat(engine.nor(false, false)).isTrue(); }
        @Test void norFalseWhenAnyTrue()    { assertThat(engine.nor(true, false)).isFalse(); }
    }

    // ── Implication ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Implication (A → B)")
    class ImplicationTests {

        @Test void trueImpliesTrue()  { assertThat(engine.implies(true, true)).isTrue(); }
        @Test void trueImpliesFalse() { assertThat(engine.implies(true, false)).isFalse(); }
        @Test void falseImpliesAnything() {
            assertThat(engine.implies(false, true)).isTrue();
            assertThat(engine.implies(false, false)).isTrue();
        }
    }

    // ── Truth Table ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AND truth table should have exactly 4 rows")
    void andTruthTableHas4Rows() {
        List<LogicEngine.TruthTableRow> table = engine.buildTruthTable(LogicEngine.BooleanOperator.AND);
        assertThat(table).hasSize(4);
    }

    @Test
    @DisplayName("A AND A is NOT a tautology")
    void andIsNotTautology() {
        var table = engine.buildTruthTable(LogicEngine.BooleanOperator.AND);
        assertThat(engine.isTautology(table)).isFalse();
    }

    @Test
    @DisplayName("A OR NOT(A) is a tautology (law of excluded middle)")
    void aOrNotAIsTautology() {
        // Build truth table for A OR ¬A
        var table = engine.evaluateAllCombinations(
            new String[]{"A"},
            vals -> engine.or(vals[0], engine.not(vals[0]))
        );
        assertThat(engine.isTautology(table)).isTrue();
    }

    @Test
    @DisplayName("A AND NOT(A) is a contradiction")
    void aAndNotAIsContradiction() {
        var table = engine.evaluateAllCombinations(
            new String[]{"A"},
            vals -> engine.and(vals[0], engine.not(vals[0]))
        );
        assertThat(engine.isContradiction(table)).isTrue();
    }

    // ── Quantifiers ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("∀ x: x > 0 holds for {1,2,3}")
    void universalQuantifierHolds() {
        assertThat(engine.forAll(List.of(1, 2, 3), x -> x > 0)).isTrue();
    }

    @Test
    @DisplayName("∀ x: x > 5 fails for {1,2,3}")
    void universalQuantifierFails() {
        assertThat(engine.forAll(List.of(1, 2, 3), x -> x > 5)).isFalse();
    }

    @Test
    @DisplayName("∃ x: x > 2 holds for {1,2,3}")
    void existentialQuantifierHolds() {
        assertThat(engine.exists(List.of(1, 2, 3), x -> x > 2)).isTrue();
    }

    // ── Bitwise Operations ────────────────────────────────────────────────────

    @Test void bitwiseAnd()    { assertThat(engine.bitwiseAnd(0b1010, 0b1100)).isEqualTo(0b1000); }
    @Test void bitwiseOr()     { assertThat(engine.bitwiseOr(0b1010, 0b1100)).isEqualTo(0b1110); }
    @Test void bitwiseXor()    { assertThat(engine.bitwiseXor(0b1010, 0b1100)).isEqualTo(0b0110); }
    @Test void shiftLeft()     { assertThat(engine.shiftLeft(1, 3)).isEqualTo(8); }
    @Test void shiftRight()    { assertThat(engine.shiftRight(8, 3)).isEqualTo(1); }
    @Test void countSetBits()  { assertThat(engine.countSetBits(0b10110111)).isEqualTo(6); }

    // ── Integer Overflow ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should detect integer overflow")
    void shouldDetectOverflow() {
        assertThat(engine.willOverflow(Integer.MAX_VALUE, 1)).isTrue();
        assertThat(engine.willOverflow(1, 1)).isFalse();
    }
}
