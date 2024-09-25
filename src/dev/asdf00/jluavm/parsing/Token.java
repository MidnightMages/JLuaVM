package dev.asdf00.jluavm.parsing;

public record Token(TokenType type, Position pos, String stVal, double nVal) {
    public Token(TokenType type, Position pos) {
        this(type, pos, "", 0);
    }

    public Token(TokenType type, Position pos, String stVal) {
        this(type, pos, stVal, 0);
    }

    public Token(TokenType type, Position pos, double nVal) {
        this(type, pos, "", nVal);
    }
}
