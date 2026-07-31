package com.conceptualware.core.logic;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@Component
public class LogicEngine {

    public boolean and(boolean a, boolean b)  { return a && b; }
    public boolean or(boolean a, boolean b)   { return a || b; }
    public boolean not(boolean a)             { return !a; }
    public boolean xor(boolean a, boolean b)  { return a ^ b; }
    public boolean nand(boolean a, boolean b) { return !(a && b); }
    public boolean nor(boolean a, boolean b)  { return !(a || b); }

    public boolean implies(boolean a, boolean b) { return !a || b; }

    public boolean biconditional(boolean a, boolean b) { return implies(a, b) && implies(b, a); }

    public enum BooleanOperator { AND, OR, NOT, XOR, NAND, NOR, IMPLIES, BICONDITIONAL }

    public record TruthTableRow(boolean a, boolean b, boolean result) {}

    public List<TruthTableRow> buildTruthTable(BooleanOperator op) {
        boolean[] values = {false, true};
        List<TruthTableRow> table = new ArrayList<>();

        for (boolean a : values) {
            for (boolean b : values) {
                boolean result = switch (op) {
                    case AND          -> and(a, b);
                    case OR           -> or(a, b);
                    case NOT          -> not(a);
                    case XOR          -> xor(a, b);
                    case NAND         -> nand(a, b);
                    case NOR          -> nor(a, b);
                    case IMPLIES      -> implies(a, b);
                    case BICONDITIONAL -> biconditional(a, b);
                };
                table.add(new TruthTableRow(a, b, result));
            }
        }
        return table;
    }

    public boolean isTautology(List<TruthTableRow> table) {
        return table.stream().allMatch(TruthTableRow::result);
    }

    public boolean isContradiction(List<TruthTableRow> table) {
        return table.stream().noneMatch(TruthTableRow::result);
    }

    public <T> boolean shortCircuitAnd(T val, Predicate<T> first, Predicate<T> second) {
        return first.test(val) && second.test(val);
    }

    public <T> boolean shortCircuitOr(T val, Predicate<T> first, Predicate<T> second) {
        return first.test(val) || second.test(val);
    }

    public boolean compoundExpression(boolean a, boolean b, boolean c) {
        return and(a, b) || not(c);
    }

    public <T> boolean forAll(Collection<T> domain, Predicate<T> predicate) {
        return domain.stream().allMatch(predicate);
    }

    public <T> boolean exists(Collection<T> domain, Predicate<T> predicate) {
        return domain.stream().anyMatch(predicate);
    }

    public <T> boolean existsUnique(Collection<T> domain, Predicate<T> predicate) {
        return domain.stream().filter(predicate).count() == 1;
    }

    public <T> boolean notForAll(Collection<T> domain, Predicate<T> predicate) {
        return exists(domain, predicate.negate());
    }

    public int bitwiseAnd(int a, int b)       { return a & b; }
    public int bitwiseOr(int a, int b)        { return a | b; }
    public int bitwiseXor(int a, int b)       { return a ^ b; }
    public int bitwiseNot(int a)              { return ~a; }
    public int shiftLeft(int a, int n)        { return a << n; }
    public int shiftRight(int a, int n)       { return a >> n; }
    public int unsignedShiftRight(int a, int n){ return a >>> n; }

    public boolean isBitSet(int num, int pos) {
        return (num & (1 << pos)) != 0;
    }

    public int setBit(int num, int pos) { return num | (1 << pos); }

    public int clearBit(int num, int pos) { return num & ~(1 << pos); }

    public int toggleBit(int num, int pos) { return num ^ (1 << pos); }

    public int countSetBits(int num) {
        int count = 0;
        while (num != 0) {
            num &= num - 1;
            count++;
        }
        return count;
    }

    public record ArithmeticResult(double add, double sub, double mul, double div,
                                   double mod, double power) {}

