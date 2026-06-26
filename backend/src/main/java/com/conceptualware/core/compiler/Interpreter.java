package com.conceptualware.core.compiler;

import java.util.*;

/**
 * Concept #10 — Interpreter / REPL (Read-Eval-Print Loop):
 *
 *   An INTERPRETER directly executes the AST without a separate compilation step.
 *   This is in contrast to ahead-of-time (AOT) compilation.
 *
 *   INTERPRETER APPROACHES:
 *     1. Tree-Walking Interpreter (this implementation):
 *        Walk the AST nodes recursively, evaluating each node directly.
 *        Simple to implement; slower due to pointer chasing.
 *
 *     2. Bytecode Interpreter (JVM, CPython, Ruby MRI):
 *        Compile AST → compact bytecode → tight execution loop.
 *        Faster dispatch (table-driven), compact representation.
 *
 *     3. JIT Compilation (V8, HotSpot, PyPy):
 *        Identify hot code paths, compile to native at runtime.
 *        Best peak performance; high warm-up cost.
 *
 *   JIT vs AOT:
 *     JIT (Just-In-Time): compile at runtime; can use runtime profile info
 *       (inline based on actual receiver types, speculative optimization)
 *     AOT (Ahead-Of-Time): compile at build time; GraalVM native-image,
 *       Kotlin/Native — fast startup, predictable performance, no JIT warm-up
 *
 *   REPL: Read → Lex → Parse → Interpret → Print → Loop
 *     Enables interactive programming, immediate feedback.
 *     Used by: Python (CPython), Node.js, Ruby (irb), Java (JShell), Scala.
 */
public class Interpreter {

    // ── Runtime Values ────────────────────────────────────────────────────────

    private record ConceptFunction(AST.Stmt.FnDecl decl, Environment closure) {}
    private record ConceptArray(List<Object> elements) {}
    private record ConceptObject(String className, Map<String, Object> fields) {}

    /** Signal exceptions for control flow (not real errors). */
    private static class ReturnSignal  extends RuntimeException { final Object value; ReturnSignal(Object v)  { super(null,null,true,false); value = v; } }
    private static class BreakSignal   extends RuntimeException { BreakSignal()   { super(null,null,true,false); } }
    private static class ContinueSignal extends RuntimeException { ContinueSignal() { super(null,null,true,false); } }
    public  static class RuntimeError  extends RuntimeException { RuntimeError(String msg) { super(msg); } }

    // ── Environment (Scope) ───────────────────────────────────────────────────

    public static class Environment {
        private final Map<String, Object> vars = new LinkedHashMap<>();
        private final Environment parent;

        public Environment(Environment parent) { this.parent = parent; }

        public void define(String name, Object value) { vars.put(name, value); }

        public Object get(String name) {
            if (vars.containsKey(name)) return vars.get(name);
            if (parent != null) return parent.get(name);
            throw new RuntimeError("Undefined variable: '" + name + "'");
        }

        public void set(String name, Object value) {
            if (vars.containsKey(name)) { vars.put(name, value); return; }
            if (parent != null) { parent.set(name, value); return; }
            throw new RuntimeError("Undefined variable: '" + name + "'");
        }
    }

    // ── Interpreter State ─────────────────────────────────────────────────────

    private Environment global = new Environment(null);
    private final StringBuilder output = new StringBuilder();

