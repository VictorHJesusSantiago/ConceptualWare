package com.conceptualware.core.compiler;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Concept #10 — Compiler / Interpreter Pipeline:
 *   Lexer → Parser → AST → SemanticAnalyzer → IRGenerator → Optimizer → Interpreter
 */
@DisplayName("Category 10 — Compiler/Interpreter Pipeline")
class CompilerTest {

    @Nested @DisplayName("Lexer (Tokenizer)")
    class LexerTests {

        @Test @DisplayName("tokenizes integer literal")
        void tokenizesInteger() {
            var tokens = new Lexer("42").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Token.TokenType.INTEGER_LITERAL);
            assertThat(tokens.get(0).literal()).isEqualTo(42);
        }

        @Test @DisplayName("tokenizes float literal")
        void tokenizesFloat() {
            var tokens = new Lexer("3.14").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Token.TokenType.FLOAT_LITERAL);
            assertThat((Double) tokens.get(0).literal()).isCloseTo(3.14, within(1e-9));
        }

        @Test @DisplayName("tokenizes string with escape sequences")
        void tokenizesString() {
            var tokens = new Lexer("\"hello\\nworld\"").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(Token.TokenType.STRING_LITERAL);
            assertThat((String) tokens.get(0).literal()).isEqualTo("hello\nworld");
        }

        @Test @DisplayName("recognizes all keywords")
        void recognizesKeywords() {
            var source = "var const fn return if else while for break continue print true false null";
            var tokens = new Lexer(source).tokenize();
            assertThat(tokens.stream().map(Token::type).toList()).contains(
                Token.TokenType.VAR, Token.TokenType.CONST, Token.TokenType.FN,
                Token.TokenType.RETURN, Token.TokenType.IF, Token.TokenType.ELSE,
                Token.TokenType.WHILE, Token.TokenType.FOR, Token.TokenType.BREAK,
                Token.TokenType.CONTINUE, Token.TokenType.PRINT, Token.TokenType.TRUE,
                Token.TokenType.FALSE, Token.TokenType.NULL
            );
        }

        @Test @DisplayName("tokenizes multi-character operators")
        void tokenizesOperators() {
            var tokens = new Lexer("== != <= >= && || ++ -- += ->").tokenize();
            var types = tokens.stream().map(Token::type).toList();
            assertThat(types).contains(
                Token.TokenType.EQUAL_EQUAL, Token.TokenType.BANG_EQUAL,
                Token.TokenType.LESS_EQUAL, Token.TokenType.GREATER_EQUAL,
                Token.TokenType.AND, Token.TokenType.OR,
                Token.TokenType.PLUS_PLUS, Token.TokenType.MINUS_MINUS,
                Token.TokenType.PLUS_EQUAL, Token.TokenType.ARROW
            );
        }

        @Test @DisplayName("skips single-line comments")
        void skipsComments() {
            var tokens = new Lexer("42 // this is a comment\n99").tokenize();
            assertThat(tokens.stream().filter(t -> t.type() == Token.TokenType.INTEGER_LITERAL).count()).isEqualTo(2);
            assertThat(tokens.get(0).literal()).isEqualTo(42);
            assertThat(tokens.get(1).literal()).isEqualTo(99);
        }

        @Test @DisplayName("tracks line and column numbers")
        void tracksPosition() {
            var tokens = new Lexer("x\ny").tokenize();
            assertThat(tokens.get(0).line()).isEqualTo(1);
            assertThat(tokens.get(1).line()).isEqualTo(2);
        }

        @Test @DisplayName("tokenizes hex literals")
        void tokenizesHex() {
            var tokens = new Lexer("0xFF").tokenize();
            assertThat(tokens.get(0).literal()).isEqualTo(255);
        }

        @Test @DisplayName("tokenizes binary literals")
        void tokenizesBinary() {
            var tokens = new Lexer("0b1010").tokenize();
            assertThat(tokens.get(0).literal()).isEqualTo(10);
        }
    }

    @Nested @DisplayName("Parser (Syntactic Analysis)")
    class ParserTests {

        private AST.Stmt.Program parse(String source) {
            var tokens = new Lexer(source).tokenize();
            return new Parser(tokens).parse();
        }

        @Test @DisplayName("parses variable declaration")
        void parsesVarDecl() {
            var prog = parse("var x: int = 42;");
            assertThat(prog.body()).hasSize(1);
            assertThat(prog.body().get(0)).isInstanceOf(AST.Stmt.VarDecl.class);
            var decl = (AST.Stmt.VarDecl) prog.body().get(0);
            assertThat(decl.name()).isEqualTo("x");
            assertThat(decl.typeAnnotation()).isEqualTo("int");
            assertThat(decl.isConst()).isFalse();
        }

        @Test @DisplayName("parses const declaration")
        void parsesConstDecl() {
            var prog = parse("const PI: float = 3.14;");
            var decl = (AST.Stmt.VarDecl) prog.body().get(0);
            assertThat(decl.isConst()).isTrue();
            assertThat(decl.name()).isEqualTo("PI");
        }

        @Test @DisplayName("parses function declaration")
        void parsesFnDecl() {
            var prog = parse("fn add(a: int, b: int) -> int { return a + b; }");
            var fn = (AST.Stmt.FnDecl) prog.body().get(0);
            assertThat(fn.name()).isEqualTo("add");
            assertThat(fn.params()).hasSize(2);
            assertThat(fn.returnType()).isEqualTo("int");
        }

        @Test @DisplayName("parses if-else")
        void parsesIfElse() {
            var prog = parse("if (x > 0) { print(x); } else { print(0); }");
            assertThat(prog.body().get(0)).isInstanceOf(AST.Stmt.If.class);
            var ifStmt = (AST.Stmt.If) prog.body().get(0);
            assertThat(ifStmt.elseBranch()).isNotNull();
        }

        @Test @DisplayName("parses while loop")
        void parsesWhile() {
            var prog = parse("while (i < 10) { i += 1; }");
            assertThat(prog.body().get(0)).isInstanceOf(AST.Stmt.While.class);
        }

        @Test @DisplayName("binary ops respect precedence")
        void parsesPrecedence() {
            // 2 + 3 * 4 should parse as 2 + (3 * 4), not (2+3) * 4
            var prog = parse("var x = 2 + 3 * 4;");
            var decl = (AST.Stmt.VarDecl) prog.body().get(0);
            var add = (AST.Expr.BinaryOp) decl.initializer();
            assertThat(add.op()).isEqualTo("+");
            assertThat(add.right()).isInstanceOf(AST.Expr.BinaryOp.class);
            var mul = (AST.Expr.BinaryOp) add.right();
            assertThat(mul.op()).isEqualTo("*");
        }

        @Test @DisplayName("parse errors are collected")
        void collectsErrors() {
            var tokens = new Lexer("var x = ;").tokenize();
            var parser = new Parser(tokens);
            parser.parse();
            assertThat(parser.errors()).isNotEmpty();
        }
    }

    @Nested @DisplayName("Semantic Analyzer")
    class SemanticTests {

        private SemanticAnalyzer.AnalysisResult analyze(String source) {
            var tokens  = new Lexer(source).tokenize();
            var program = new Parser(tokens).parse();
            return new SemanticAnalyzer().analyze(program);
        }

        @Test @DisplayName("accepts valid program")
        void acceptsValidProgram() {
            var result = analyze("var x: int = 42; var y: int = x + 1;");
            assertThat(result.hasErrors()).isFalse();
        }

        @Test @DisplayName("detects undefined variable")
        void detectsUndefined() {
            var result = analyze("var x: int = undeclared;");
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors().get(0)).contains("undeclared");
        }

        @Test @DisplayName("detects assignment to const")
        void detectsConstAssignment() {
            var result = analyze("const X: int = 5; X = 10;");
            assertThat(result.hasErrors()).isTrue();
        }

        @Test @DisplayName("detects duplicate declaration in same scope")
        void detectsDuplicate() {
            var result = analyze("var x: int = 1; var x: int = 2;");
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.errors().get(0)).contains("x");
        }

        @Test @DisplayName("detects type mismatch")
        void detectsTypeMismatch() {
            var result = analyze("var x: int = 3.14;");
            assertThat(result.hasErrors()).isTrue();
        }

        @Test @DisplayName("allows int-to-float widening")
        void allowsWidening() {
            var result = analyze("var x: float = 42;");
            assertThat(result.hasErrors()).isFalse(); // int → float is ok
        }

        @Test @DisplayName("detects break outside loop")
        void detectsBreakOutsideLoop() {
            var result = analyze("break;");
            assertThat(result.hasErrors()).isTrue();
        }
    }

    @Nested @DisplayName("IR Generator")
    class IRGeneratorTests {

        private List<IR> generateIR(String source) {
            var tokens   = new Lexer(source).tokenize();
            var program  = new Parser(tokens).parse();
            return new IRGenerator().generate(program);
        }

        @Test @DisplayName("generates ALLOC + COPY for var declaration")
        void generatesVarDecl() {
            var ir = generateIR("var x = 42;");
            assertThat(ir.stream().anyMatch(i -> i instanceof IR.Alloc a && a.name().equals("x"))).isTrue();
            assertThat(ir.stream().anyMatch(i -> i instanceof IR.Copy c && c.target().equals("x") && c.source().equals("42"))).isTrue();
        }

        @Test @DisplayName("generates BINOP for arithmetic")
        void generatesBinaryOp() {
            var ir = generateIR("var z = 3 + 4;");
            assertThat(ir.stream().anyMatch(i -> i instanceof IR.BinOp b && b.op().equals("+"))).isTrue();
        }

        @Test @DisplayName("generates labels and GOTO for if-else")
        void generatesIfElse() {
            var ir = generateIR("if (x > 0) { var a = 1; } else { var b = 2; }");
            assertThat(ir.stream().anyMatch(i -> i instanceof IR.Label)).isTrue();
            assertThat(ir.stream().anyMatch(i -> i instanceof IR.Goto)).isTrue();
        }

        @Test @DisplayName("generates while loop structure")
        void generatesWhile() {
            var ir = generateIR("while (i > 0) { i = i - 1; }");
            // Head label + conditional goto + body + goto head + exit label
            long labels = ir.stream().filter(i -> i instanceof IR.Label).count();
            assertThat(labels).isGreaterThanOrEqualTo(2);
        }

        @Test @DisplayName("generates PARAM + CALL for function calls")
        void generatesFunctionCall() {
            var ir = generateIR("fn foo(a: int) -> int { return a; } foo(5);");
            assertThat(ir.stream().anyMatch(i -> i instanceof IR.Param p && p.value().equals("5"))).isTrue();
            assertThat(ir.stream().anyMatch(i -> i instanceof IR.Call c && c.function().equals("foo"))).isTrue();
        }
    }

    @Nested @DisplayName("Optimizer")
    class OptimizerTests {

        @Test @DisplayName("constant folding: 3 + 4 → 7")
        void constantFolding() {
            var ir = List.of(new IR.BinOp("t0", "3", "+", "4"));
            var stats = new Optimizer().optimize(ir);
            assertThat(stats.constantsFolded()).isGreaterThan(0);
            assertThat(stats.optimizedIR().stream()
                .anyMatch(i -> i instanceof IR.Copy c && c.source().equals("7")))
                .isTrue();
        }

        @Test @DisplayName("algebraic simplification: x + 0 → x")
        void algebraicSimplification() {
            var ir = List.of(new IR.BinOp("t0", "x", "+", "0"));
            var stats = new Optimizer().optimize(ir);
            assertThat(stats.algebraicSimplifications()).isGreaterThan(0);
        }

        @Test @DisplayName("algebraic simplification: x * 0 → 0")
        void multiplyByZero() {
            var ir = List.of(new IR.BinOp("t0", "x", "*", "0"));
            var stats = new Optimizer().optimize(ir);
            assertThat(stats.algebraicSimplifications()).isGreaterThan(0);
        }

        @Test @DisplayName("CSE eliminates duplicate computation")
        void commonSubexpressionElimination() {
            var ir = List.of(
                new IR.BinOp("t0", "a", "+", "b"),
                new IR.BinOp("t1", "a", "+", "b")  // same expression
            );
            var stats = new Optimizer().optimize(ir);
            assertThat(stats.cseSubstitutions()).isGreaterThan(0);
        }

        @Test @DisplayName("optimization stats report")
        void statsReport() {
            var ir = List.of(
                new IR.BinOp("t0", "2", "*", "3"),  // constant fold
                new IR.BinOp("t1", "t0", "+", "0")  // algebraic simplify
            );
            var stats = new Optimizer().optimize(ir);
            assertThat(stats.totalSavings()).isGreaterThan(0);
        }
    }

    @Nested @DisplayName("Interpreter (REPL)")
    class InterpreterTests {

        private final Interpreter interpreter = new Interpreter();

        @Test @DisplayName("evaluates arithmetic expression")
        void evaluatesArithmetic() {
            var result = interpreter.execute("print(2 + 3 * 4);");
            assertThat(result.trim()).isEqualTo("14");
        }

        @Test @DisplayName("variable declaration and use")
        void variableDeclaration() {
            var result = interpreter.execute("var x = 10; var y = x + 5; print(y);");
            assertThat(result.trim()).isEqualTo("15");
        }

        @Test @DisplayName("if-else branching")
        void ifElse() {
            var result = interpreter.execute("var x = 7; if (x > 5) { print(1); } else { print(0); }");
            assertThat(result.trim()).isEqualTo("1");
        }

        @Test @DisplayName("while loop")
        void whileLoop() {
            var result = interpreter.execute("""
                var sum = 0;
                var i = 1;
                while (i <= 5) {
                    sum = sum + i;
                    i = i + 1;
                }
                print(sum);
                """);
            assertThat(result.trim()).isEqualTo("15");
        }

        @Test @DisplayName("function definition and call")
        void functionCall() {
            var result = interpreter.execute("""
                fn factorial(n: int) -> int {
                    if (n <= 1) { return 1; }
                    return n * factorial(n - 1);
                }
                print(factorial(6));
                """);
            assertThat(result.trim()).isEqualTo("720");
        }

        @Test @DisplayName("string concatenation")
        void stringConcat() {
            var result = interpreter.execute("var s = \"hello\" + \" \" + \"world\"; print(s);");
            assertThat(result.trim()).isEqualTo("hello world");
        }

        @Test @DisplayName("fibonacci via recursion")
        void fibonacci() {
            var result = interpreter.execute("""
                fn fib(n: int) -> int {
                    if (n <= 1) { return n; }
                    return fib(n-1) + fib(n-2);
                }
                print(fib(10));
                """);
            assertThat(result.trim()).isEqualTo("55");
        }

        @Test @DisplayName("full pipeline: lex → parse → analyze → interpret")
        void fullPipeline() {
            var source = """
                fn max(a: int, b: int) -> int {
                    if (a > b) { return a; }
                    return b;
                }
                var result = max(42, 17);
                print(result);
                """;
            assertThat(interpreter.execute(source).trim()).isEqualTo("42");
        }

        @Test @DisplayName("pretty printer round-trips simple program")
        void prettyPrinter() {
            var source = "var x: int = 42;";
            var tokens  = new Lexer(source).tokenize();
            var program = new Parser(tokens).parse();
            String printed = AST.prettyPrint(program);
            assertThat(printed).contains("var x").contains("42");
        }

        @Test @DisplayName("IR dump is non-empty for non-trivial program")
        void irDump() {
            var source = "fn add(a: int, b: int) -> int { return a + b; }";
            var tokens  = new Lexer(source).tokenize();
            var program = new Parser(tokens).parse();
            var ir = new IRGenerator().generate(program);
            assertThat(IRGenerator.dump(ir)).isNotBlank();
            assertThat(ir).isNotEmpty();
        }
    }
}
