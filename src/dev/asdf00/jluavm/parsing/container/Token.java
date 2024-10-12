package dev.asdf00.jluavm.parsing.container;

public record Token(TokenType type, Position pos, String stVal, double nVal, long lVal) {
    public Token(TokenType type, Position pos) {
        this(type, pos, type.rep, -1, -1);
    }

    public Token(TokenType type, Position pos, String stVal) {
        this(type, pos, stVal, -1, -1);
    }
}
