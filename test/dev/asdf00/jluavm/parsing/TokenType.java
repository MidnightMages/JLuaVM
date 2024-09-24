package dev.asdf00.jluavm.parsing;

public enum TokenType {
    IDENT(""),

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

    // constants
    NUMERAL(""),
    LITERAL_STRING(""),

    EOF(""),
    ;

    public final String rep;

    TokenType(String representation) {
        rep = representation;
    }
}
