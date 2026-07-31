package com.conceptualware.core.compiler;

import com.conceptualware.core.compiler.IRGenerator.IR;

import java.util.*;

public class Optimizer {

    public record OptimizationStats(
        int constantsFolded,
        int deadCodesEliminated,
        int cseSubstitutions,
        int copyPropagations,
        int algebraicSimplifications,
        List<IR> originalIR,
        List<IR> optimizedIR
    ) {
        public int totalSavings() {
            return constantsFolded + deadCodesEliminated + cseSubstitutions
                + copyPropagations + algebraicSimplifications;
        }
    }

    private int constantsFolded      = 0;
    private int deadCodesEliminated  = 0;
    private int cseSubstitutions     = 0;
    private int copyPropagations     = 0;
    private int algebraicSimplifications = 0;

    public OptimizationStats optimize(List<IR> input) {
        List<IR> ir = new ArrayList<>(input);
        List<IR> original = List.copyOf(ir);

        boolean changed = true;
        while (changed) {
            int before = countChanges();
            ir = constantFoldingPass(ir);
            ir = constantPropagationPass(ir);
            ir = algebraicSimplificationPass(ir);
            ir = csePass(ir);
            ir = copyPropagationPass(ir);
            ir = deadCodeEliminationPass(ir);
            changed = countChanges() > before;
        }

        return new OptimizationStats(
            constantsFolded, deadCodesEliminated, cseSubstitutions,
            copyPropagations, algebraicSimplifications, original, List.copyOf(ir)
        );
    }

    private int countChanges() {
        return constantsFolded + deadCodesEliminated + cseSubstitutions
            + copyPropagations + algebraicSimplifications;
    }

    private List<IR> constantFoldingPass(List<IR> ir) {
        List<IR> result = new ArrayList<>();
        for (var inst : ir) {
            if (inst instanceof IR.BinOp b && isConstant(b.left()) && isConstant(b.right())) {
                var folded = fold(b.result(), b.left(), b.op(), b.right());
                if (folded != null) { result.add(folded); constantsFolded++; continue; }
            }
            result.add(inst);
        }
        return result;
    }

    private IR fold(String result, String left, String op, String right) {
        try {
            double l = Double.parseDouble(left);
            double r = Double.parseDouble(right);
            double res = switch (op) {
                case "+"  -> l + r;
                case "-"  -> l - r;
                case "*"  -> l * r;
                case "/"  -> r == 0 ? Double.NaN : l / r;
                case "%"  -> l % r;
                case "**" -> Math.pow(l, r);
                default -> Double.NaN;
            };
            if (Double.isNaN(res)) return null;
            String val = (res == Math.floor(res) && !Double.isInfinite(res) && !left.contains(".") && !right.contains("."))
                ? String.valueOf((long) res) : String.valueOf(res);
            return new IR.Copy(result, val);
        } catch (NumberFormatException e) { return null; }
    }

    private List<IR> constantPropagationPass(List<IR> ir) {
        Map<String, String> constants = new HashMap<>();
        List<IR> result = new ArrayList<>();
        for (var inst : ir) {
            inst = substituteConstants(inst, constants);
            result.add(inst);
            if (inst instanceof IR.Copy c && isConstant(c.source())) {
                constants.put(c.target(), c.source());
            } else if (inst instanceof IR.Label) {
                constants.clear();
            }
        }
        return result;
    }

    private IR substituteConstants(IR inst, Map<String, String> consts) {
        return switch (inst) {
            case IR.BinOp b -> {
                String l = consts.getOrDefault(b.left(), b.left());
                String r = consts.getOrDefault(b.right(), b.right());
                yield new IR.BinOp(b.result(), l, b.op(), r);
            }
            case IR.UnaryOp u -> new IR.UnaryOp(u.result(), u.op(), consts.getOrDefault(u.operand(), u.operand()));
            case IR.Copy c    -> new IR.Copy(c.target(), consts.getOrDefault(c.source(), c.source()));
            case IR.IfGoto g  -> new IR.IfGoto(consts.getOrDefault(g.condition(), g.condition()), g.jumpIfTrue(), g.label());
            case IR.Param p   -> new IR.Param(consts.getOrDefault(p.value(), p.value()));
            case IR.Return r  -> r.value() != null ? new IR.Return(consts.getOrDefault(r.value(), r.value())) : r;
            case IR.Print p   -> new IR.Print(consts.getOrDefault(p.value(), p.value()));
            default -> inst;
        };
    }

    private List<IR> algebraicSimplificationPass(List<IR> ir) {
        List<IR> result = new ArrayList<>();
        for (var inst : ir) {
            if (inst instanceof IR.BinOp b) {
                var simplified = simplifyAlgebra(b);
                if (simplified != null) { result.add(simplified); algebraicSimplifications++; continue; }
            }
            result.add(inst);
        }
        return result;
    }

