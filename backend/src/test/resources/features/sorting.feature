# Concept #19 — BDD: Behavior-Driven Development (Gherkin / Cucumber)
# Gherkin syntax: Feature → Scenario → Given-When-Then
# This .feature file IS the executable specification (ATDD).

Feature: Sorting Algorithms
  As a developer using ConceptualWare
  I want to execute sorting algorithms against arbitrary input
  So that I can verify correctness and understand time complexity

  Background:
    Given the sorting engine is initialized

  Scenario: Bubble Sort sorts a random array
    Given an unsorted array [64, 34, 25, 12, 22, 11, 90]
    When I apply "bubble-sort"
    Then the result should be [11, 12, 22, 25, 34, 64, 90]
    And the result should be sorted in ascending order

  Scenario: Merge Sort handles duplicates correctly
    Given an unsorted array [3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5]
    When I apply "merge-sort"
    Then the result should contain 11 elements
    And the result should be sorted in ascending order

  Scenario: Quick Sort handles single element
    Given an unsorted array [42]
    When I apply "quick-sort"
    Then the result should be [42]

  Scenario: Heap Sort handles already-sorted input
    Given an unsorted array [1, 2, 3, 4, 5]
    When I apply "heap-sort"
    Then the result should be [1, 2, 3, 4, 5]
    And the result should be sorted in ascending order

  Scenario Outline: All O(n log n) algorithms produce identical output
    Given an unsorted array [5, 3, 8, 1, 9, 2, 7, 4, 6]
    When I apply "<algorithm>"
    Then the result should be [1, 2, 3, 4, 5, 6, 7, 8, 9]

    Examples:
      | algorithm      |
      | merge-sort     |
      | quick-sort     |
      | heap-sort      |
      | tim-sort       |

Feature: Dynamic Programming
  As a developer
  I want to solve classic DP problems via ConceptualWare
  So that I can verify optimal substructure and overlapping subproblems

  Scenario: Fibonacci via tabulation
    Given the Fibonacci index is 10
    When I compute Fibonacci using tabulation
    Then the result should be 55

  Scenario: 0/1 Knapsack — classic example
    Given items with weights [1, 3, 4, 5] and values [1, 4, 5, 7]
    And a knapsack capacity of 7
    When I solve the 0/1 knapsack problem
    Then the maximum value should be 9

  Scenario: Edit distance between "kitten" and "sitting"
    Given source word "kitten" and target word "sitting"
    When I compute the edit distance
    Then the result should be 3
