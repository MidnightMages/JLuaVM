package dev.asdf00.jluavm.parsing;

public enum TokenType {
    IDENT("$ident$"),

    // symbols
    SEMICOLON(";"),
    ASSIGN("="),
    COMMA(","),
    DCOLON("::"),
    COLON(":"),
    DOT("."),
    DDOT(".."),
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
    ADD("+"),
    SUB("-"),
    MULT("*"),
    DIV("/"),
    FDIV("//"),
    MOD("%"),
    HASH("#"),
    EXPONENT("^"),


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

    TokenType(String representation) {
        rep = representation;
    }
}
