package com.conceptualware.core.control;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — TDD: Red-Green-Refactor, AAA, Given-When-Then
 * Concept #2  — Estruturas de controle: if/else, switch, ternary, for, while,
 *               do-while, foreach, nested loop, break, continue,
 *               labeled break (goto), assert, try/catch/finally,
 *               multiple exceptions, pattern matching switch
 */
@DisplayName("ControlFlowEngine — All Control Structures")
class ControlFlowEngineTest {

    private ControlFlowEngine engine;

    @BeforeEach
    void setUp() { engine = new ControlFlowEngine(); }

    // ── §1  if/else if/else ──────────────────────────────────────────────────

    @Test void classifiesFreezingTemperature()    { assertThat(engine.classifyTemperature(-5)).isEqualTo("freezing"); }
    @Test void classifiesColdTemperature()        { assertThat(engine.classifyTemperature(10)).isEqualTo("cold"); }
    @Test void classifiesComfortableTemperature() { assertThat(engine.classifyTemperature(20)).isEqualTo("comfortable"); }
    @Test void classifiesWarmTemperature()        { assertThat(engine.classifyTemperature(30)).isEqualTo("warm"); }
    @Test void classifiesHotTemperature()         { assertThat(engine.classifyTemperature(40)).isEqualTo("hot"); }

    // ── §2  switch expression ────────────────────────────────────────────────

    @Test void mondayIsDay1() { assertThat(engine.dayName(1)).isEqualTo("Monday"); }
    @Test void sundayIsDay7() { assertThat(engine.dayName(7)).isEqualTo("Sunday"); }
    @Test void unknownDay()   { assertThat(engine.dayName(9)).isEqualTo("Unknown"); }

    // ── §3  Ternary ──────────────────────────────────────────────────────────

    @Test void evenNumber() { assertThat(engine.isEven(4)).isEqualTo("even"); }
    @Test void oddNumber()  { assertThat(engine.isEven(3)).isEqualTo("odd"); }

    // ── §4  for loop ─────────────────────────────────────────────────────────

    @Test void sumUpTo5() { assertThat(engine.sumUpTo(5)).isEqualTo(15); }
    @Test void sumUpTo0() { assertThat(engine.sumUpTo(0)).isEqualTo(0); }

    // ── §5  while loop (Collatz) ─────────────────────────────────────────────

    @Test void collatzFrom6() { assertThat(engine.collatzSteps(6)).isEqualTo(8); }
    @Test void collatzFrom1() { assertThat(engine.collatzSteps(1)).isEqualTo(0); }

    // ── §6  do-while (digital root) ─────────────────────────────────────────

    @Test void digitalRootOf493() { assertThat(engine.digitalRoot(493)).isEqualTo(7); }
    @Test void digitalRootOf0()   { assertThat(engine.digitalRoot(0)).isEqualTo(0); }

    // ── §7  foreach ──────────────────────────────────────────────────────────

    @Test void sumArray() { assertThat(engine.sumArray(new int[]{1, 2, 3, 4, 5})).isEqualTo(15); }

    // ── §8  Nested loops ─────────────────────────────────────────────────────

    @Test void multiplicationTable3x3() {
        int[][] table = engine.multiplicationTable(3);
        assertThat(table[0][0]).isEqualTo(1); // 1×1
        assertThat(table[2][2]).isEqualTo(9); // 3×3
    }

    // ── §9  break/continue (next prime) ─────────────────────────────────────

    @Test void nextPrimeAfter10() { assertThat(engine.nextPrime(10)).isEqualTo(11); }
    @Test void nextPrimeAfter14() { assertThat(engine.nextPrime(14)).isEqualTo(17); }
    @Test void nextPrimeFrom2()   { assertThat(engine.nextPrime(2)).isEqualTo(2); }

    // ── §10  Labeled break (goto equivalent) ─────────────────────────────────

    @Test
    @DisplayName("Labeled break exits nested loop — Java goto equivalent")
    void labeledBreakFindsTarget() {
        int[][] matrix = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        int[] pos = engine.labeledBreakSearch(matrix, 5);
        assertThat(pos[0]).isEqualTo(1); // row 1
        assertThat(pos[1]).isEqualTo(1); // col 1
    }

    @Test
    @DisplayName("Labeled break — returns {-1,-1} when not found")
    void labeledBreakNotFound() {
        int[][] matrix = {{1, 2}, {3, 4}};
        int[] pos = engine.labeledBreakSearch(matrix, 99);
        assertThat(pos).containsExactly(-1, -1);
    }

    // ── §11  try/catch/finally ────────────────────────────────────────────────

    @Test void safeDivideNormal()      { assertThat(engine.safeDivide(10, 2)).isEqualTo(5.0); }
    @Test void safeDivideByZeroIsNaN() { assertThat(engine.safeDivide(5, 0)).isNaN(); }

    // ── §12  Multiple exceptions ──────────────────────────────────────────────

    @Test void parsesValidNumber()  { assertThat(engine.parseAndFormat("42")).isEqualTo("Value: 42"); }
    @Test void rejectsNonNumber()   { assertThat(engine.parseAndFormat("abc")).startsWith("Not a number"); }
    @Test void handlesNullInput()   { assertThat(engine.parseAndFormat(null)).isEqualTo("Input was null"); }

    // ── §13  Exception hierarchy (checked vs unchecked) ──────────────────────

    @Test
    @DisplayName("lookupConcept — known concept returns success message")
    void lookupKnownConcept() throws ControlFlowEngine.ConceptNotFoundException {
        assertThat(engine.lookupConcept("OOP")).contains("OOP");
    }

    @Test
    @DisplayName("lookupConcept — throws checked ConceptNotFoundException for unknown")
    void lookupUnknownConceptThrows() {
        assertThatThrownBy(() -> engine.lookupConcept("UNKNOWN_CONCEPT"))
            .isInstanceOf(ControlFlowEngine.ConceptNotFoundException.class);
    }

    @Test
    @DisplayName("lookupConcept — throws unchecked InvalidInputException for blank")
    void lookupBlankConceptThrows() {
        assertThatThrownBy(() -> engine.lookupConcept(""))
            .isInstanceOf(ControlFlowEngine.InvalidInputException.class);
    }

    // ── §14  Java pattern matching switch ─────────────────────────────────────

    @Test
    @DisplayName("Pattern matching switch — Circle area")
    void circleAreaPatternMatching() {
        var circle = new ControlFlowEngine.Shape.Circle(5.0);
        assertThat(engine.area(circle)).isCloseTo(Math.PI * 25, within(0.001));
    }

    @Test
    @DisplayName("Pattern matching switch — Rectangle area")
    void rectangleAreaPatternMatching() {
        var rect = new ControlFlowEngine.Shape.Rectangle(4.0, 6.0);
        assertThat(engine.area(rect)).isEqualTo(24.0);
    }
}
