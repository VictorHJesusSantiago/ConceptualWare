package com.conceptualware.core.compiler;

import java.util.List;

/**
 * Concept #10 — Abstract Syntax Tree (AST):
 *
 *   The AST is a TREE representation of the syntactic structure of source code.
 *   Unlike the Parse Tree (CST), the AST discards syntactic sugar and redundant
 *   nodes (parentheses, semicolons), keeping only semantically meaningful nodes.
 *
 *   Two categories:
 *     Stmt — statements (produce side effects, no value: if, while, var decl)
 *     Expr — expressions (produce a value: 1+2, fn call, literal)
 *
 *   Sealed interfaces enforce exhaustive pattern matching in the visitor/interpreter.
 *   Every statement or expression IS one of the listed subtypes — no others possible.
 *
 *   Visitor pattern (implicit via Java 21 pattern matching switch):
 *     Instead of accept(visitor), we use switch(node) { case IfStmt s -> ... }
 *     This is cleaner with sealed types and avoids the boilerplate double-dispatch.
 */
public final class AST {

    // ── Statements ────────────────────────────────────────────────────────────

    /** A statement is executed for its side effects. Returns no value. */
    public sealed interface Stmt permits
        Stmt.Program, Stmt.Block, Stmt.VarDecl, Stmt.FnDecl,
        Stmt.ClassDecl, Stmt.Return, Stmt.If, Stmt.While,
        Stmt.For, Stmt.Break, Stmt.Continue, Stmt.Print, Stmt.ExprStmt {

        /** Source line for error reporting */
        int line();

        record Program(List<Stmt> body, int line) implements Stmt {}

        record Block(List<Stmt> stmts, int line) implements Stmt {}

        /** var x: int = 42; — type annotation optional for inference */
        record VarDecl(
            String name, String typeAnnotation, Expr initializer,
            boolean isConst, int line
        ) implements Stmt {}

        /** fn add(a: int, b: int) -> int { ... } */
        record FnDecl(
            String name,
            List<Param> params,
            String returnType,
            Block body,
            int line
        ) implements Stmt {}

        record Param(String name, String type) {}

        /** class Point { var x: int; var y: int; fn dist() -> float { ... } } */
        record ClassDecl(
            String name,
            List<VarDecl> fields,
            List<FnDecl> methods,
            int line
        ) implements Stmt {}

        record Return(Expr value, int line) implements Stmt {}
        record If(Expr condition, Stmt thenBranch, Stmt elseBranch, int line) implements Stmt {}
        record While(Expr condition, Block body, int line) implements Stmt {}
        record For(Stmt init, Expr condition, Expr increment, Block body, int line) implements Stmt {}
        record Break(int line) implements Stmt {}
        record Continue(int line) implements Stmt {}
        record Print(Expr value, int line) implements Stmt {}
        record ExprStmt(Expr expr, int line) implements Stmt {}
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    /** An expression evaluates to a value. */
    public sealed interface Expr permits
        Expr.Literal, Expr.Identifier, Expr.BinaryOp, Expr.UnaryOp,
        Expr.Assignment, Expr.Call, Expr.Index, Expr.Member,
        Expr.Ternary, Expr.ArrayLiteral, Expr.New {

        int line();

        /** Literal values: 42, 3.14, "hello", true, null */
        record Literal(Object value, int line) implements Expr {}

        /** Variable or function reference by name */
        record Identifier(String name, int line) implements Expr {}

        /** Binary operations: a + b, x < y, p && q */
        record BinaryOp(Expr left, String op, Expr right, int line) implements Expr {}

        /** Unary operations: -x, !flag, ~bits, ++i, --j */
        record UnaryOp(String op, Expr operand, boolean prefix, int line) implements Expr {}

        /** Assignment: x = 10, arr[i] = v, obj.field = v */
        record Assignment(Expr target, String op, Expr value, int line) implements Expr {}

        /** Function call: add(1, 2), obj.method(args) */
        record Call(Expr callee, List<Expr> args, int line) implements Expr {}

        /** Array index: arr[i] */
        record Index(Expr object, Expr index, int line) implements Expr {}

        /** Member access: obj.field */
        record Member(Expr object, String field, int line) implements Expr {}

        /** Ternary conditional: condition ? thenExpr : elseExpr */
        record Ternary(Expr condition, Expr thenExpr, Expr elseExpr, int line) implements Expr {}

        /** Array literal: [1, 2, 3] */
        record ArrayLiteral(List<Expr> elements, int line) implements Expr {}

        /** Object construction: new Point(1, 2) */
        record New(String className, List<Expr> args, int line) implements Expr {}
    }

    // ── Pretty Printer ────────────────────────────────────────────────────────

    /** Converts AST back to formatted source (round-trip pretty printing). */
    public static String prettyPrint(Stmt stmt) {
        return prettyPrint(stmt, 0);
    }

    private static String prettyPrint(Stmt stmt, int indent) {
        String pad = "  ".repeat(indent);
        return switch (stmt) {
            case Stmt.Program p -> p.body().stream().map(s -> prettyPrint(s, 0)).reduce("", (a, b) -> a + b + "\n");
            case Stmt.Block b   -> "{\n" + b.stmts().stream().map(s -> pad + "  " + prettyPrint(s, indent + 1)).reduce("", String::concat) + pad + "}";
            case Stmt.VarDecl v -> (v.isConst() ? "const " : "var ") + v.name()
                + (v.typeAnnotation() != null ? ": " + v.typeAnnotation() : "")
                + (v.initializer() != null ? " = " + prettyPrint(v.initializer()) : "") + ";";
            case Stmt.FnDecl f  -> "fn " + f.name() + "(" +
                f.params().stream().map(p -> p.name() + ": " + p.type()).reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b) +
                ") -> " + f.returnType() + " " + prettyPrint(f.body(), indent);
            case Stmt.Return r  -> "return " + (r.value() != null ? prettyPrint(r.value()) : "") + ";";
            case Stmt.If i      -> "if (" + prettyPrint(i.condition()) + ") " + prettyPrint(i.thenBranch(), indent) +
                (i.elseBranch() != null ? " else " + prettyPrint(i.elseBranch(), indent) : "");
            case Stmt.While w   -> "while (" + prettyPrint(w.condition()) + ") " + prettyPrint(w.body(), indent);
            case Stmt.Print p   -> "print(" + prettyPrint(p.value()) + ");";
            case Stmt.ExprStmt e -> prettyPrint(e.expr()) + ";";
            default -> stmt.toString();
        };
    }

