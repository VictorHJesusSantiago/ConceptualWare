package com.conceptualware.core.compiler;

import java.util.*;

/**
 * Concept #10 — Semantic Analysis:
 *
 *   After syntactic analysis (parsing), the compiler performs SEMANTIC analysis
 *   to catch errors that are grammatically correct but meaningless:
 *
 *   1. SYMBOL TABLE / SCOPE RESOLUTION:
 *      - Build a scoped symbol table (stack of hash maps)
 *      - Each block/function creates a new scope; leaving it pops the scope
 *      - Variable references are resolved to their declaration scope
 *      - Shadowing rules: inner scope variable can shadow outer scope
 *
 *   2. TYPE CHECKING:
 *      - Infer or verify the type of every expression
 *      - Ensure operands match operator requirements (int+int ok, int+string error)
 *      - Coercion rules: int → float implicit, no others
 *      - Check function call arity and argument types
 *
 *   3. SEMANTIC VALIDATION:
 *      - Variable used before declaration
 *      - Assignment to constant
 *      - Return type mismatch with declared return type
 *      - break/continue outside loop
 *      - Duplicate variable/function names in same scope
 *
 *   Output: enriched symbol table + type map + list of semantic errors
 */
public class SemanticAnalyzer {

    // ── Symbol Table ──────────────────────────────────────────────────────────

    public record Symbol(
        String name,
        String type,
        SymbolKind kind,
        boolean isConst,
        int declaredLine
    ) {}

    public enum SymbolKind { VARIABLE, FUNCTION, PARAMETER, CLASS }

    /** Lexically scoped symbol table implemented as a stack of maps. */
    public static class SymbolTable {
        private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();

        public void enterScope() { scopes.push(new LinkedHashMap<>()); }
        public void exitScope()  { scopes.pop(); }

        public void define(Symbol symbol) {
            if (scopes.isEmpty()) throw new IllegalStateException("No active scope");
            scopes.peek().put(symbol.name(), symbol);
        }

        public Optional<Symbol> resolve(String name) {
            for (var scope : scopes) {
                if (scope.containsKey(name)) return Optional.of(scope.get(name));
            }
            return Optional.empty();
        }

        public boolean isDeclaredInCurrentScope(String name) {
            return !scopes.isEmpty() && scopes.peek().containsKey(name);
        }

        public List<Map<String, Symbol>> allScopes() { return new ArrayList<>(scopes); }
    }

    // ── Analyzer State ────────────────────────────────────────────────────────

    private final SymbolTable symbolTable = new SymbolTable();
    private final Map<AST.Expr, String> typeMap = new IdentityHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private int loopDepth = 0;
    private String currentFnReturnType = null;

    // ── Public API ────────────────────────────────────────────────────────────

    public AnalysisResult analyze(AST.Stmt.Program program) {
        symbolTable.enterScope(); // global scope
        registerBuiltins();
        for (var stmt : program.body()) analyzeStmt(stmt);
        symbolTable.exitScope();
        return new AnalysisResult(symbolTable, typeMap, List.copyOf(errors));
    }

    public record AnalysisResult(
        SymbolTable symbolTable,
        Map<AST.Expr, String> typeMap,
        List<String> errors
    ) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    // ── Statement Analysis ────────────────────────────────────────────────────

    private void analyzeStmt(AST.Stmt stmt) {
        switch (stmt) {
            case AST.Stmt.Program p   -> p.body().forEach(this::analyzeStmt);
            case AST.Stmt.Block b     -> analyzeBlock(b);
            case AST.Stmt.VarDecl v   -> analyzeVarDecl(v);
            case AST.Stmt.FnDecl f    -> analyzeFnDecl(f);
            case AST.Stmt.ClassDecl c -> analyzeClassDecl(c);
            case AST.Stmt.If i        -> analyzeIf(i);
            case AST.Stmt.While w     -> analyzeWhile(w);
            case AST.Stmt.For fo      -> analyzeFor(fo);
            case AST.Stmt.Return r    -> analyzeReturn(r);
            case AST.Stmt.Break b     -> { if (loopDepth == 0) error("'break' outside loop", b.line()); }
            case AST.Stmt.Continue c  -> { if (loopDepth == 0) error("'continue' outside loop", c.line()); }
            case AST.Stmt.Print p     -> analyzeExpr(p.value());
            case AST.Stmt.ExprStmt e  -> analyzeExpr(e.expr());
        }
    }

