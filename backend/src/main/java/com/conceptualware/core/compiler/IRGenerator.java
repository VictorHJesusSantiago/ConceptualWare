package com.conceptualware.core.compiler;

import java.util.*;

/**
 * Concept #10 — Intermediate Code Generation:
 *
 *   The IR Generator is the THIRD phase of the compiler pipeline.
 *   It translates the AST into an INTERMEDIATE REPRESENTATION (IR) that is:
 *     • Lower-level than the AST (closer to machine code)
 *     • Higher-level than machine code (still architecture-independent)
 *     • Easier to OPTIMIZE than the raw AST
 *
 *   We use THREE-ADDRESS CODE (TAC), a common IR format:
 *     t1 = a + b          (binary op)
 *     t2 = -c             (unary op)
 *     x = t1              (assignment/copy)
 *     GOTO label          (unconditional jump)
 *     IF t1 GOTO label    (conditional jump)
 *     CALL fn, n, result  (function call with n args)
 *     PARAM arg           (push argument)
 *     RETURN val          (function return)
 *     LABEL name:         (jump target)
 *
 *   Properties:
 *     • Each instruction has AT MOST 3 addresses (operand1, operand2, result)
 *     • Temporaries (t0, t1, ...) are used to break down complex expressions
 *     • Labels enable control flow (if/while/for branches)
 *     • SSA (Static Single Assignment): each variable assigned exactly once
 *       in the full IR optimization pipeline (we emit pre-SSA TAC here)
 */
public class IRGenerator {

    // ── IR Instruction Model ──────────────────────────────────────────────────

    public sealed interface IR permits
        IR.BinOp, IR.UnaryOp, IR.Copy, IR.Load, IR.Store,
        IR.Label, IR.Goto, IR.IfGoto, IR.Param, IR.Call,
        IR.Return, IR.Print, IR.Alloc, IR.Index, IR.StoreIndex {

        record BinOp(String result, String left, String op, String right) implements IR {
            public String toString() { return result + " = " + left + " " + op + " " + right; }
        }
        record UnaryOp(String result, String op, String operand) implements IR {
            public String toString() { return result + " = " + op + operand; }
        }
        record Copy(String target, String source) implements IR {
            public String toString() { return target + " = " + source; }
        }
        record Load(String result, String address) implements IR {
            public String toString() { return result + " = *" + address; }
        }
        record Store(String address, String value) implements IR {
            public String toString() { return "*" + address + " = " + value; }
        }
        record Label(String name) implements IR {
            public String toString() { return name + ":"; }
        }
        record Goto(String label) implements IR {
            public String toString() { return "GOTO " + label; }
        }
        record IfGoto(String condition, boolean jumpIfTrue, String label) implements IR {
            public String toString() { return "IF" + (jumpIfTrue ? "" : "FALSE") + " " + condition + " GOTO " + label; }
        }
        record Param(String value) implements IR {
            public String toString() { return "PARAM " + value; }
        }
        record Call(String result, String function, int argCount) implements IR {
            public String toString() { return (result != null ? result + " = " : "") + "CALL " + function + ", " + argCount; }
        }
        record Return(String value) implements IR {
            public String toString() { return "RETURN" + (value != null ? " " + value : ""); }
        }
        record Print(String value) implements IR {
            public String toString() { return "PRINT " + value; }
        }
        record Alloc(String name, String type) implements IR {
            public String toString() { return "ALLOC " + name + " : " + type; }
        }
        record Index(String result, String array, String index) implements IR {
            public String toString() { return result + " = " + array + "[" + index + "]"; }
        }
        record StoreIndex(String array, String index, String value) implements IR {
            public String toString() { return array + "[" + index + "] = " + value; }
        }
    }

    // ── Generator State ───────────────────────────────────────────────────────

    private final List<IR> instructions = new ArrayList<>();
    private int tempCount  = 0;
    private int labelCount = 0;

    // ── Public API ────────────────────────────────────────────────────────────

    public List<IR> generate(AST.Stmt.Program program) {
        for (var stmt : program.body()) genStmt(stmt);
        return List.copyOf(instructions);
    }

