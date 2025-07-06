package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.container.Position;
import dev.asdf00.jluavm.parsing.container.Token;
import dev.asdf00.jluavm.parsing.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ConstantNode extends Node {
    private final String codeRepr;

    private ConstantNode(Position sourcePos, String codeRepr) {
        super(sourcePos);
        this.codeRepr = codeRepr;
    }

    @Override
    public String generate(CompilationState cState) {
        var spot = cState.pushEStack();
        return spot + " = " + codeRepr + ";";
    }

    public static ConstantNode ofLong(Token tk) {
        return ofLong(tk.pos(), tk.lVal());
    }

    public static ConstantNode ofLong(Position sourcePos, long lVal) {
        return new ConstantNode(sourcePos, "LuaObject.of(%d)".formatted(lVal));
    }

    public static ConstantNode ofDouble(Token tk) {
        return ofDouble(tk.pos(), tk.nVal());
    }

    public static ConstantNode ofDouble(Position sourcePos, double dVal) {
        String replacement;
        if (dVal == Double.POSITIVE_INFINITY) {
            replacement = "Double.POSITIVE_INFINITY";
        } else if (dVal == Double.NEGATIVE_INFINITY) {
            replacement = "Double.NEGATIVE_INFINITY";
        } else if (Double.isNaN(dVal)) {
            replacement = "Double.NaN";
        } else {
            replacement = Double.toString(dVal);
        }
        return new ConstantNode(sourcePos, "LuaObject.of(%s)".formatted(replacement));
    }

    public static ConstantNode ofBool(Position sourcePos, boolean bVal) {
        return new ConstantNode(sourcePos, "LuaObject." + (bVal ? "TRUE" : "FALSE"));
    }

    public static ConstantNode ofNil(Position sourcePos) {
        return new ConstantNode(sourcePos, "LuaObject.nil()");
    }

    public static ConstantNode ofIdent(Token tk) {
        return ofIdent(tk.pos(), tk.stVal());
    }

    public static ConstantNode ofIdent(Position sourcePos, String ident) {
        // idents can only be lower-, upper- and title-case characters (as well as digits and underscores) and can
        // therefore not escape the string context and do not need any weird escape sequences. therefore, we can just
        // embed the string as is.
        return new ConstantNode(sourcePos, "LuaObject.of(\"%s\")".formatted(ident));
    }

    public static ConstantNode ofB64(Token tk) {
        return ofB64(tk.pos(), tk.stVal());
    }

    public static ConstantNode ofB64(Position sourcePos, String literalString) {
        // literal strings can contain any number of weird characters like backslash, double quote or null characters.
        // therefore we effectively treat it as a list of raw UTF8 bytes and encode it with base 64 to be sure.
        return new ConstantNode(sourcePos, "LuaObject.ofB64(\"%s\")".formatted(Base64.getEncoder().encodeToString(literalString.getBytes(StandardCharsets.UTF_8))));
    }

    public static ConstantNode ofNull(Position sourcePos) {
        return new ConstantNode(sourcePos, "null");
    }
}
