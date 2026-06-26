package com.conceptualware.core.compiler;

/**
 * Concept #10 — Lexer / Tokenizer:
 *   A token is the smallest meaningful unit in source code.
 *   The lexer converts a raw character stream → sequence of tokens.
 *
 *   Token types cover: literals, identifiers, keywords, operators, delimiters.
 *   Each token stores its type, lexeme (raw text), and source position for
 *   precise error messages ("line 3, column 12: expected ';'").
 */
public record Token(TokenType type, String lexeme, Object literal, int line, int column) {

    public enum TokenType {
        // ── Literals ──────────────────────────────────────────────────────────
        INTEGER_LITERAL, FLOAT_LITERAL, STRING_LITERAL, BOOL_LITERAL, NULL_LITERAL,

        // ── Identifiers & Keywords ────────────────────────────────────────────
        IDENTIFIER,
        // Keywords (reserved words — cannot be used as identifiers)
        VAR, CONST, FN, RETURN, IF, ELSE, WHILE, FOR, BREAK, CONTINUE,
        PRINT, TRUE, FALSE, NULL, INT, FLOAT, BOOL, STRING, VOID,
        CLASS, NEW, THIS, IMPORT,

        // ── Arithmetic Operators ──────────────────────────────────────────────
        PLUS, MINUS, STAR, SLASH, PERCENT, POWER,
        PLUS_PLUS, MINUS_MINUS,            // ++ --
        PLUS_EQUAL, MINUS_EQUAL,           // += -=
        STAR_EQUAL, SLASH_EQUAL,           // *= /=

        // ── Comparison Operators ──────────────────────────────────────────────
        EQUAL_EQUAL, BANG_EQUAL,           // == !=
        LESS, LESS_EQUAL,                  // < <=
        GREATER, GREATER_EQUAL,            // > >=

        // ── Logical Operators ─────────────────────────────────────────────────
        AND, OR, BANG,                     // && || !

        // ── Bitwise Operators ─────────────────────────────────────────────────
        AMPERSAND, PIPE, CARET, TILDE,    // & | ^ ~
        LEFT_SHIFT, RIGHT_SHIFT,           // << >>

        // ── Assignment ────────────────────────────────────────────────────────
        EQUAL,                             // =

        // ── Delimiters ────────────────────────────────────────────────────────
        LEFT_PAREN, RIGHT_PAREN,           // ( )
        LEFT_BRACE, RIGHT_BRACE,           // { }
        LEFT_BRACKET, RIGHT_BRACKET,       // [ ]
        SEMICOLON, COLON, COMMA, DOT,      // ; : , .
        ARROW,                             // ->

        // ── Special ───────────────────────────────────────────────────────────
        EOF, ERROR
    }

    private static final java.util.Map<String, TokenType> KEYWORDS = java.util.Map.ofEntries(
        java.util.Map.entry("var",      TokenType.VAR),
        java.util.Map.entry("const",    TokenType.CONST),
        java.util.Map.entry("fn",       TokenType.FN),
        java.util.Map.entry("return",   TokenType.RETURN),
        java.util.Map.entry("if",       TokenType.IF),
        java.util.Map.entry("else",     TokenType.ELSE),
        java.util.Map.entry("while",    TokenType.WHILE),
        java.util.Map.entry("for",      TokenType.FOR),
        java.util.Map.entry("break",    TokenType.BREAK),
        java.util.Map.entry("continue", TokenType.CONTINUE),
        java.util.Map.entry("print",    TokenType.PRINT),
        java.util.Map.entry("true",     TokenType.TRUE),
        java.util.Map.entry("false",    TokenType.FALSE),
        java.util.Map.entry("null",     TokenType.NULL),
        java.util.Map.entry("int",      TokenType.INT),
        java.util.Map.entry("float",    TokenType.FLOAT),
        java.util.Map.entry("bool",     TokenType.BOOL),
        java.util.Map.entry("string",   TokenType.STRING),
        java.util.Map.entry("void",     TokenType.VOID),
        java.util.Map.entry("class",    TokenType.CLASS),
        java.util.Map.entry("new",      TokenType.NEW),
        java.util.Map.entry("this",     TokenType.THIS)
    );

    public static TokenType keyword(String text) {
        return KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
    }

    @Override public String toString() {
        return "[%s '%s' L%d:C%d]".formatted(type, lexeme, line, column);
    }
}
