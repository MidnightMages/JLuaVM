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
    BOR("|"),
    BXOR("~"),
    BAND("&"),
    SHL("<<"),
    SHR(">>"),
    ADD("+","add"),
    SUB("-","sub"),
    MULT("*","mul"),
    DIV("/", "div"),
    FDIV("//", "idiv"),
    MOD("%","mod"),
    HASH("#"),
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
    NOT("not"),

    // constants
    NUMERAL("$numeral$"),
    LITERAL_STRING("$literal_string$"),

    EOF("$eof$"),
    ;

    public final String rep;
    public final String metatableFuncName; // referenced in a fragile manner in BinaryOpProxyNode / in the emitted code

    TokenType(String representation) {
        rep = representation;
        metatableFuncName = null;
    }
    TokenType(String representation, String metatableFuncName) {
        rep = representation;
        this.metatableFuncName = metatableFuncName;
    }
}
