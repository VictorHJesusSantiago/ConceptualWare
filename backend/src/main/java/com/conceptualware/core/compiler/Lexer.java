package com.conceptualware.core.compiler;

import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private final List<String> errors  = new ArrayList<>();

    private int start   = 0;
    private int current = 0;
    private int line    = 1;
    private int column  = 1;
    private int lexemeStartColumn = 1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = current;
            lexemeStartColumn = column;
            scanToken();
        }
        tokens.add(new Token(Token.TokenType.EOF, "", null, line, column));
        return List.copyOf(tokens);
    }

    public List<String> errors() { return List.copyOf(errors); }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(' -> addToken(Token.TokenType.LEFT_PAREN);
            case ')' -> addToken(Token.TokenType.RIGHT_PAREN);
            case '{' -> addToken(Token.TokenType.LEFT_BRACE);
            case '}' -> addToken(Token.TokenType.RIGHT_BRACE);
            case '[' -> addToken(Token.TokenType.LEFT_BRACKET);
            case ']' -> addToken(Token.TokenType.RIGHT_BRACKET);
            case ';' -> addToken(Token.TokenType.SEMICOLON);
            case ':' -> addToken(Token.TokenType.COLON);
            case ',' -> addToken(Token.TokenType.COMMA);
            case '.' -> addToken(Token.TokenType.DOT);
            case '~' -> addToken(Token.TokenType.TILDE);
            case '^' -> addToken(Token.TokenType.CARET);
            case '%' -> addToken(Token.TokenType.PERCENT);
            case '*' -> addToken(match('*') ? Token.TokenType.POWER  :
                                 match('=') ? Token.TokenType.STAR_EQUAL  : Token.TokenType.STAR);
            case '+' -> addToken(match('+') ? Token.TokenType.PLUS_PLUS  :
                                 match('=') ? Token.TokenType.PLUS_EQUAL  : Token.TokenType.PLUS);
            case '-' -> addToken(match('>') ? Token.TokenType.ARROW      :
                                 match('-') ? Token.TokenType.MINUS_MINUS :
                                 match('=') ? Token.TokenType.MINUS_EQUAL : Token.TokenType.MINUS);
            case '/' -> {
                if (match('/'))      lineComment();
                else if (match('*')) blockComment();
                else if (match('=')) addToken(Token.TokenType.SLASH_EQUAL);
                else                 addToken(Token.TokenType.SLASH);
            }
            case '=' -> addToken(match('=') ? Token.TokenType.EQUAL_EQUAL : Token.TokenType.EQUAL);
            case '!' -> addToken(match('=') ? Token.TokenType.BANG_EQUAL   : Token.TokenType.BANG);
            case '<' -> addToken(match('=') ? Token.TokenType.LESS_EQUAL   :
                                 match('<') ? Token.TokenType.LEFT_SHIFT    : Token.TokenType.LESS);
            case '>' -> addToken(match('=') ? Token.TokenType.GREATER_EQUAL :
                                 match('>') ? Token.TokenType.RIGHT_SHIFT   : Token.TokenType.GREATER);
            case '&' -> addToken(match('&') ? Token.TokenType.AND           : Token.TokenType.AMPERSAND);
            case '|' -> addToken(match('|') ? Token.TokenType.OR            : Token.TokenType.PIPE);

            case ' ', '\r', '\t' -> {}
            case '\n' -> { line++; column = 1; }

            case '"' -> stringLiteral();

            default -> {
                if (Character.isDigit(c))          numberLiteral();
                else if (isIdentStart(c))          identifier();
                else error("Unexpected character: '" + c + "'");
            }
        }
    }

    private void stringLiteral() {
        var sb = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            char ch = advance();
            if (ch == '\n') { line++; column = 1; }
            if (ch == '\\') {
                ch = advance();
                sb.append(switch (ch) {
                    case 'n'  -> '\n';
                    case 't'  -> '\t';
                    case 'r'  -> '\r';
                    case '\\' -> '\\';
                    case '"'  -> '"';
                    case '0'  -> '\0';
                    default   -> { error("Unknown escape: \\" + ch); yield ch; }
                });
            } else {
                sb.append(ch);
            }
        }
        if (isAtEnd()) { error("Unterminated string literal"); return; }
        advance();
        addToken(Token.TokenType.STRING_LITERAL, sb.toString());
    }

    private void numberLiteral() {
        boolean isFloat = false;

        if (source.charAt(start) == '0' && !isAtEnd()) {
            if (peek() == 'x' || peek() == 'X') {
                advance();
                while (!isAtEnd() && isHexDigit(peek())) advance();
                long value = Long.parseLong(source.substring(start + 2, current).replace("_", ""), 16);
                addToken(Token.TokenType.INTEGER_LITERAL, (int) value);
                return;
            } else if (peek() == 'b' || peek() == 'B') {
                advance();
                while (!isAtEnd() && (peek() == '0' || peek() == '1' || peek() == '_')) advance();
                long value = Long.parseLong(source.substring(start + 2, current).replace("_", ""), 2);
                addToken(Token.TokenType.INTEGER_LITERAL, (int) value);
                return;
            }
        }

        while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '_')) advance();
        if (!isAtEnd() && peek() == '.' && isDigitAt(current + 1)) {
            isFloat = true;
            advance();
            while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '_')) advance();
        }
        if (!isAtEnd() && (peek() == 'e' || peek() == 'E')) {
            isFloat = true;
            advance();
            if (!isAtEnd() && (peek() == '+' || peek() == '-')) advance();
            while (!isAtEnd() && Character.isDigit(peek())) advance();
        }

        String text = source.substring(start, current).replace("_", "");
        if (isFloat) addToken(Token.TokenType.FLOAT_LITERAL,   Double.parseDouble(text));
        else         addToken(Token.TokenType.INTEGER_LITERAL,  Integer.parseInt(text));
    }

    private void identifier() {
        while (!isAtEnd() && isIdentPart(peek())) advance();
        String text = source.substring(start, current);
        Token.TokenType type = Token.keyword(text);
        Object literal = switch (type) {
            case TRUE  -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case NULL  -> null;
            default    -> null;
        };
        addToken(type, literal);
    }

    private void lineComment() {
        while (!isAtEnd() && peek() != '\n') advance();
    }

    private void blockComment() {
        int depth = 1;
        while (!isAtEnd() && depth > 0) {
            if (peek() == '\n') { line++; column = 1; }
            if (peek() == '/' && peekNext() == '*') { depth++; advance(); }
            else if (peek() == '*' && peekNext() == '/') { depth--; advance(); }
            advance();
        }
    }

    private char advance() {
        char c = source.charAt(current++);
        column++;
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;
        current++;
        column++;
        return true;
    }

    private char peek()         { return isAtEnd() ? '\0' : source.charAt(current); }
    private char peekNext()     { return (current + 1 >= source.length()) ? '\0' : source.charAt(current + 1); }
    private boolean isAtEnd()   { return current >= source.length(); }

    private boolean isIdentStart(char c) { return Character.isLetter(c) || c == '_'; }
    private boolean isIdentPart(char c)  { return Character.isLetterOrDigit(c) || c == '_'; }
    private boolean isHexDigit(char c)   { return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
    private boolean isDigitAt(int pos)   { return pos < source.length() && Character.isDigit(source.charAt(pos)); }

    private void addToken(Token.TokenType type)                  { addToken(type, null); }
    private void addToken(Token.TokenType type, Object literal)  {
        String lexeme = source.substring(start, current);
        tokens.add(new Token(type, lexeme, literal, line, lexemeStartColumn));
    }

    private void error(String message) {
        String err = "Lexer error [L%d:C%d]: %s".formatted(line, column, message);
        errors.add(err);
        tokens.add(new Token(Token.TokenType.ERROR, source.substring(start, current), null, line, column));
    }
}