    public static String prettyPrint(Expr expr) {
        return switch (expr) {
            case Expr.Literal l      -> l.value() == null ? "null" : (l.value() instanceof String s ? "\"" + s + "\"" : String.valueOf(l.value()));
            case Expr.Identifier id  -> id.name();
            case Expr.BinaryOp b     -> "(" + prettyPrint(b.left()) + " " + b.op() + " " + prettyPrint(b.right()) + ")";
            case Expr.UnaryOp u      -> u.prefix() ? u.op() + prettyPrint(u.operand()) : prettyPrint(u.operand()) + u.op();
            case Expr.Assignment a   -> prettyPrint(a.target()) + " " + a.op() + " " + prettyPrint(a.value());
            case Expr.Call c         -> prettyPrint(c.callee()) + "(" +
                c.args().stream().map(AST::prettyPrint).reduce("", (x, y) -> x.isEmpty() ? y : x + ", " + y) + ")";
            case Expr.Index i        -> prettyPrint(i.object()) + "[" + prettyPrint(i.index()) + "]";
            case Expr.Member m       -> prettyPrint(m.object()) + "." + m.field();
            case Expr.Ternary t      -> prettyPrint(t.condition()) + " ? " + prettyPrint(t.thenExpr()) + " : " + prettyPrint(t.elseExpr());
            case Expr.ArrayLiteral a -> "[" + a.elements().stream().map(AST::prettyPrint).reduce("", (x, y) -> x.isEmpty() ? y : x + ", " + y) + "]";
            case Expr.New n          -> "new " + n.className() + "(...)";
        };
    }
}
