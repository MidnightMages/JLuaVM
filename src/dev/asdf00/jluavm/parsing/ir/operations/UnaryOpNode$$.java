package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.util.Objects;

public class UnaryOpNode$$ extends Node {
    protected final Node x;
    private final TokenType tokenType;

    public UnaryOpNode$$(Node x, TokenType tokenType) {
        this.x = x;
        this.tokenType = tokenType;
    }

    @Override
    public String generate() {
        return P("UnaryOpNode_RTIMPL$$.IL__%s(%s)".formatted(Objects.requireNonNull(tokenType.metatableFuncNameUnary), x.generate()));
    }
}