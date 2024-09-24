package dev.asdf00.jluavm.parsing;

public record Token(TokenType type, String stVal, double nVal) {
    public Token(TokenType type) {
        this(type, "", 0);
    }

    public Token(TokenType type, String stVal) {
        this(type, stVal, 0);
    }

    public Token(TokenType type, double nVal) {
        this(type, "", nVal);
    }
}
