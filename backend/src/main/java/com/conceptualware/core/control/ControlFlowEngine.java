package com.conceptualware.core.control;

import java.util.*;

public class ControlFlowEngine {

    public String classifyTemperature(double celsius) {
        if (celsius < 0) {
            return "freezing";
        } else if (celsius < 15) {
            return "cold";
        } else if (celsius < 25) {
            return "comfortable";
        } else if (celsius < 35) {
            return "warm";
        } else {
            return "hot";
        }
    }

    public String dayName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1  -> "Monday";
            case 2  -> "Tuesday";
            case 3  -> "Wednesday";
            case 4  -> "Thursday";
            case 5  -> "Friday";
            case 6  -> "Saturday";
            case 7  -> "Sunday";
            default -> "Unknown";
        };
    }

    public String isEven(int n) { return (n % 2 == 0) ? "even" : "odd"; }

    public int sumUpTo(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) sum += i;
        return sum;
    }

    public int collatzSteps(int n) {
        int steps = 0;
        while (n != 1) {
            n = (n % 2 == 0) ? n / 2 : 3 * n + 1;
            steps++;
        }
        return steps;
    }

    public int digitalRoot(int n) {
        do {
            int sum = 0;
            while (n > 0) { sum += n % 10; n /= 10; }
            n = sum;
        } while (n >= 10);
        return n;
    }

    public int sumArray(int[] arr) {
        int total = 0;
        for (int val : arr) total += val;
        return total;
    }

    public int[][] multiplicationTable(int size) {
        int[][] table = new int[size][size];
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
                table[i][j] = (i + 1) * (j + 1);
        return table;
    }

    public int nextPrime(int n) {
        outer: for (int candidate = Math.max(n, 2); ; candidate++) {
            for (int d = 2; d * d <= candidate; d++) {
                if (candidate % d == 0) continue outer;
            }
            return candidate;
        }
    }

    public int[] labeledBreakSearch(int[][] matrix, int target) {
        int[] found = {-1, -1};

        search:
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                if (matrix[row][col] == target) {
                    found[0] = row;
                    found[1] = col;
                    break search;
                }
            }
        }
        return found;
    }

    public double safeDivide(double a, double b) {
        try {
            if (b == 0) throw new ArithmeticException("Division by zero");
            return a / b;
        } catch (ArithmeticException e) {
            return Double.NaN;
        } finally {
        }
    }

    public String parseAndFormat(String input) {
        try {
            int value = Integer.parseInt(input.trim());
            return "Value: " + value;
        } catch (NumberFormatException e) {
            return "Not a number: " + e.getMessage();
        } catch (NullPointerException e) {
            return "Input was null";
        } catch (Exception e) {
            return "Unexpected: " + e.getClass().getSimpleName();
        }
    }

    public static class ConceptNotFoundException extends Exception {
        public ConceptNotFoundException(String concept) {
            super("Concept not found: " + concept);
        }
    }

    public static class InvalidInputException extends RuntimeException {
        public InvalidInputException(String msg) { super(msg); }
    }

    public String lookupConcept(String name) throws ConceptNotFoundException {
        if (name == null || name.isBlank()) throw new InvalidInputException("Name cannot be blank");
        Set<String> known = Set.of("OOP", "FP", "DDD", "SOLID", "CQRS", "TDD");
        if (!known.contains(name)) throw new ConceptNotFoundException(name);
        return "Concept '" + name + "' found";
    }

    public int factorial(int n) {
        assert n >= 0 : "n must be non-negative, got: " + n;
        if (n == 0) return 1;
        return n * factorial(n - 1);
    }

    sealed interface Shape permits Shape.Circle, Shape.Rectangle, Shape.Triangle {
        record Circle(double radius) implements Shape {}
        record Rectangle(double w, double h) implements Shape {}
        record Triangle(double base, double height) implements Shape {}
    }

    public double area(Shape shape) {
        return switch (shape) {
            case Shape.Circle c       -> Math.PI * c.radius() * c.radius();
            case Shape.Rectangle r    -> r.w() * r.h();
            case Shape.Triangle t     -> 0.5 * t.base() * t.height();
        };
    }

    public <T> T safeGet(java.util.concurrent.Future<T> future) {
        try {
            return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new InvalidInputException("Operation timed out");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new InvalidInputException("Async execution failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidInputException("Thread interrupted");
        }
    }
}
