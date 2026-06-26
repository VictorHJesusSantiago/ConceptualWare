package com.conceptualware.core.logic;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * LogicEngine — implements all propositional and predicate logic operations.
 *
 * Covers Concepts:
 *   #1  — Lógica Proposicional: AND, OR, NOT, XOR, NAND, NOR, Implication, Biconditional
 *   #1  — Tabela-verdade, Tautologia, Contradição, Curto-circuito
 *   #1  — Lógica de predicados, Quantificadores ∀ ∃
 *   #2  — Estruturas de controle: if/else, switch, for, while, do-while, break, continue
 *   #3  — Operadores bit a bit: AND, OR, XOR, NOT, <<, >>, >>>
 *   #28 — Álgebra booleana, Mapa de Karnaugh (simplificação), Complemento de dois
 */
@Component
public class LogicEngine {

    // ─────────────────────────────────────────────────────────────────────────
    // §1 — Álgebra Booleana / Operadores Lógicos
    // ─────────────────────────────────────────────────────────────────────────

    public boolean and(boolean a, boolean b)  { return a && b; }   // E lógico
    public boolean or(boolean a, boolean b)   { return a || b; }   // OU lógico
    public boolean not(boolean a)             { return !a; }        // NÃO lógico
    public boolean xor(boolean a, boolean b)  { return a ^ b; }    // OU exclusivo
    public boolean nand(boolean a, boolean b) { return !(a && b); } // NÃO-E
    public boolean nor(boolean a, boolean b)  { return !(a || b); } // NÃO-OU

    // Implicação lógica: A → B ≡ ¬A ∨ B
    public boolean implies(boolean a, boolean b) { return !a || b; }

    // Bicondicional: A ↔ B ≡ (A → B) ∧ (B → A)
    public boolean biconditional(boolean a, boolean b) { return implies(a, b) && implies(b, a); }

    // ─────────────────────────────────────────────────────────────────────────
    // §2 — Tabela-Verdade
    // ─────────────────────────────────────────────────────────────────────────

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
                    case NOT          -> not(a);       // b ignored
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

    // Tautologia: expression is true for ALL combinations
    public boolean isTautology(List<TruthTableRow> table) {
        return table.stream().allMatch(TruthTableRow::result);
    }

