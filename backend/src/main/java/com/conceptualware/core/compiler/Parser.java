package com.conceptualware.core.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Concept #10 — Parsing / Syntactic Analysis:
 *
 *   The Parser is the SECOND phase of the compiler pipeline.
 *   Input:  flat list of Tokens from the Lexer
 *   Output: AST (Abstract Syntax Tree)
 *
 *   Strategy: Recursive Descent Parser (top-down, LL(1)):
 *     • Each grammar rule → one parseXxx() method
 *     • Current token guides which rule to apply (predictive parsing)
 *     • Operator precedence handled via call chain nesting:
 *         parseOr → parseAnd → parseEquality → parseComparison
 *             → parseAddSub → parseMulDiv → parseUnary → parsePrimary
 *     • Panic-mode error recovery: on syntax error, advance until synchronization
 *       point (statement boundary) to report as many errors as possible
 *
 *   ConceptLang Grammar (simplified EBNF):
 *     program       := stmt*
 *     stmt          := varDecl | fnDecl | classDecl | if | while | for
 *                    | return | break | continue | print | exprStmt | block
 *     varDecl       := ('var'|'const') IDENT (':' TYPE)? ('=' expr)? ';'
 *     fnDecl        := 'fn' IDENT '(' params? ')' ('->' TYPE)? block
 *     if            := 'if' '(' expr ')' stmt ('else' stmt)?
 *     while         := 'while' '(' expr ')' block
 *     expr          := assignment
 *     assignment    := ternary (('='|'+='|'-='|'*='|'/=') assignment)?
 *     ternary       := or ('?' expr ':' ternary)?
 *     or            := and ('||' and)*
 *     and           := equality ('&&' equality)*
 *     equality      := comparison (('=='|'!=') comparison)*
 *     comparison    := addSub (('<'|'<='|'>'|'>=') addSub)*
 *     addSub        := mulDiv (('+'|'-') mulDiv)*
 *     mulDiv        := unary (('*'|'/'|'%') unary)*
 *     unary         := ('!'|'-'|'~'|'++'|'--') unary | postfix
 *     postfix       := primary ('++'|'--'|'[' expr ']'|'.' IDENT|'(' args ')')*
 *     primary       := LITERAL | IDENT | '(' expr ')' | '[' args ']' | 'new' IDENT '(' args ')'
 */