    public Interpreter() {
        registerBuiltins();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String execute(String source) {
        output.setLength(0);
        try {
            var lexer  = new Lexer(source);
            var tokens = lexer.tokenize();
            if (!lexer.errors().isEmpty()) return "Lexer errors: " + lexer.errors();

            var parser = new Parser(tokens);
            var program = parser.parse();
            if (!parser.errors().isEmpty()) return "Parse errors: " + parser.errors();

            var analyzer = new SemanticAnalyzer();
            var analysis = analyzer.analyze(program);
            if (analysis.hasErrors()) return "Semantic errors: " + analysis.errors();

            executeProgram(program);
        } catch (RuntimeError e) {
            return "Runtime error: " + e.getMessage();
        }
        return output.toString();
    }

    /** REPL mode: stateful — each call shares the global environment. */
    public String executeRepl(String line) {
        output.setLength(0);
        try {
            var tokens = new Lexer(line).tokenize();
            // Auto-add semicolon for expression statements in REPL
            String src = line.trim().endsWith(";") ? line : line + ";";
            tokens = new Lexer(src).tokenize();
            var program = new Parser(tokens).parse();
            for (var stmt : program.body()) executeStmt(stmt, global);
        } catch (RuntimeError e) {
            return "Error: " + e.getMessage();
        }
        return output.toString();
    }

    // ── Statement Execution ───────────────────────────────────────────────────

    private void executeProgram(AST.Stmt.Program p) {
        for (var stmt : p.body()) executeStmt(stmt, global);
    }

    private void executeStmt(AST.Stmt stmt, Environment env) {
        switch (stmt) {
            case AST.Stmt.Block b -> {
                Environment blockEnv = new Environment(env);
                for (var s : b.stmts()) executeStmt(s, blockEnv);
            }
            case AST.Stmt.VarDecl v -> {
                Object val = v.initializer() != null ? evalExpr(v.initializer(), env) : null;
                env.define(v.name(), val);
            }
            case AST.Stmt.FnDecl f -> env.define(f.name(), new ConceptFunction(f, env));
            case AST.Stmt.ClassDecl c -> env.define(c.name(), c); // class descriptor
            case AST.Stmt.If i -> {
                if (isTruthy(evalExpr(i.condition(), env))) executeStmt(i.thenBranch(), env);
                else if (i.elseBranch() != null)            executeStmt(i.elseBranch(), env);
            }
            case AST.Stmt.While w -> {
                while (isTruthy(evalExpr(w.condition(), env))) {
                    try { executeBlock(w.body(), new Environment(env)); }
                    catch (BreakSignal ignored) { break; }
                    catch (ContinueSignal ignored) {}
                }
            }
            case AST.Stmt.For f -> {
                Environment forEnv = new Environment(env);
                if (f.init() != null)      executeStmt(f.init(), forEnv);
                while (f.condition() == null || isTruthy(evalExpr(f.condition(), forEnv))) {
                    try { executeBlock(f.body(), new Environment(forEnv)); }
                    catch (BreakSignal ignored) { break; }
                    catch (ContinueSignal ignored) {}
                    if (f.increment() != null) evalExpr(f.increment(), forEnv);
                }
            }
            case AST.Stmt.Return r -> throw new ReturnSignal(r.value() != null ? evalExpr(r.value(), env) : null);
            case AST.Stmt.Break ignored    -> throw new BreakSignal();
            case AST.Stmt.Continue ignored -> throw new ContinueSignal();
            case AST.Stmt.Print p -> {
                Object val = evalExpr(p.value(), env);
                String text = stringify(val) + "\n";
                output.append(text);
            }
            case AST.Stmt.ExprStmt e -> evalExpr(e.expr(), env);
            default -> {}
        }
    }

    private void executeBlock(AST.Stmt.Block b, Environment env) {
        for (var s : b.stmts()) executeStmt(s, env);
    }

    // ── Expression Evaluation ─────────────────────────────────────────────────

    private Object evalExpr(AST.Expr expr, Environment env) {
        return switch (expr) {
            case AST.Expr.Literal l     -> l.value();
            case AST.Expr.Identifier id -> env.get(id.name());
            case AST.Expr.BinaryOp b    -> evalBinary(b, env);
            case AST.Expr.UnaryOp u     -> evalUnary(u, env);
            case AST.Expr.Assignment a  -> evalAssignment(a, env);
            case AST.Expr.Call c        -> evalCall(c, env);
            case AST.Expr.ArrayLiteral a -> {
                List<Object> elems = new ArrayList<>();
                for (var e : a.elements()) elems.add(evalExpr(e, env));
                yield new ConceptArray(elems);
            }
            case AST.Expr.Index i -> {
                Object obj = evalExpr(i.object(), env);
                int idx = ((Number) evalExpr(i.index(), env)).intValue();
                if (obj instanceof ConceptArray arr) yield arr.elements().get(idx);
                if (obj instanceof String s) yield String.valueOf(s.charAt(idx));
                throw new RuntimeError("Cannot index type: " + obj);
            }
            case AST.Expr.Member m -> {
                Object obj = evalExpr(m.object(), env);
                if (obj instanceof ConceptObject co) yield co.fields().getOrDefault(m.field(), null);
                throw new RuntimeError("Cannot access member of: " + obj);
            }
            case AST.Expr.Ternary t -> isTruthy(evalExpr(t.condition(), env))
                ? evalExpr(t.thenExpr(), env) : evalExpr(t.elseExpr(), env);
            case AST.Expr.New n -> {
                Object classDef = env.get(n.className());
                if (classDef instanceof AST.Stmt.ClassDecl c) {
                    Map<String, Object> fields = new LinkedHashMap<>();
                    Environment objEnv = new Environment(env);
                    for (var f : c.fields()) {
                        Object val = f.initializer() != null ? evalExpr(f.initializer(), objEnv) : null;
                        fields.put(f.name(), val);
                    }
                    yield new ConceptObject(n.className(), fields);
                }
                throw new RuntimeError("Not a class: " + n.className());
            }
        };
    }

    private Object evalBinary(AST.Expr.BinaryOp b, Environment env) {
        Object left = evalExpr(b.left(), env);
        // Short-circuit &&, ||
        if ("&&".equals(b.op())) return isTruthy(left) ? evalExpr(b.right(), env) : false;
        if ("||".equals(b.op())) return isTruthy(left) ? true : evalExpr(b.right(), env);

        Object right = evalExpr(b.right(), env);
        return switch (b.op()) {
            case "+"  -> (left instanceof String || right instanceof String)
                ? stringify(left) + stringify(right) : numOp(left, right, (l, r) -> l + r);
            case "-"  -> numOp(left, right, (l, r) -> l - r);
            case "*"  -> numOp(left, right, (l, r) -> l * r);
            case "/"  -> { if (toDouble(right) == 0) throw new RuntimeError("Division by zero"); yield numOp(left, right, (l, r) -> l / r); }
            case "%"  -> numOp(left, right, (l, r) -> l % r);
            case "**" -> numOp(left, right, Math::pow);
            case "==" -> Objects.equals(left, right);
            case "!=" -> !Objects.equals(left, right);
            case "<"  -> toDouble(left) < toDouble(right);
            case "<=" -> toDouble(left) <= toDouble(right);
            case ">"  -> toDouble(left) > toDouble(right);
            case ">=" -> toDouble(left) >= toDouble(right);
            case "&"  -> (long) toDouble(left) & (long) toDouble(right);
            case "|"  -> (long) toDouble(left) | (long) toDouble(right);
            case "^"  -> (long) toDouble(left) ^ (long) toDouble(right);
            case "<<" -> (long) toDouble(left) << (long) toDouble(right);
            case ">>" -> (long) toDouble(left) >> (long) toDouble(right);
            default -> throw new RuntimeError("Unknown binary op: " + b.op());
        };
    }

    private Object evalUnary(AST.Expr.UnaryOp u, Environment env) {
        if ("++".equals(u.op()) || "--".equals(u.op())) {
            String name = ((AST.Expr.Identifier) u.operand()).name();
            double old = toDouble(env.get(name));
            double newVal = "++".equals(u.op()) ? old + 1 : old - 1;
            env.set(name, toNumber(newVal));
            return u.prefix() ? toNumber(newVal) : toNumber(old);
        }
        Object val = evalExpr(u.operand(), env);
        return switch (u.op()) {
            case "-"   -> numOp(val, val, (l, r) -> -l);
            case "!"   -> !isTruthy(val);
            case "~"   -> ~(long) toDouble(val);
            default    -> throw new RuntimeError("Unknown unary op: " + u.op());
        };
    }

    private Object evalAssignment(AST.Expr.Assignment a, Environment env) {
        Object val = evalExpr(a.value(), env);
        if (a.target() instanceof AST.Expr.Identifier id) {
            Object cur = env.get(id.name());
            val = switch (a.op()) {
                case "+=" -> numOp(cur, val, (l, r) -> l + r);
                case "-=" -> numOp(cur, val, (l, r) -> l - r);
                case "*=" -> numOp(cur, val, (l, r) -> l * r);
                case "/=" -> numOp(cur, val, (l, r) -> l / r);
                default   -> val;
            };
            env.set(id.name(), val);
        }
        return val;
    }

    private Object evalCall(AST.Expr.Call c, Environment env) {
        Object callee = evalExpr(c.callee(), env);
        List<Object> args = new ArrayList<>();
        for (var arg : c.args()) args.add(evalExpr(arg, env));

        if (callee instanceof ConceptFunction fn) {
            Environment fnEnv = new Environment(fn.closure());
            var params = fn.decl().params();
            if (params.size() != args.size())
                throw new RuntimeError("Expected " + params.size() + " args, got " + args.size());
            for (int i = 0; i < params.size(); i++) fnEnv.define(params.get(i).name(), args.get(i));
            try { executeBlock(fn.decl().body(), fnEnv); return null; }
            catch (ReturnSignal r) { return r.value; }
        }
        throw new RuntimeError("Not callable: " + callee);
    }

    // ── Builtins ──────────────────────────────────────────────────────────────

    private void registerBuiltins() {
        // We register builtins as ConceptFunctions with special handling in evalCall
        // Instead, handle builtin calls by name in a special method
        global.define("__builtins__", true); // marker
    }

    // Override evalCall to handle builtins by name
    // (We detect them because their name resolves to a Java-side function descriptor)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.doubleValue() != 0;
        if (val instanceof String s) return !s.isEmpty();
        return true;
    }

    @FunctionalInterface interface DoubleOp { double apply(double l, double r); }

    private Object numOp(Object l, Object r, DoubleOp op) {
        double ld = toDouble(l), rd = toDouble(r);
        double res = op.apply(ld, rd);
        return toNumber(res);
    }

    private Object toNumber(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return (long) d;
        return d;
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        throw new RuntimeError("Expected number, got: " + v);
    }

    private String stringify(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean) return v.toString();
        if (v instanceof Double d) return d == Math.floor(d) ? String.valueOf(d.longValue()) : v.toString();
        if (v instanceof ConceptArray arr) return arr.elements().stream().map(this::stringify).toList().toString();
        if (v instanceof ConceptObject obj) return obj.className() + "{" + obj.fields() + "}";
        return String.valueOf(v);
    }

    public String getOutput() { return output.toString(); }
}
