package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.parsing.exceptions.LuaLexerException;

import java.util.Set;

import static dev.asdf00.jluavm.parsing.TokenType.*;

public class Lexer {
    private static final char CEOF = (char) -1;
    private static final Set<String> KEYWORDS = Set.of("break", "goto", "do", "end", "while", "repeat", "until", "if",
            "then", "elseif", "else", "for", "in", "function", "local", "return", "nil", "false", "true", "and", "or",
            "not");

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

    public Token next() throws LuaLexerException {
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
                    while (cur != CEOF && !(cur == ']' && input.peek() == ']')) {
                        cur = input.next();
                    }
                    if (cur == CEOF) {
                        throw new LuaLexerException(input.pos(), "Unexpected EOF in multi line comment");
                    }
                    advance();  // remove "]]" from stream
                    advance();
                } else {
                    // single line comment
                    while (cur != '\n' && cur != CEOF) {
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
        if (isDecDigit(cur)) {
            boolean isInteger = true;
            boolean isHex = false;
            boolean isExp = false;
            boolean isValid = true;
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
                if (isDecDigit(cur)) {
                    isValid = true;
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
                        isValid = false;
                        continue;
                    }
                } else if (!isExp) {
                    // exponent?
                    if (isHex) {
                        if (cur == 'p' || cur == 'P') {
                            isHex = false;
                            allowPM = true;
                            isValid = false;
                            continue;
                        }
                    } else {
                        if (cur == 'e' || cur == 'E') {
                            allowPM = true;
                            isValid = false;
                            continue;
                        }
                    }
                }
                break;
            }

            String number = nb.toString();
            if (!isValid) {
                throw new LuaLexerException(pos, "'%s' is not a valid number".formatted(number));
            }
            try {
                double val = isInteger ? Long.parseLong(number) : Double.parseDouble(number);
                return new Token(NUMERAL, pos, number, val);
            } catch (NumberFormatException e) {
                throw new LuaLexerException(pos, "'%s' is not a valid number".formatted(number));
            }
        }

        // read short string literal
        if (cur == '"' || cur == '\'') {
            char init = cur;
            var sb = new StringBuilder();
            advance();
            while (cur != init) {
                if (cur == CEOF) {
                    throw new LuaLexerException(pos, "Unexpected EOF in string literal");
                } else if (cur == '\n') {
                    throw new LuaLexerException(pos, "Unexpected NEWLINE in string literal");
                } else if (cur == '\\') {
                    advance();
                    switch (cur) {
                        case 'a' -> sb.append('\u0007');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n', '\n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'v' -> sb.append('\u000B');
                        case '\\' -> sb.append('\\');
                        case '"' -> sb.append('"');
                        case '\'' -> sb.append('\'');
                        case 'z' -> {
                            // skip 'z' and all following whitespace
                            do {
                                advance();
                            } while (Character.isWhitespace(cur));
                            continue;
                        }
                        case 'x' -> {
                            advance();
                            int codePoint = 0;
                            if (!isHexDigit(cur)) {
                                throw new LuaLexerException(input.prevPos(), "Unexpected character '%s' in hex escape sequence".formatted(cur));
                            }
                            codePoint += Integer.parseInt("0x%s".formatted(cur)) * 16;
                            advance();
                            if (!isHexDigit(cur)) {
                                throw new LuaLexerException(input.prevPos(), "Unexpected character '%s' in hex escape sequence".formatted(cur));
                            }
                            codePoint += Integer.parseInt("0x%s".formatted(cur));
                            sb.append((char) codePoint);
                        }
                        case 'u' -> {
                            advance();
                            if (cur == CEOF) {
                                throw new LuaLexerException(pos, "Unexpected EOF in string literal");
                            } else if (cur != '{') {
                                throw new LuaLexerException(input.prevPos(), "Unexpected character '%s' in hex escape sequence".formatted(cur));
                            }
                            advance();
                            int codePoint = 0;
                            boolean foundOne = false;
                            while (cur != '}') {
                                if (cur == CEOF) {
                                    throw new LuaLexerException(pos, "Unexpected EOF in string literal");
                                }
                                if (!isHexDigit(cur)) {
                                    throw new LuaLexerException(input.prevPos(), "Unexpected character '%s' in hex escape sequence".formatted(cur));
                                }
                                codePoint *= 16;
                                codePoint += Integer.parseInt("0x%s".formatted(cur));
                                foundOne = true;
                                advance();
                            }
                            if (!foundOne) {
                                throw new LuaLexerException(input.prevPos(), "Empty unicode escape sequence");
                            }
                            sb.append((char) codePoint);
                        }
                        default -> {
                            if (!isDecDigit(cur)) {
                                throw new LuaLexerException(input.prevPos(), "Unknown escape sequence'\\%s'".formatted(cur));
                            }
                            int codePoint = cur - '0';
                            if (isDecDigit(input.peek())) {
                                advance();
                                codePoint *= 10;
                                codePoint += cur - '0';
                                if (isDecDigit(input.peek())) {
                                    advance();
                                    codePoint *= 10;
                                    codePoint += cur - '0';
                                }
                            }
                            sb.append((char) codePoint);
                        }
                    }
                    advance();
                    continue;
                }
                sb.append(cur);
                advance();
            }
            advance();
            return new Token(LITERAL_STRING, pos, sb.toString());
        }

