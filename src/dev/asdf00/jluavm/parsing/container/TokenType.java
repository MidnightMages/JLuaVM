package dev.asdf00.jluavm.parsing.container;

public enum TokenType {
    IDENT("$ident$"),

    // symbols
    SEMICOLON(";"),
    ASSIGN("="),
    COMMA(","),
    DCOLON("::"),
    COLON(":"),
    DOT("."),
    DDOT("..", "concat"),
    TDOT("..."),
    LPAR("("),
    RPAR(")"),
    LBRAK("["),
    RBRAK("]"),
    LBRAC("{"),
    RBRAC("}"),

    // arithmetic
    LT("<"),
    GT(">"),
    LE("<="),
    GE(">="),
    NE("~="),
    EQ("=="),
    BOR("|", "bor"),
    BXOR("~", "bxor", "bnot"),
    BAND("&", "band"),
    SHL("<<", "shl"),
    SHR(">>", "shr"),
    ADD("+","add"),
    SUB("-","sub", "unm"),
    MULT("*","mul"),
    DIV("/", "div"),
    FDIV("//", "idiv"),
    MOD("%","mod"),
    HASH("#", null, "len"),
    EXPONENT("^", "pow"),


    // keywords
    BREAK("break"),
    GOTO("goto"),
    DO("do"),
    END("end"),
    WHILE("while"),
    REPEAT("repeat"),
    UNTIL("until"),
    IF("if"),
    THEN("then"),
    ELSEIF("elseif"),
    ELSE("else"),
    FOR("for"),
    IN("in"),
    FUNCTION("function"),
    LOCAL("local"),
    RETURN("return"),
    NIL("nil"),
    FALSE("false"),
    TRUE("true"),
    AND("and"),
    OR("or"),
    NOT("not", null, "_builtin_not"),

    // constants
    NUMERAL("$numeral$"),
    LITERAL_STRING("$literal_string$"),

    EOF("$eof$"),
    ;

    public final String rep;
    public final String metatableFuncNameBinary; // referenced in a fragile manner in BinaryOpProxyNode / in the emitted code
    public final String metatableFuncNameUnary; // referenced in a fragile manner in UnaryOpProxyNode / in the emitted code

    TokenType(String representation) {
        rep = representation;
        metatableFuncNameBinary = null;
        metatableFuncNameUnary = null;
    }
    TokenType(String representation, String metatableFuncNameBinary) {
        this(representation, metatableFuncNameBinary, null);
    }
    TokenType(String representation, String metatableFuncNameBinary, String metatableFuncNameUnary) {
        rep = representation;
        this.metatableFuncNameBinary = metatableFuncNameBinary;
        this.metatableFuncNameUnary = metatableFuncNameUnary;
    }
}
