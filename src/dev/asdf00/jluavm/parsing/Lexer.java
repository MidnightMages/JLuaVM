package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.exceptions.LuaLexerException;

import java.util.Set;

import static dev.asdf00.jluavm.parsing.TokenType.*;

public class Lexer {
    private static final char CEOF = (char) -1;
    private static final Set<String> KEYWORDS = Set.of("break", "goto", "do", "end", "while", "repeat", "until", "if",
            "then", "elseif", "else", "for", "in", "function", "local", "return", "nil", "false", "true", "and", "or");

    private final CharStream input;
    private char cur;

    public Lexer(String text) {
        this(text.toCharArray());
    }

    public Lexer(char[] text) {
        input = new CharStream(text);
        cur = input.next();
    }

    private void advance() {
        cur = input.next();
    }

    public Token next() {
        // skip whitespace and comments
        for (; ; ) {
            while (Character.isWhitespace(cur)) {
                advance();
            }
            if (cur == '-' && input.peek() == '-') {
                // start of comment
                advance();  // remove "--" from stream
                advance();
                if (cur == '[' && input.peek() == '[') {
                    // multiline comment
                    while (cur != ']' && input.peek() != ']') {
                        cur = input.next();
                    }
                    advance();  // remove "]]" from stream
                    advance();
                } else {
                    // single line comment
                    while (cur != '\n') {
                        advance();
                    }
                    advance();
                }
                continue;  // goto skipWhitespace;
            }
            // this loop is just a goto replacement, we do not actually want to loop
            break;
        }

        // START OF NEW TOKEN
        // get position of last retrieved char to mark a token start
        Position pos = input.prevPos();
        if (cur == CEOF) {
            return new Token(EOF, pos);
        }

        // read keyword or ident
        if (isIdentStart(cur)) {
            var ib = new StringBuilder();
            do {
                ib.append(cur);
                advance();
            } while (isIdentContination(cur));
            String ident = ib.toString();
            if (KEYWORDS.contains(ident)) {
                return new Token(TokenType.valueOf(ident.toUpperCase()), pos, ident);
            } else {
                return new Token(IDENT, pos, ident);
            }
        }

        // read number
        if (Character.isDigit(cur)) {
            boolean isInteger = true;
            boolean isHex = false;
            boolean isExp = false;
            boolean allowPM = false;
            final var nb = new StringBuilder();
            nb.append(cur);
            if (cur == '0') {
                advance();
                if (cur == 'x' || cur == 'X') {
                    isHex = true;
                    nb.append(cur);
                    advance();
                }
            } else {
                advance();
            }

            Runnable step = () -> {
                nb.append(cur);
                advance();
            };
            for (; ; step.run()) {
                if (allowPM) {
                    allowPM = false;
                    isExp = true;
                    if (cur == '+' || cur == '-') {
                        continue;
                    }
                }
                if (Character.isDigit(cur)) {
                    continue;
                }
                if (isHex) {
                    if (('a' <= cur && cur <= 'f') || ('A' <= cur && cur <= 'F')) {
                        continue;
                    }
                }
                if (isInteger) {
                    if (cur == '.') {
                        isInteger = false;
                        continue;
                    }
                } else if (!isExp) {
                    // exponent?
                    if (isHex) {
                        if (cur == 'p' || cur == 'P') {
                            isHex = false;
                            allowPM = true;
                            continue;
                        }
                    } else {
                        if (cur == 'e' || cur == 'E') {
                            allowPM = true;
                            continue;
                        }
                    }
                }
                break;
            }

            String number = nb.toString();
            try {
                double val = isInteger ? Long.parseLong(number) : Double.parseDouble(number);
                return new Token(NUMERAL, pos, val);
            } catch (NumberFormatException e) {
                throw new LuaLexerException("'%s' is not a valid number".formatted(number));
            }
        }

        // TODO: read string literals
        if (false) {

        }

        // TODO: switch case for all symbols


        throw new LuaLexerException("should not reach");
    }

    private static boolean isIdentStart(char c) {
        return c == '_' |
                ((((1 << Character.UPPERCASE_LETTER) |
                        (1 << Character.LOWERCASE_LETTER) |
                        (1 << Character.TITLECASE_LETTER))
                        >> Character.getType(c)) & 1) != 0;
    }

    private static boolean isIdentContination(char c) {
        return c == '_' | Character.isLetterOrDigit(c);
    }

    private static class CharStream {
        private final char[] cs;
        private int ptr;
        private int line;
        private int col;
        private int pLine;
        private int pCol;

        public CharStream(char[] cs) {
            this.cs = cs;
            ptr = 0;
            line = 0;
            col = 0;
            pLine = -1;
            pCol = -1;
        }

        public char next() {
            char n = ptr < cs.length ? cs[ptr++] : CEOF;
            pLine = line;
            pCol = col;
            if (n == '\n') {
                line++;
                col = 0;
            } else if (n != CEOF) {
                col++;
            }
            return n;
        }

        public char peek() {
            return peek(1);
        }

        public char peek(int cnt) {
            int effective = ptr + cnt - 1;
            return effective < cs.length ? cs[effective] : CEOF;
        }

        public Position pos() {
            return new Position(line, col);
        }

        public Position prevPos() {
            return new Position(pLine, pCol);
        }
    }
}