    // Contradição: expression is false for ALL combinations
    public boolean isContradiction(List<TruthTableRow> table) {
        return table.stream().noneMatch(TruthTableRow::result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §3 — Expressões Lógicas Compostas & Curto-Circuito
    // ─────────────────────────────────────────────────────────────────────────

    /** Short-circuit AND: second predicate NOT evaluated if first is false. */
    public <T> boolean shortCircuitAnd(T val, Predicate<T> first, Predicate<T> second) {
        return first.test(val) && second.test(val); // Java && is already short-circuit
    }

    /** Short-circuit OR: second predicate NOT evaluated if first is true. */
    public <T> boolean shortCircuitOr(T val, Predicate<T> first, Predicate<T> second) {
        return first.test(val) || second.test(val);
    }

    // Compound logical expression: (A AND B) OR (NOT C)
    public boolean compoundExpression(boolean a, boolean b, boolean c) {
        return and(a, b) || not(c);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §4 — Lógica de Predicados & Quantificadores
    // ─────────────────────────────────────────────────────────────────────────

    /** ∀ (Universal quantifier): P(x) holds for ALL elements. */
    public <T> boolean forAll(Collection<T> domain, Predicate<T> predicate) {
        return domain.stream().allMatch(predicate);
    }

    /** ∃ (Existential quantifier): P(x) holds for AT LEAST ONE element. */
    public <T> boolean exists(Collection<T> domain, Predicate<T> predicate) {
        return domain.stream().anyMatch(predicate);
    }

    /** ∃! (Unique existential): P(x) holds for EXACTLY ONE element. */
    public <T> boolean existsUnique(Collection<T> domain, Predicate<T> predicate) {
        return domain.stream().filter(predicate).count() == 1;
    }

    /** ¬∀ ≡ ∃¬: negation of universal = existential of negation */
    public <T> boolean notForAll(Collection<T> domain, Predicate<T> predicate) {
        return exists(domain, predicate.negate());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §5 — Operadores Bit a Bit (Concept 1 & 3)
    // ─────────────────────────────────────────────────────────────────────────

    public int bitwiseAnd(int a, int b)       { return a & b; }
    public int bitwiseOr(int a, int b)        { return a | b; }
    public int bitwiseXor(int a, int b)       { return a ^ b; }
    public int bitwiseNot(int a)              { return ~a; }
    public int shiftLeft(int a, int n)        { return a << n; }    // <<
    public int shiftRight(int a, int n)       { return a >> n; }    // >> (arithmetic)
    public int unsignedShiftRight(int a, int n){ return a >>> n; }  // >>> (logical)

    /** Check if bit at position pos is set. */
    public boolean isBitSet(int num, int pos) {
        return (num & (1 << pos)) != 0;
    }

    /** Set bit at position pos. */
    public int setBit(int num, int pos) { return num | (1 << pos); }

    /** Clear bit at position pos. */
    public int clearBit(int num, int pos) { return num & ~(1 << pos); }

    /** Toggle bit at position pos. */
    public int toggleBit(int num, int pos) { return num ^ (1 << pos); }

    /** Count set bits (Hamming weight / Brian Kernighan's algorithm). */
    public int countSetBits(int num) {
        int count = 0;
        while (num != 0) {
            num &= num - 1; // Clear lowest set bit
            count++;
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §6 — Operadores Aritméticos & Relacionais (Concept 1)
    // ─────────────────────────────────────────────────────────────────────────

    public record ArithmeticResult(double add, double sub, double mul, double div,
                                   double mod, double power) {}

    public ArithmeticResult arithmetic(double a, double b) {
        if (b == 0 && (a % b == 0)) throw new ArithmeticException("Division by zero");
        return new ArithmeticResult(
            a + b,            // +
            a - b,            // -
            a * b,            // *
            b != 0 ? a / b : Double.NaN,  // /
            b != 0 ? a % b : Double.NaN,  // %
            Math.pow(a, b)    // **
        );
    }

    // Relational operators
    public boolean eq(double a, double b)  { return a == b; }
    public boolean neq(double a, double b) { return a != b; }
    public boolean gt(double a, double b)  { return a > b; }
    public boolean lt(double a, double b)  { return a < b; }
    public boolean gte(double a, double b) { return a >= b; }
    public boolean lte(double a, double b) { return a <= b; }

    // ─────────────────────────────────────────────────────────────────────────
    // §7 — Complemento de Dois & Representação Binária (Concept 28)
    // ─────────────────────────────────────────────────────────────────────────

    /** Two's complement representation of a negative number. */
    public String twosComplement(int n, int bits) {
        if (n >= 0) return toBinary(n, bits);
        int magnitude = Math.abs(n);
        int inverted = ~magnitude & ((1 << bits) - 1); // bitwise NOT masked to `bits`
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

    /** Check integer overflow for addition. */
    public boolean willOverflow(int a, int b) {
        long result = (long) a + b;
        return result > Integer.MAX_VALUE || result < Integer.MIN_VALUE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §8 — Tabela-Verdade para expressão composta (demo completo)
    // ─────────────────────────────────────────────────────────────────────────

    public record PropExpression(String formula, boolean[] variables, boolean result) {}

    /**
     * Evaluate a named proposition with the given truth assignments.
     * Demonstrates: logical operators, arrays, loops, records.
     */
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

    // ─────────────────────────────────────────────────────────────────────────
    // §9 — Precedência de Operadores (Concept 1)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Demonstrates operator precedence:
     *   NOT > AND > OR  (in propositional logic)
     *   In Java: !, &&, ||  follow this precedence.
     */
    public boolean demonstratePrecedence(boolean a, boolean b, boolean c) {
        // NOT has highest precedence, then AND, then OR
        // This evaluates as: ((!a) AND b) OR c — NOT a first, then AND, then OR
        return !a && b || c;
    }

    /** Negation (Concept 1) */
    public boolean negate(boolean p) { return !p; }

    /** Conjunção (Concept 1) */
    public boolean conjunction(boolean p, boolean q) { return p && q; }

    /** Disjunção (Concept 1) */
    public boolean disjunction(boolean p, boolean q) { return p || q; }

    // ─────────────────────────────────────────────────────────────────────────
    // §10 — Operadores de Atribuição (Concept 1: =, +=, -=, *=, /=)
    // Demonstra todos os operadores de atribuição compostos explicitamente.
    // ─────────────────────────────────────────────────────────────────────────

    public record AssignmentDemo(int initial, int addAssign, int subAssign,
                                  int mulAssign, int divAssign, int modAssign) {}

    /** Shows every compound assignment operator applied to `initial` with operand `op`. */
    public AssignmentDemo demonstrateAssignmentOperators(int initial, int operand) {
        int a = initial; a += operand; // +=
        int b = initial; b -= operand; // -=
        int c = initial; c *= operand; // *=
        int d = initial; d /= (operand != 0 ? operand : 1); // /=
        int e = initial; e %= (operand != 0 ? operand : 1); // %=
        return new AssignmentDemo(initial, a, b, c, d, e);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // §11 — Karnugh Map simplification (Concept 28) — 2-variable SOP minimizer
    // Given a 4-bit minterm mask (A,B rows), returns minimized Boolean expression.
    // ─────────────────────────────────────────────────────────────────────────

    /** Identifies which minterms correspond to tautology, contradiction, or a specific term. */
    public String karnaughMinimize2Var(boolean[] minterms) {
        // minterms[0]=F(0,0), [1]=F(0,1), [2]=F(1,0), [3]=F(1,1)
        if (minterms.length != 4) throw new IllegalArgumentException("Need exactly 4 minterms for 2 variables");
        long trueCount = Arrays.stream(minterms).filter(b -> b).count();
        if (trueCount == 4) return "1";          // tautology
        if (trueCount == 0) return "0";          // contradiction
        if (minterms[0] && minterms[1]) return "¬A";        // AB=00,01 → ¬A
        if (minterms[2] && minterms[3]) return "A";         // AB=10,11 → A
        if (minterms[0] && minterms[2]) return "¬B";        // AB=00,10 → ¬B
        if (minterms[1] && minterms[3]) return "B";         // AB=01,11 → B
        if (minterms[0]) return "¬A∧¬B";
        if (minterms[1]) return "¬A∧B";
        if (minterms[2]) return "A∧¬B";
        if (minterms[3]) return "A∧B";
        return "SOP"; // multi-minterm, fall back to full SOP
    }
}