    public ArithmeticResult arithmetic(double a, double b) {
        if (b == 0 && (a % b == 0)) throw new ArithmeticException("Division by zero");
        return new ArithmeticResult(
            a + b,
            a - b,
            a * b,
            b != 0 ? a / b : Double.NaN,
            b != 0 ? a % b : Double.NaN,
            Math.pow(a, b)
        );
    }

    public boolean eq(double a, double b)  { return a == b; }
    public boolean neq(double a, double b) { return a != b; }
    public boolean gt(double a, double b)  { return a > b; }
    public boolean lt(double a, double b)  { return a < b; }
    public boolean gte(double a, double b) { return a >= b; }
    public boolean lte(double a, double b) { return a <= b; }

    public String twosComplement(int n, int bits) {
        if (n >= 0) return toBinary(n, bits);
        int magnitude = Math.abs(n);
        int inverted = ~magnitude & ((1 << bits) - 1);
        int complement = inverted + 1;
        return toBinary(complement & ((1 << bits) - 1), bits);
    }

    public String toBinary(int n, int bits) {
        StringBuilder sb = new StringBuilder();
        for (int i = bits - 1; i >= 0; i--) {
            sb.append(isBitSet(n, i) ? '1' : '0');
        }
        return sb.toString();
    }

    public boolean willOverflow(int a, int b) {
        long result = (long) a + b;
        return result > Integer.MAX_VALUE || result < Integer.MIN_VALUE;
    }

    public record PropExpression(String formula, boolean[] variables, boolean result) {}

    public List<PropExpression> evaluateAllCombinations(String[] varNames,
                                                         java.util.function.Function<boolean[], Boolean> formula) {
        int n = varNames.length;
        int rows = (int) Math.pow(2, n);
        List<PropExpression> results = new ArrayList<>(rows);

        for (int i = 0; i < rows; i++) {
            boolean[] assignment = new boolean[n];
            for (int j = 0; j < n; j++) {
                assignment[j] = isBitSet(i, n - 1 - j);
            }
            String formulaStr = buildFormulaString(varNames, assignment);
            results.add(new PropExpression(formulaStr, assignment.clone(), formula.apply(assignment)));
        }
        return results;
    }

    private String buildFormulaString(String[] varNames, boolean[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < varNames.length; i++) {
            sb.append(varNames[i]).append("=").append(values[i]);
            if (i < varNames.length - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean demonstratePrecedence(boolean a, boolean b, boolean c) {
        return !a && b || c;
    }

    public boolean negate(boolean p) { return !p; }

    public boolean conjunction(boolean p, boolean q) { return p && q; }

    public boolean disjunction(boolean p, boolean q) { return p || q; }

    public record AssignmentDemo(int initial, int addAssign, int subAssign,
                                  int mulAssign, int divAssign, int modAssign) {}

    public AssignmentDemo demonstrateAssignmentOperators(int initial, int operand) {
        int a = initial; a += operand;
        int b = initial; b -= operand;
        int c = initial; c *= operand;
        int d = initial; d /= (operand != 0 ? operand : 1);
        int e = initial; e %= (operand != 0 ? operand : 1);
        return new AssignmentDemo(initial, a, b, c, d, e);
    }

    public String karnaughMinimize2Var(boolean[] minterms) {
        if (minterms.length != 4) throw new IllegalArgumentException("Need exactly 4 minterms for 2 variables");
        long trueCount = 0;
        for (boolean b : minterms) if (b) trueCount++;
        if (trueCount == 4) return "1";
        if (trueCount == 0) return "0";
        if (minterms[0] && minterms[1]) return "¬A";
        if (minterms[2] && minterms[3]) return "A";
        if (minterms[0] && minterms[2]) return "¬B";
        if (minterms[1] && minterms[3]) return "B";
        if (minterms[0]) return "¬A∧¬B";
        if (minterms[1]) return "¬A∧B";
        if (minterms[2]) return "A∧¬B";
        if (minterms[3]) return "A∧B";
        return "SOP";
    }
}
