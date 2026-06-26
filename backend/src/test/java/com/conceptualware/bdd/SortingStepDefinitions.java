package com.conceptualware.bdd;

import com.conceptualware.core.algorithms.sorting.SortingAlgorithms;
import com.conceptualware.core.algorithms.dp.DynamicProgramming;
import io.cucumber.java.en.*;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #19 — BDD Step Definitions:
 *   Given-When-Then (Gherkin), ATDD, living documentation
 *   Each method is a step that maps to a Gherkin phrase.
 *
 * Concept #23 — Definition of Done expressed as executable specifications
 */
public class SortingStepDefinitions {

    private int[] input;
    private int[] result;
    private int fibIndex;
    private long fibResult;
    private int[] knapsackWeights;
    private int[] knapsackValues;
    private int knapsackCapacity;
    private int knapsackResult;
    private String editSource;
    private String editTarget;
    private int editResult;

    // ── Background ────────────────────────────────────────────────────────────

    @Given("the sorting engine is initialized")
    public void theSortingEngineIsInitialized() {
        // SortingAlgorithms is a static utility class — always ready
    }

    // ── Given steps ───────────────────────────────────────────────────────────

    @Given("an unsorted array {}")
    public void anUnsortedArray(String arrayLiteral) {
        input = parseArray(arrayLiteral);
    }

    @Given("the Fibonacci index is {int}")
    public void theFibonacciIndexIs(int n) {
        fibIndex = n;
    }

    @Given("items with weights {} and values {}")
    public void itemsWithWeightsAndValues(String weightsLiteral, String valuesLiteral) {
        knapsackWeights = parseArray(weightsLiteral);
        knapsackValues  = parseArray(valuesLiteral);
    }

    @Given("a knapsack capacity of {int}")
    public void aKnapsackCapacityOf(int capacity) {
        knapsackCapacity = capacity;
    }

    @Given("source word {string} and target word {string}")
    public void sourceAndTargetWords(String source, String target) {
        editSource = source;
        editTarget = target;
    }

    // ── When steps ────────────────────────────────────────────────────────────

    @When("I apply {string}")
    public void iApplyAlgorithm(String algorithm) {
        result = switch (algorithm) {
            case "bubble-sort"    -> SortingAlgorithms.bubbleSort(input);
            case "merge-sort"     -> SortingAlgorithms.mergeSort(input);
            case "quick-sort"     -> SortingAlgorithms.quickSort(input);
            case "heap-sort"      -> SortingAlgorithms.heapSort(input);
            case "tim-sort"       -> SortingAlgorithms.timSort(input);
            case "selection-sort" -> SortingAlgorithms.selectionSort(input);
            case "insertion-sort" -> SortingAlgorithms.insertionSort(input);
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        };
    }

    @When("I compute Fibonacci using tabulation")
    public void iComputeFibonacciUsingTabulation() {
        fibResult = DynamicProgramming.fibTabulation(fibIndex);
    }

    @When("I solve the 0/1 knapsack problem")
    public void iSolveKnapsack() {
        knapsackResult = DynamicProgramming.knapsack01(knapsackWeights, knapsackValues, knapsackCapacity);
    }

    @When("I compute the edit distance")
    public void iComputeEditDistance() {
        editResult = DynamicProgramming.editDistance(editSource, editTarget);
    }

    // ── Then steps ────────────────────────────────────────────────────────────

    @Then("the result should be {}")
    public void theResultShouldBe(String expectedLiteral) {
        int[] expected = parseArray(expectedLiteral);
        assertThat(result).isEqualTo(expected);
    }

    @Then("the result should be sorted in ascending order")
    public void theResultShouldBeSorted() {
        assertThat(result).isSorted();
    }

    @Then("the result should contain {int} elements")
    public void theResultShouldContainElements(int count) {
        assertThat(result).hasSize(count);
    }

    @Then("the result should be {long}")
    public void theResultShouldBeLong(long expected) {
        assertThat(fibResult).isEqualTo(expected);
    }

    @Then("the maximum value should be {int}")
    public void theMaximumValueShouldBe(int expected) {
        assertThat(knapsackResult).isEqualTo(expected);
    }

    @Then("the result should be {int}")
    public void theResultShouldBeInt(int expected) {
        assertThat(editResult).isEqualTo(expected);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int[] parseArray(String literal) {
        String clean = literal.replaceAll("[\\[\\]\\s]", "");
        if (clean.isEmpty()) return new int[0];
        return Arrays.stream(clean.split(","))
            .mapToInt(Integer::parseInt)
            .toArray();
    }
}
