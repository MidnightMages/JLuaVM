package dev.asdf00.jluavm.parsing;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaLexerError;
import dev.asdf00.jluavm.exceptions.loading.LuaLexerException;
import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.container.TokenType;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static dev.asdf00.jluavm.parsing.container.TokenType.*;

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
        if (cur == '#') { // if file starts with #, ignore first line to achieve luac compliance regarding shebangs
            do {
                cur = input.next();
            } while (cur != '\r' && cur != '\n');
        }
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
                    if (cur != CEOF) {
                        advance();
                    }
                }
                continue;  // goto skipWhitespace;
            }
            // this loop is just a goto replacement, we do not actually want to loop
            break;
        }

        // START OF NEW TOKEN
        // getExpression position of last retrieved char to mark a token start
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
        if (isDecDigit(cur) || (cur == '.' && isDecDigit(input.peek()))) {
            var parseResult = parseNumber(pos, () -> cur, this::advance);
            return new Token(NUMERAL, pos, parseResult.consumedString, parseResult.dVal, parseResult.lVal);
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
                            codePoint += Integer.parseInt(String.valueOf(cur), 16) * 16;
                            advance();
                            if (!isHexDigit(cur)) {
                                throw new LuaLexerException(input.prevPos(), "Unexpected character '%s' in hex escape sequence".formatted(cur));
                            }
                            codePoint += Integer.parseInt(String.valueOf(cur), 16);
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
                                codePoint += Integer.parseInt(String.valueOf(cur), 16);
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
                                throw new LuaLexerException(input.prevPos(), "Unknown escape sequence '\\%s'".formatted(cur));
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
                level++;
                advance();
            }
            if (cur == CEOF) {
                throw new LuaLexerException(pos, "Unexpected EOF in long string literal");
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

    public record NumberParseResult(double dVal, long lVal, String consumedString) {
    }

    @SuppressWarnings("DuplicateExpressions")
    public static NumberParseResult parseNumber(Position globalStartPos, Supplier<Character> getCurrCharRaw, Runnable performCharAdvance) {
        // numbers can start with any digit, the prefix '0x' or .

        // so first consume the first group of base10 digits, as that is a safe bet

        final Supplier<Character> getCurrChar = () -> Character.toLowerCase(getCurrCharRaw.get()); // numbers are always case-insensitive

        final var consumed = new StringBuilder();
        // returns the number of consumed chars
        final Function<Function<Character, Boolean>, String> consumeWhile = (filter) -> {
            var rv = new StringBuilder();
            while (true) {
                var chr = getCurrChar.get();
                if (filter.apply(chr)) {
                    consumed.append(chr);
                    performCharAdvance.run();
                    rv.append(chr);
                } else {
                    break;
                }
            }
            return rv.toString();
        };

        final Runnable consumeAndAdvance = () -> {
            consumed.append(getCurrChar.get());
            performCharAdvance.run();
        };

        consumeWhile.apply(Lexer::isDecDigit);

        // now we have a set of digits (or empty if actual number starts with a period)
        if (consumed.length() == 1 && consumed.charAt(0) == '0' && getCurrChar.get() == 'x') { // 0x prefix
            consumeAndAdvance.run();
            consumeWhile.apply(Lexer::isHexDigit); // consume any hex digits
            // now we got 0x445465ab
            // so valid continuations are .1E, p-4, p+1

            var mantissaDigits = consumed.substring(2); // skip 0x
            var next = getCurrChar.get();
            if (next != '.' && next != 'p') {
                // return the number
                // this will always be an integer, and the last 64bits are used. Numbers that are longer will be truncated, ignoring the left digits
                try {
                    return new NumberParseResult(-1,
                            Long.parseUnsignedLong(mantissaDigits.length() <= 16 ? mantissaDigits : mantissaDigits.substring(mantissaDigits.length() - 16), 16),
                            consumed.toString());
                } catch (NumberFormatException e) {
                    throw new LuaLexerException(globalStartPos, "%s is not a valid number".formatted(consumed.toString()));
                }
            }

            double d = 0;
            for (char c : mantissaDigits.toCharArray()) { // parse mantissa
                d *= 16;
                if (c >= '0' && c <= '9')
                    d += c - '0';
                else if (c >= 'a' && c <= 'f')
                    d += c - 'a' + 10;
                else
                    throw new InternalLuaLexerError("failed to parse hex mantissa");
            }
            if (next == '.') { // always double
                consumeAndAdvance.run();
                var postPeriod = consumeWhile.apply(Lexer::isHexDigit); // consume any hex digits
                if (consumed.toString().equals("0x.")) // "0x." is invalid, but 0x1. and 0x.1 are allowed
                    throw new LuaLexerException(globalStartPos, "malformed number");
                // now we got 0x445465ab.65ab

                double mult = 1;
                for (char c : postPeriod.toCharArray()) { // parse period suffix
                    mult /= 16d;
                    if (c >= '0' && c <= '9')
                        d += (c - '0') * mult;
                    else if (c >= 'a' && c <= 'f')
                        d += (c - 'a' + 10) * mult;
                    else
                        throw new InternalLuaLexerError("failed to parse hex mantissa2");
                }
                // so next up might be p and then + or - and a base10 number (which is the next if-case)
            }
            next = getCurrChar.get();
            if (next == 'p') { // always double
                consumeAndAdvance.run();
                next = getCurrChar.get();
                if (next == '-' || next == '+' || isDecDigit(next)) {
                    d *= next == '-' ? -1 : 1;
                    if (!isDecDigit(next)) // if it was - or +
                        consumeAndAdvance.run();
                    // next up may be a base10 number
                    var exp = consumeWhile.apply(Lexer::isDecDigit);
                    d *= Math.pow(2, Long.parseUnsignedLong(exp, 10));
                } else {
                    throw new LuaLexerException(globalStartPos, "malformed exponent in number %s".formatted(consumed.toString()));
                }
            }
            return new NumberParseResult(d, -1, consumed.toString());
        } else if (getCurrChar.get() == '.') { // . or 1234. ; always double
            consumeAndAdvance.run();
            // next up may be a base 10 number
            consumeWhile.apply(Lexer::isDecDigit);
            // followed by an optional exponent

            if (getCurrChar.get() == 'e') { // always double
                consumeAndAdvance.run();
                var next = getCurrChar.get();
                if (next == '-' || next == '+' || isDecDigit(next)) {
                    consumeAndAdvance.run();
                    // next up may be a base10 number
                    consumeWhile.apply(Lexer::isDecDigit);
                }
            } else if (isIdentContination(getCurrChar.get())) {
                throw new LuaLexerException(globalStartPos, "%s is not a valid number".formatted(consumed.toString()));
            }
            // return the number
            try {
                return new NumberParseResult(Double.parseDouble(consumed.toString()), -1, consumed.toString());
            } catch (NumberFormatException e) {
                throw new LuaLexerException(globalStartPos, "%s is not a valid number".formatted(consumed.toString()));
            }
        } else if (getCurrChar.get() == 'e') { // always double; number like 1e2
            consumeAndAdvance.run();
            // next up may be a base 10 exponent
            consumeWhile.apply(Lexer::isDecDigit);
            try {
                return new NumberParseResult(Double.parseDouble(consumed.toString()), -1, consumed.toString());
            } catch (NumberFormatException e) {
                throw new LuaLexerException(globalStartPos, "%s is not a valid number".formatted(consumed.toString()));
            }
        } else { // we are done, next characters must be part of another token
            if (isIdentContination(getCurrChar.get())) {
                throw new LuaLexerException(globalStartPos, "%s is not a valid number".formatted(consumed.toString()));
            }
            // return the number
            // 2^63 is the first number that does not fit into a lua integer
            var consumedStr = consumed.toString();
            var numberSize = Double.parseDouble(consumedStr);
            if (numberSize < Long.MAX_VALUE * 1.5d) { // if it definitely parsable in an unsigned manner, parse it as unsigned, then check the exact size
                var ulong = Long.parseUnsignedLong(consumedStr);
                if (Long.compareUnsigned(ulong, 0x8000_0000_0000_0000L) < 0) { // compare with 2^63; if number<2^63, it fits in a lua integer
                    return new NumberParseResult(-1, ulong, consumed.toString());
                }
                // else fall through to next one
            }
            // otherwise its too large -> return as double
            return new NumberParseResult(numberSize, -1, consumed.toString());
        }
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isHexDigit(char c) {
        return ('0' <= c && c <= '9') || ('a' <= c && c <= 'f') || ('A' <= c && c <= 'F');
    }

    public static boolean isDecDigit(char c) {
        return '0' <= c && c <= '9';
    }

    public static double parseHexDouble(String num) {
        double d = 0;
        for (int i = 0; i < num.length(); i++) {
            d *= 16;
            char c = num.charAt(i);
            if (isDecDigit(c)) {
                d += c - '0';
            } else {
                char cl = Character.toLowerCase(c);
                if (cl < 'a' || cl > 'f') {
                    throw new NumberFormatException("non hex digit character '%s' in '%s'".formatted(c, num));
                }
                d += cl - 'a';
            }
        }
        return d;
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
            return new Position(line, col, ptr);
        }

        public Position prevPos() {
            return new Position(pLine, pCol, ptr);
        }
    }
}