    private void analyzeBlock(AST.Stmt.Block b) {
        symbolTable.enterScope();
        b.stmts().forEach(this::analyzeStmt);
        symbolTable.exitScope();
    }

    private void analyzeVarDecl(AST.Stmt.VarDecl v) {
        if (symbolTable.isDeclaredInCurrentScope(v.name())) {
            error("Variable '%s' already declared in this scope".formatted(v.name()), v.line());
        }
        String type = "unknown";
        if (v.initializer() != null) {
            type = analyzeExpr(v.initializer());
            if (v.typeAnnotation() != null && !isCompatible(type, v.typeAnnotation())) {
                error("Type mismatch: expected '%s', got '%s' for var '%s'"
                    .formatted(v.typeAnnotation(), type, v.name()), v.line());
            }
        }
        String declaredType = v.typeAnnotation() != null ? v.typeAnnotation() : type;
        symbolTable.define(new Symbol(v.name(), declaredType, SymbolKind.VARIABLE, v.isConst(), v.line()));
    }

    private void analyzeFnDecl(AST.Stmt.FnDecl f) {
        if (symbolTable.isDeclaredInCurrentScope(f.name())) {
            error("Function '%s' already declared".formatted(f.name()), f.line());
        }
        String sigType = "fn(" + f.params().stream().map(AST.Stmt.Param::type).reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b) + ")->" + f.returnType();
        symbolTable.define(new Symbol(f.name(), sigType, SymbolKind.FUNCTION, true, f.line()));