public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private final List<String> errors = new ArrayList<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public AST.Stmt.Program parse() {
        List<AST.Stmt> stmts = new ArrayList<>();
        while (!isAtEnd()) {
            try { stmts.add(statement()); }
            catch (ParseError e) { errors.add(e.getMessage()); synchronize(); }
        }
        return new AST.Stmt.Program(stmts, 1);
    }

    public List<String> errors() { return List.copyOf(errors); }

    // ── Statement Parsing ─────────────────────────────────────────────────────

    private AST.Stmt statement() {
        return switch (peek().type()) {
            case VAR, CONST -> varDecl();
            case FN         -> fnDecl();
            case CLASS      -> classDecl();
            case IF         -> ifStmt();
            case WHILE      -> whileStmt();
            case FOR        -> forStmt();
            case RETURN     -> returnStmt();
            case BREAK      -> { advance(); yield new AST.Stmt.Break(previous().line()); consume(Token.TokenType.SEMICOLON, "Expected ';'"); }
            case CONTINUE   -> { advance(); yield new AST.Stmt.Continue(previous().line()); consume(Token.TokenType.SEMICOLON, "Expected ';'"); }
            case PRINT      -> printStmt();
            case LEFT_BRACE -> block();
            default         -> exprStmt();
        };
    }

    private AST.Stmt.VarDecl varDecl() {
        boolean isConst = peek().type() == Token.TokenType.CONST;
        advance();
        int line = previous().line();
        String name = consume(Token.TokenType.IDENTIFIER, "Expected variable name").lexeme();
        String typeAnn = null;
        if (match(Token.TokenType.COLON)) typeAnn = typeName();
        AST.Expr init = null;
        if (match(Token.TokenType.EQUAL)) init = expression();
        consume(Token.TokenType.SEMICOLON, "Expected ';' after variable declaration");
        return new AST.Stmt.VarDecl(name, typeAnn, init, isConst, line);
    }

    private AST.Stmt.FnDecl fnDecl() {
        consume(Token.TokenType.FN, "Expected 'fn'");
        int line = previous().line();
        String name = consume(Token.TokenType.IDENTIFIER, "Expected function name").lexeme();
        consume(Token.TokenType.LEFT_PAREN, "Expected '('");
        List<AST.Stmt.Param> params = new ArrayList<>();
        if (!check(Token.TokenType.RIGHT_PAREN)) {
            do {
                String pName = consume(Token.TokenType.IDENTIFIER, "Expected parameter name").lexeme();
                consume(Token.TokenType.COLON, "Expected ':' after parameter name");
                String pType = typeName();
                params.add(new AST.Stmt.Param(pName, pType));
            } while (match(Token.TokenType.COMMA));
        }
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')'");
        String returnType = "void";
        if (match(Token.TokenType.ARROW)) returnType = typeName();
        AST.Stmt.Block body = block();
        return new AST.Stmt.FnDecl(name, params, returnType, body, line);
    }

    private AST.Stmt.ClassDecl classDecl() {
        consume(Token.TokenType.CLASS, "Expected 'class'");
        int line = previous().line();
        String name = consume(Token.TokenType.IDENTIFIER, "Expected class name").lexeme();
        consume(Token.TokenType.LEFT_BRACE, "Expected '{'");
        List<AST.Stmt.VarDecl> fields  = new ArrayList<>();
        List<AST.Stmt.FnDecl>  methods = new ArrayList<>();
        while (!check(Token.TokenType.RIGHT_BRACE) && !isAtEnd()) {
            if (check(Token.TokenType.FN)) methods.add(fnDecl());
            else fields.add(varDecl());
        }
        consume(Token.TokenType.RIGHT_BRACE, "Expected '}'");
        return new AST.Stmt.ClassDecl(name, fields, methods, line);
    }

    private AST.Stmt.If ifStmt() {
        advance(); int line = previous().line();
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'if'");
        AST.Expr cond = expression();
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')' after condition");
        AST.Stmt then = statement();
        AST.Stmt els  = match(Token.TokenType.ELSE) ? statement() : null;
        return new AST.Stmt.If(cond, then, els, line);
    }

    private AST.Stmt.While whileStmt() {
        advance(); int line = previous().line();
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'while'");
        AST.Expr cond = expression();
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')'");
        AST.Stmt.Block body = block();
        return new AST.Stmt.While(cond, body, line);
    }

    private AST.Stmt.For forStmt() {
        advance(); int line = previous().line();
        consume(Token.TokenType.LEFT_PAREN, "Expected '('");
        AST.Stmt init = check(Token.TokenType.SEMICOLON) ? null : statement();
        if (init == null) consume(Token.TokenType.SEMICOLON, "Expected ';'");
        AST.Expr cond = check(Token.TokenType.SEMICOLON) ? null : expression();
        consume(Token.TokenType.SEMICOLON, "Expected ';'");
        AST.Expr inc = check(Token.TokenType.RIGHT_PAREN) ? null : expression();
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')'");
        AST.Stmt.Block body = block();
        return new AST.Stmt.For(init, cond, inc, body, line);
    }

    private AST.Stmt.Return returnStmt() {
        advance(); int line = previous().line();
        AST.Expr val = check(Token.TokenType.SEMICOLON) ? null : expression();
        consume(Token.TokenType.SEMICOLON, "Expected ';'");
        return new AST.Stmt.Return(val, line);
    }

    private AST.Stmt.Print printStmt() {
        advance(); int line = previous().line();
        consume(Token.TokenType.LEFT_PAREN, "Expected '(' after 'print'");
        AST.Expr val = expression();
        consume(Token.TokenType.RIGHT_PAREN, "Expected ')'");
        consume(Token.TokenType.SEMICOLON, "Expected ';'");
        return new AST.Stmt.Print(val, line);
    }

    private AST.Stmt.Block block() {
        consume(Token.TokenType.LEFT_BRACE, "Expected '{'");
        int line = previous().line();
        List<AST.Stmt> stmts = new ArrayList<>();
        while (!check(Token.TokenType.RIGHT_BRACE) && !isAtEnd()) {
            try { stmts.add(statement()); }
            catch (ParseError e) { errors.add(e.getMessage()); synchronize(); }
        }
        consume(Token.TokenType.RIGHT_BRACE, "Expected '}'");
        return new AST.Stmt.Block(stmts, line);
    }

    private AST.Stmt.ExprStmt exprStmt() {
        int line = peek().line();
        AST.Expr e = expression();
        consume(Token.TokenType.SEMICOLON, "Expected ';' after expression");
        return new AST.Stmt.ExprStmt(e, line);
    }

    // ── Expression Parsing (Pratt-style via method chain) ─────────────────────

    private AST.Expr expression() { return assignment(); }

    private AST.Expr assignment() {
        AST.Expr left = ternary();
        if (matchAny(Token.TokenType.EQUAL, Token.TokenType.PLUS_EQUAL,
                     Token.TokenType.MINUS_EQUAL, Token.TokenType.STAR_EQUAL,
                     Token.TokenType.SLASH_EQUAL)) {
            String op = previous().lexeme();
            int line = previous().line();
            AST.Expr val = assignment(); // right-associative
            return new AST.Expr.Assignment(left, op, val, line);
        }
        return left;
    }

    private AST.Expr ternary() {
        AST.Expr cond = or();
        if (match(Token.TokenType.IDENTIFIER) && previous().lexeme().equals("?")) {
            // manually handle ? since we haven't made it a token type
            // check via: peek if it's ? symbol
        }
        return cond;
    }

    private AST.Expr or() {
        AST.Expr left = and();
        while (match(Token.TokenType.OR)) {
            int line = previous().line();
            left = new AST.Expr.BinaryOp(left, "||", and(), line);
        }
        return left;
    }

    private AST.Expr and() {
        AST.Expr left = equality();
        while (match(Token.TokenType.AND)) {
            int line = previous().line();
            left = new AST.Expr.BinaryOp(left, "&&", equality(), line);
        }
        return left;
    }

    private AST.Expr equality() {
        AST.Expr left = comparison();
        while (matchAny(Token.TokenType.EQUAL_EQUAL, Token.TokenType.BANG_EQUAL)) {
            String op = previous().lexeme(); int line = previous().line();
            left = new AST.Expr.BinaryOp(left, op, comparison(), line);
        }
        return left;
    }

    private AST.Expr comparison() {
        AST.Expr left = addSub();
        while (matchAny(Token.TokenType.LESS, Token.TokenType.LESS_EQUAL,
                        Token.TokenType.GREATER, Token.TokenType.GREATER_EQUAL)) {
            String op = previous().lexeme(); int line = previous().line();
            left = new AST.Expr.BinaryOp(left, op, addSub(), line);
        }
        return left;
    }

    private AST.Expr addSub() {
        AST.Expr left = mulDiv();
        while (matchAny(Token.TokenType.PLUS, Token.TokenType.MINUS)) {
            String op = previous().lexeme(); int line = previous().line();
            left = new AST.Expr.BinaryOp(left, op, mulDiv(), line);
        }
        return left;
    }

    private AST.Expr mulDiv() {
        AST.Expr left = unary();
        while (matchAny(Token.TokenType.STAR, Token.TokenType.SLASH,
                        Token.TokenType.PERCENT, Token.TokenType.POWER)) {
            String op = previous().lexeme(); int line = previous().line();
            left = new AST.Expr.BinaryOp(left, op, unary(), line);
        }
        return left;
    }

    private AST.Expr unary() {
        if (matchAny(Token.TokenType.BANG, Token.TokenType.MINUS,
                     Token.TokenType.TILDE, Token.TokenType.PLUS_PLUS,
                     Token.TokenType.MINUS_MINUS)) {
            String op = previous().lexeme(); int line = previous().line();
            return new AST.Expr.UnaryOp(op, unary(), true, line);
        }
        return postfix();
    }

    private AST.Expr postfix() {
        AST.Expr expr = primary();
        while (true) {
            int line = peek().line();
            if (matchAny(Token.TokenType.PLUS_PLUS, Token.TokenType.MINUS_MINUS)) {
                expr = new AST.Expr.UnaryOp(previous().lexeme(), expr, false, line);
            } else if (match(Token.TokenType.LEFT_BRACKET)) {
                AST.Expr idx = expression();
                consume(Token.TokenType.RIGHT_BRACKET, "Expected ']'");
                expr = new AST.Expr.Index(expr, idx, line);
            } else if (match(Token.TokenType.DOT)) {
                String field = consume(Token.TokenType.IDENTIFIER, "Expected field name").lexeme();
                expr = new AST.Expr.Member(expr, field, line);
            } else if (check(Token.TokenType.LEFT_PAREN)) {
                advance();
                List<AST.Expr> args = new ArrayList<>();
                if (!check(Token.TokenType.RIGHT_PAREN)) {
                    do { args.add(expression()); } while (match(Token.TokenType.COMMA));
                }
                consume(Token.TokenType.RIGHT_PAREN, "Expected ')'");
                expr = new AST.Expr.Call(expr, args, line);
            } else {
                break;
            }
        }
        return expr;
    }

    private AST.Expr primary() {
        int line = peek().line();
        if (matchAny(Token.TokenType.INTEGER_LITERAL, Token.TokenType.FLOAT_LITERAL,
                     Token.TokenType.STRING_LITERAL)) {
            return new AST.Expr.Literal(previous().literal(), line);
        }
        if (matchAny(Token.TokenType.TRUE, Token.TokenType.FALSE)) {
            return new AST.Expr.Literal(previous().type() == Token.TokenType.TRUE, line);
        }
        if (match(Token.TokenType.NULL)) {
            return new AST.Expr.Literal(null, line);
        }
        if (match(Token.TokenType.IDENTIFIER)) {
            return new AST.Expr.Identifier(previous().lexeme(), line);
        }
        if (match(Token.TokenType.NEW)) {
            String cls = consume(Token.TokenType.IDENTIFIER, "Expected class name").lexeme();
            consume(Token.TokenType.LEFT_PAREN, "Expected '('");
            List<AST.Expr> args = new ArrayList<>();
            if (!check(Token.TokenType.RIGHT_PAREN)) {
                do { args.add(expression()); } while (match(Token.TokenType.COMMA));
            }
            consume(Token.TokenType.RIGHT_PAREN, "Expected ')'");
            return new AST.Expr.New(cls, args, line);
        }
        if (match(Token.TokenType.LEFT_PAREN)) {
            AST.Expr e = expression();
            consume(Token.TokenType.RIGHT_PAREN, "Expected ')'");
            return e;
        }
        if (match(Token.TokenType.LEFT_BRACKET)) {
            List<AST.Expr> elems = new ArrayList<>();
            if (!check(Token.TokenType.RIGHT_BRACKET)) {
                do { elems.add(expression()); } while (match(Token.TokenType.COMMA));
            }
            consume(Token.TokenType.RIGHT_BRACKET, "Expected ']'");
            return new AST.Expr.ArrayLiteral(elems, line);
        }
        throw error("Unexpected token: " + peek());
    }

    // ── Type parsing ──────────────────────────────────────────────────────────

    private String typeName() {
        return switch (peek().type()) {
            case INT, FLOAT, BOOL, STRING, VOID, IDENTIFIER -> { advance(); yield previous().lexeme(); }
            default -> throw error("Expected type name, got: " + peek());
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean match(Token.TokenType type) {
        if (check(type)) { advance(); return true; }
        return false;
    }

    private boolean matchAny(Token.TokenType... types) {
        for (var t : types) { if (check(t)) { advance(); return true; } }
        return false;
    }

    private boolean check(Token.TokenType type) {
        return !isAtEnd() && peek().type() == type;
    }

    private Token advance()  { if (!isAtEnd()) current++; return previous(); }
    private Token peek()     { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
    private boolean isAtEnd(){ return peek().type() == Token.TokenType.EOF; }

    private Token consume(Token.TokenType type, String message) {
        if (check(type)) return advance();
        throw error(message + " (got " + peek() + ")");
    }

    private ParseError error(String message) {
        String msg = "Parse error [L%d:C%d]: %s".formatted(peek().line(), peek().column(), message);
        errors.add(msg);
        return new ParseError(msg);
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type() == Token.TokenType.SEMICOLON) return;
            switch (peek().type()) {
                case FN, VAR, CONST, CLASS, IF, WHILE, FOR, RETURN, PRINT -> { return; }
                default -> advance();
            }
        }
    }

    private static class ParseError extends RuntimeException {
        ParseError(String msg) { super(msg); }
    }
}