    // ── Statement Code Generation ─────────────────────────────────────────────

    private void genStmt(AST.Stmt stmt) {
        switch (stmt) {
            case AST.Stmt.Program p   -> p.body().forEach(this::genStmt);
            case AST.Stmt.Block b     -> b.stmts().forEach(this::genStmt);
            case AST.Stmt.VarDecl v   -> genVarDecl(v);
            case AST.Stmt.FnDecl f    -> genFnDecl(f);
            case AST.Stmt.If i        -> genIf(i);
            case AST.Stmt.While w     -> genWhile(w);
            case AST.Stmt.For fo      -> genFor(fo);
            case AST.Stmt.Return r    -> emit(new IR.Return(r.value() != null ? genExpr(r.value()) : null));
            case AST.Stmt.Print p     -> emit(new IR.Print(genExpr(p.value())));
            case AST.Stmt.ExprStmt e  -> genExpr(e.expr());
            case AST.Stmt.Break b     -> emit(new IR.Goto("break_target")); // patched by loop gen
            case AST.Stmt.Continue c  -> emit(new IR.Goto("continue_target"));
            case AST.Stmt.ClassDecl c -> c.methods().forEach(this::genFnDecl);
        }
    }

    private void genVarDecl(AST.Stmt.VarDecl v) {
        String type = v.typeAnnotation() != null ? v.typeAnnotation() : "unknown";
        emit(new IR.Alloc(v.name(), type));
        if (v.initializer() != null) {
            String val = genExpr(v.initializer());
            emit(new IR.Copy(v.name(), val));
        }
    }

    private void genFnDecl(AST.Stmt.FnDecl f) {
        emit(new IR.Label("fn_" + f.name()));
        for (var p : f.params()) emit(new IR.Alloc(p.name(), p.type()));
        f.body().stmts().forEach(this::genStmt);
        // Implicit return void if no return at end
        if (f.body().stmts().isEmpty() || !(instructions.get(instructions.size()-1) instanceof IR.Return)) {
            emit(new IR.Return(null));
        }
    }

    private void genIf(AST.Stmt.If i) {
        String cond  = genExpr(i.condition());
        String else_ = newLabel("else");
        String end   = newLabel("end_if");

        emit(new IR.IfGoto(cond, false, else_));
        genStmt(i.thenBranch());
        emit(new IR.Goto(end));
        emit(new IR.Label(else_));
        if (i.elseBranch() != null) genStmt(i.elseBranch());
        emit(new IR.Label(end));
    }

    private void genWhile(AST.Stmt.While w) {
        String head = newLabel("while_head");
        String exit = newLabel("while_exit");

        emit(new IR.Label(head));
        String cond = genExpr(w.condition());
        emit(new IR.IfGoto(cond, false, exit));
        w.body().stmts().forEach(this::genStmt);
        emit(new IR.Goto(head));
        emit(new IR.Label(exit));
    }

    private void genFor(AST.Stmt.For f) {
        if (f.init() != null)      genStmt(f.init());
        String head = newLabel("for_head");
        String exit = newLabel("for_exit");

        emit(new IR.Label(head));
        if (f.condition() != null) {
            String cond = genExpr(f.condition());
            emit(new IR.IfGoto(cond, false, exit));
        }
        f.body().stmts().forEach(this::genStmt);
        if (f.increment() != null) genExpr(f.increment());
        emit(new IR.Goto(head));
        emit(new IR.Label(exit));
    }

    // ── Expression Code Generation ────────────────────────────────────────────