        symbolTable.enterScope();
        for (var p : f.params()) {
            symbolTable.define(new Symbol(p.name(), p.type(), SymbolKind.PARAMETER, false, f.line()));
        }
        String prevReturn = currentFnReturnType;
        currentFnReturnType = f.returnType();
        f.body().stmts().forEach(this::analyzeStmt);
        currentFnReturnType = prevReturn;
        symbolTable.exitScope();
    }

    private void analyzeClassDecl(AST.Stmt.ClassDecl c) {
        symbolTable.define(new Symbol(c.name(), c.name(), SymbolKind.CLASS, true, c.line()));
        symbolTable.enterScope();
        c.fields().forEach(this::analyzeVarDecl);
        c.methods().forEach(this::analyzeFnDecl);
        symbolTable.exitScope();
    }

    private void analyzeIf(AST.Stmt.If i) {
        String condType = analyzeExpr(i.condition());
        if (!"bool".equals(condType) && !"unknown".equals(condType)) {
            error("'if' condition must be bool, got '%s'".formatted(condType), i.line());
        }
        analyzeStmt(i.thenBranch());
        if (i.elseBranch() != null) analyzeStmt(i.elseBranch());
    }

    private void analyzeWhile(AST.Stmt.While w) {
        analyzeExpr(w.condition());
        loopDepth++;
        analyzeBlock(w.body());
        loopDepth--;
    }

    private void analyzeFor(AST.Stmt.For f) {
        symbolTable.enterScope();
        if (f.init() != null)      analyzeStmt(f.init());
        if (f.condition() != null)  analyzeExpr(f.condition());
        if (f.increment() != null)  analyzeExpr(f.increment());
        loopDepth++;
        f.body().stmts().forEach(this::analyzeStmt);
        loopDepth--;
        symbolTable.exitScope();
    }

    private void analyzeReturn(AST.Stmt.Return r) {
        if (currentFnReturnType == null) { error("'return' outside function", r.line()); return; }
        String retType = r.value() != null ? analyzeExpr(r.value()) : "void";
        if (!isCompatible(retType, currentFnReturnType)) {
            error("Return type mismatch: function returns '%s', got '%s'"
                .formatted(currentFnReturnType, retType), r.line());
        }
    }

    // ── Expression Analysis & Type Inference ──────────────────────────────────

    private String analyzeExpr(AST.Expr expr) {
        String type = inferType(expr);
        typeMap.put(expr, type);
        return type;
    }

    private String inferType(AST.Expr expr) {
        return switch (expr) {
            case AST.Expr.Literal l -> switch (l.value()) {
                case Integer ignored -> "int";
                case Double  ignored -> "float";
                case Boolean ignored -> "bool";
                case String  ignored -> "string";
                case null    -> "null";
                default -> "unknown";
            };
            case AST.Expr.Identifier id -> {
                var sym = symbolTable.resolve(id.name());
                if (sym.isEmpty()) { error("Undefined variable '%s'".formatted(id.name()), id.line()); yield "unknown"; }
                yield sym.get().type();
            }
            case AST.Expr.BinaryOp b -> inferBinaryType(b);
            case AST.Expr.UnaryOp u  -> {
                String operandType = analyzeExpr(u.operand());
                yield switch (u.op()) {
                    case "!", "not" -> "bool";
                    case "-", "~"   -> operandType;
                    case "++", "--" -> operandType;
                    default -> "unknown";
                };
            }
            case AST.Expr.Assignment a -> {
                var sym = symbolTable.resolve(a.target() instanceof AST.Expr.Identifier id ? id.name() : "");
                if (sym.isPresent() && sym.get().isConst()) {
                    error("Cannot assign to constant '%s'".formatted(sym.get().name()), a.line());
                }
                yield analyzeExpr(a.value());
            }
            case AST.Expr.Call c -> {
                AST.Expr callee = c.callee();
                if (callee instanceof AST.Expr.Identifier id) {
                    var sym = symbolTable.resolve(id.name());
                    if (sym.isEmpty()) { error("Undefined function '%s'".formatted(id.name()), c.line()); yield "unknown"; }
                    // parse return type from signature fn(a,b)->retType
                    String sig = sym.get().type();
                    yield sig.contains("->") ? sig.substring(sig.lastIndexOf("->") + 2) : "unknown";
                }
                yield "unknown";
            }
            case AST.Expr.ArrayLiteral a -> {
                a.elements().forEach(this::analyzeExpr);
                yield "array";
            }
            case AST.Expr.Index i -> { analyzeExpr(i.object()); analyzeExpr(i.index()); yield "unknown"; }
            case AST.Expr.Member m -> { analyzeExpr(m.object()); yield "unknown"; }
            case AST.Expr.Ternary t -> {
                analyzeExpr(t.condition());
                String tt = analyzeExpr(t.thenExpr());
                analyzeExpr(t.elseExpr());
                yield tt;
            }
            case AST.Expr.New n -> n.className();
        };
    }

    private String inferBinaryType(AST.Expr.BinaryOp b) {
        String lt = analyzeExpr(b.left());
        String rt = analyzeExpr(b.right());
        return switch (b.op()) {
            case "+", "-", "*", "/", "%", "**" -> {
                if (isNumeric(lt) && isNumeric(rt)) {
                    yield (lt.equals("float") || rt.equals("float")) ? "float" : "int";
                }
                if ("+".equals(b.op()) && "string".equals(lt)) yield "string"; // string concat
                error("Invalid operand types '%s' %s '%s'".formatted(lt, b.op(), rt), b.line());
                yield "unknown";
            }
            case "==", "!=", "<", "<=", ">", ">=" -> "bool";
            case "&&", "||"                        -> "bool";
            case "&", "|", "^", "<<", ">>"        -> "int";
            default -> "unknown";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isNumeric(String type)  { return "int".equals(type) || "float".equals(type); }

    private boolean isCompatible(String actual, String expected) {
        if (expected.equals("unknown") || actual.equals("unknown")) return true;
        if (actual.equals(expected)) return true;
        return actual.equals("int") && expected.equals("float"); // widening
    }

    private void error(String message, int line) {
        errors.add("Semantic error [L%d]: %s".formatted(line, message));
    }

    private void registerBuiltins() {
        symbolTable.define(new Symbol("print",  "fn(string)->void",  SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("len",    "fn(array)->int",    SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("push",   "fn(array,any)->void", SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("pop",    "fn(array)->any",    SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("str",    "fn(any)->string",   SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("int",    "fn(any)->int",      SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("float",  "fn(any)->float",    SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("sqrt",   "fn(float)->float",  SymbolKind.FUNCTION, true, 0));
        symbolTable.define(new Symbol("abs",    "fn(float)->float",  SymbolKind.FUNCTION, true, 0));
    }
}