        // read long string literal
        if (cur == '[' && (input.peek() == '[' || input.peek() == '=')) {
            advance();
            int level = 0;
            while (cur == '=') {
                if (cur == CEOF) {
                    throw new LuaLexerException(pos, "Unexpected EOF in long string literal");
                }
                level++;
                advance();
            }
            if (cur != '[') {
                throw new LuaLexerException(pos, "Unexpected character '%s' in long string literal".formatted(cur));
            }
            advance();
            var sb = new StringBuilder();
            for (; ; advance()) {
                if (cur == CEOF) {
                    throw new LuaLexerException(pos, "Unexpected EOF in long string literal");
                }
                if (cur == ']') {
                    boolean found = true;
                    for (int i = 1; i <= level; i++) {
                        if (input.peek(i) != '=') {
                            found = false;
                            break;
                        }
                    }
                    if (found && input.peek(level + 1) == ']') {
                        input.skip(level + 1);
                        advance();
                        break;
                    }
                }
                sb.append(cur);
            }
            // no escape sequences in long string literal
            return new Token(LITERAL_STRING, pos, sb.toString());
        }

        // switch case for all symbols
        TokenType type = switch (cur) {
            case ';' -> SEMICOLON;
            case '=' -> {
                if (input.peek() == '=') {
                    advance();
                    yield EQ;
                } else {
                    yield ASSIGN;
                }
            }
            case ',' -> COMMA;
            case ':' -> {
                if (input.peek() == ':') {
                    advance();
                    yield DCOLON;
                } else {
                    yield COLON;
                }
            }
            case '.' -> {
                if (input.peek() == '.') {
                    advance();
                    if (input.peek() == '.') {
                        advance();
                        yield TDOT;
                    } else {
                        yield DDOT;
                    }
                } else {
                    yield DOT;
                }
            }
            case '(' -> LPAR;
            case ')' -> RPAR;
            case '[' -> LBRAK;
            case ']' -> RBRAK;
            case '{' -> LBRAC;
            case '}' -> RBRAC;
            case '<' -> {
                if (input.peek() == '=') {
                    advance();
                    yield LE;
                } else if (input.peek() == '<') {
                    advance();
                    yield SHL;
                } else {
                    yield LT;
                }
            }
            case '>' -> {
                if (input.peek() == '=') {
                    advance();
                    yield GE;
                } else if (input.peek() == '>') {
                    advance();
                    yield SHR;
                } else {
                    yield GT;
                }
            }
            case '~' -> {
                if (input.peek() == '=') {
                    advance();
                    yield NE;
                } else {
                    yield BXOR;
                }
            }
            case '|' -> BOR;
            case '&' -> BAND;
            case '+' -> ADD;
            case '-' -> SUB;
            case '*' -> MULT;
            case '/' -> {
                if (input.peek() == '/') {
                    advance();
                    yield FDIV;
                } else {
                    yield DIV;
                }
            }
            case '%' -> MOD;
            case '#' -> HASH;
            case '^' -> EXPONENT;
            default -> throw new LuaLexerException(pos, "Unknown character '%s'".formatted(cur));
        };
        advance();
        return new Token(type, pos);
    }

    private static boolean isIdentStart(char c) {
        return c != CEOF && (c == '_' |
                ((((1 << Character.UPPERCASE_LETTER) |
                        (1 << Character.LOWERCASE_LETTER) |
                        (1 << Character.TITLECASE_LETTER))
                        >> Character.getType(c)) & 1) != 0);
    }

    private static boolean isIdentContination(char c) {
        return c != CEOF && (c == '_' || Character.isLetterOrDigit(c));
    }

    private static boolean isHexDigit(char c) {
        return ('0' <= c && c <= '9') || ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F');
    }

    private static boolean isDecDigit(char c) {
        return '0' <= c && c <= '9';
    }

    private static class CharStream {
        private final char[] cs;
        private int ptr;
        private int line;
        private int col;
        private int pLine;
        private int pCol;
        private boolean done;

        public CharStream(char[] cs) {
            this.cs = cs;
            ptr = 0;
            line = 1;
            col = 0;
            pLine = -1;
            pCol = -1;
            done = false;
        }

        public char next() {
            char n = ptr < cs.length ? cs[ptr++] : CEOF;
            pLine = line;
            pCol = col;
            if (n == '\n') {
                line++;
                col = 0;
            } else if (n == CEOF) {
                if (done) {
                    throw new RuntimeException("Lexer seems to be looping endlessly, have a look!");
                }
                done = true;
            } else {
                col++;
            }
            return n;
        }

        public void skip(int cnt) {
            for (int i = 0; i < cnt; i++) {
                next();
            }
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