    private IR simplifyAlgebra(IR.BinOp b) {
        boolean lIsZero = "0".equals(b.left());
        boolean rIsZero = "0".equals(b.right());
        boolean lIsOne  = "1".equals(b.left());
        boolean rIsOne  = "1".equals(b.right());

        return switch (b.op()) {
            case "+" -> rIsZero ? new IR.Copy(b.result(), b.left())
                      : lIsZero ? new IR.Copy(b.result(), b.right()) : null;
            case "-" -> rIsZero ? new IR.Copy(b.result(), b.left()) : null;
            case "*" -> rIsZero || lIsZero ? new IR.Copy(b.result(), "0")
                      : rIsOne  ? new IR.Copy(b.result(), b.left())
                      : lIsOne  ? new IR.Copy(b.result(), b.right()) : null;
            case "/" -> rIsOne  ? new IR.Copy(b.result(), b.left())
                      : lIsZero ? new IR.Copy(b.result(), "0") : null;
            case "**"-> rIsZero ? new IR.Copy(b.result(), "1")
                      : rIsOne  ? new IR.Copy(b.result(), b.left()) : null;
            default -> null;
        };
    }

    private List<IR> csePass(List<IR> ir) {
        Map<String, String> cseMap = new HashMap<>();
        List<IR> result = new ArrayList<>();
        for (var inst : ir) {
            if (inst instanceof IR.BinOp b) {
                String key = b.left() + " " + b.op() + " " + b.right();
                if (cseMap.containsKey(key)) {
                    result.add(new IR.Copy(b.result(), cseMap.get(key)));
                    cseSubstitutions++;
                    continue;
                }
                cseMap.put(key, b.result());
            }
            if (inst instanceof IR.Label) cseMap.clear();
            result.add(inst);
        }
        return result;
    }

    private List<IR> copyPropagationPass(List<IR> ir) {
        Map<String, String> copies = new HashMap<>();
        List<IR> result = new ArrayList<>();
        for (var inst : ir) {
            if (inst instanceof IR.Copy c && !isConstant(c.source())) {
                copies.put(c.target(), copies.getOrDefault(c.source(), c.source()));
            }
            inst = substituteCopies(inst, copies);
            result.add(inst);
            if (inst instanceof IR.BinOp b)    copies.entrySet().removeIf(e -> e.getValue().equals(b.result()) || e.getKey().equals(b.result()));
            else if (inst instanceof IR.Label)  copies.clear();
        }
        return result;
    }

    private IR substituteCopies(IR inst, Map<String, String> copies) {
        return switch (inst) {
            case IR.BinOp b  -> new IR.BinOp(b.result(), copies.getOrDefault(b.left(), b.left()), b.op(), copies.getOrDefault(b.right(), b.right()));
            case IR.Param p  -> new IR.Param(copies.getOrDefault(p.value(), p.value()));
            case IR.Return r -> r.value() != null ? new IR.Return(copies.getOrDefault(r.value(), r.value())) : r;
            default -> inst;
        };
    }

    private List<IR> deadCodeEliminationPass(List<IR> ir) {
        Set<String> live = new HashSet<>();
        List<IR> result = new ArrayList<>();

        for (int i = ir.size() - 1; i >= 0; i--) {
            IR inst = ir.get(i);
            boolean isDead = switch (inst) {
                case IR.BinOp b   -> !live.contains(b.result());
                case IR.UnaryOp u -> !live.contains(u.result());
                case IR.Copy c    -> !live.contains(c.target()) && c.target().startsWith("t");
                default -> false;
            };
            if (isDead) { deadCodesEliminated++; continue; }

            switch (inst) {
                case IR.BinOp b   -> { live.add(b.left()); live.add(b.right()); live.remove(b.result()); }
                case IR.UnaryOp u -> { live.add(u.operand()); live.remove(u.result()); }
                case IR.Copy c    -> { if (!isConstant(c.source())) live.add(c.source()); live.remove(c.target()); }
                case IR.IfGoto g  -> live.add(g.condition());
                case IR.Return r  -> { if (r.value() != null) live.add(r.value()); }
                case IR.Param p   -> live.add(p.value());
                case IR.Print p   -> live.add(p.value());
                case IR.Label l   -> {}
                default -> {}
            }
            result.add(0, inst);
        }
        return result;
    }

    private boolean isConstant(String operand) {
        if ("null".equals(operand) || "true".equals(operand) || "false".equals(operand)) return true;
        if (operand.startsWith("\"")) return true;
        try { Double.parseDouble(operand); return true; }
        catch (NumberFormatException e) { return false; }
    }
}