    /** Returns the TAC "address" (temp variable or literal) holding the result. */
    private String genExpr(AST.Expr expr) {
        return switch (expr) {
            case AST.Expr.Literal l      -> l.value() == null ? "null" : String.valueOf(l.value());
            case AST.Expr.Identifier id  -> id.name();
            case AST.Expr.BinaryOp b     -> {
                String left  = genExpr(b.left());
                String right = genExpr(b.right());
                String t     = newTemp();
                emit(new IR.BinOp(t, left, b.op(), right));
                yield t;
            }
            case AST.Expr.UnaryOp u -> {
                String op = u.operand() instanceof AST.Expr.Identifier id ? id.name() : genExpr(u.operand());
                if ((u.op().equals("++") || u.op().equals("--"))) {
                    String arithOp = u.op().equals("++") ? "+" : "-";
                    String t = newTemp();
                    if (u.prefix()) {
                        emit(new IR.BinOp(op, op, arithOp, "1"));
                        emit(new IR.Copy(t, op));
                    } else {
                        emit(new IR.Copy(t, op));
                        emit(new IR.BinOp(op, op, arithOp, "1"));
                    }
                    yield t;
                }
                String t = newTemp();
                emit(new IR.UnaryOp(t, u.op(), op));
                yield t;
            }
            case AST.Expr.Assignment a -> {
                String val = genExpr(a.value());
                String target = a.target() instanceof AST.Expr.Identifier id ? id.name() : newTemp();
                if (!a.op().equals("=")) {
                    // Compound assignment: x += 1 → x = x + 1
                    String arith = a.op().replace("=", "").trim();
                    String t = newTemp();
                    emit(new IR.BinOp(t, target, arith, val));
                    emit(new IR.Copy(target, t));
                } else {
                    emit(new IR.Copy(target, val));
                }
                yield target;
            }
            case AST.Expr.Call c -> {
                // Push args in order (PARAM instruction)
                c.args().forEach(arg -> emit(new IR.Param(genExpr(arg))));
                String fnName = c.callee() instanceof AST.Expr.Identifier id ? id.name()
                    : c.callee() instanceof AST.Expr.Member m ? genExpr(m.object()) + "." + m.field()
                    : genExpr(c.callee());
                String result = newTemp();
                emit(new IR.Call(result, fnName, c.args().size()));
                yield result;
            }
            case AST.Expr.ArrayLiteral a -> {
                String arr = newTemp();
                emit(new IR.Alloc(arr, "array[" + a.elements().size() + "]"));
                for (int i = 0; i < a.elements().size(); i++) {
                    String val = genExpr(a.elements().get(i));
                    emit(new IR.StoreIndex(arr, String.valueOf(i), val));
                }
                yield arr;
            }
            case AST.Expr.Index i -> {
                String arr = genExpr(i.object());
                String idx = genExpr(i.index());
                String t   = newTemp();
                emit(new IR.Index(t, arr, idx));
                yield t;
            }
            case AST.Expr.Member m -> {
                String obj = genExpr(m.object());
                String t = newTemp();
                emit(new IR.Load(t, obj + "." + m.field()));
                yield t;
            }
            case AST.Expr.Ternary t -> {
                String cond = genExpr(t.condition());
                String result = newTemp();
                String elseLbl = newLabel("ternary_else");
                String endLbl  = newLabel("ternary_end");
                emit(new IR.IfGoto(cond, false, elseLbl));
                emit(new IR.Copy(result, genExpr(t.thenExpr())));
                emit(new IR.Goto(endLbl));
                emit(new IR.Label(elseLbl));
                emit(new IR.Copy(result, genExpr(t.elseExpr())));
                emit(new IR.Label(endLbl));
                yield result;
            }
            case AST.Expr.New n -> {
                n.args().forEach(arg -> emit(new IR.Param(genExpr(arg))));
                String t = newTemp();
                emit(new IR.Call(t, "new_" + n.className(), n.args().size()));
                yield t;
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String newTemp()    { return "t" + tempCount++; }
    private String newLabel(String prefix) { return prefix + "_" + labelCount++; }
    private void emit(IR instruction)      { instructions.add(instruction); }

    // ── Pretty Print ──────────────────────────────────────────────────────────

    public static String dump(List<IR> instructions) {
        var sb = new StringBuilder();
        int i = 0;
        for (var ir : instructions) {
            String prefix = ir instanceof IR.Label ? "" : "    ";
            sb.append("%4d  %s%s\n".formatted(i++, prefix, ir));
        }
        return sb.toString();
    }
}
