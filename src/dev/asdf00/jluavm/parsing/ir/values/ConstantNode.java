package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.CompilationState;
import dev.asdf00.jluavm.parsing.ir.Node;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class ConstantNode extends Node {
    private final String codeRepr;

    private ConstantNode(String codeRepr) {
        this.codeRepr = codeRepr;
    }

    @Override
    public String generate(CompilationState cState) {
        var spot = cState.pushEStack();
        return spot + " = " + codeRepr + ";";
    }

    public static ConstantNode ofLong(long lVal) {
        return new ConstantNode("LuaObject.of(%d)".formatted(lVal));
    }

    public static ConstantNode ofDouble(double dVal) {
        return new ConstantNode("LuaObject.of(%d)".formatted(dVal));
    }

    public static ConstantNode ofBool(boolean bVal) {
        return new ConstantNode("LuaObject." + (bVal ? "TRUE" : "FALSE"));
    }

    public static ConstantNode ofNil() {
        return new ConstantNode("LuaObject.nil()");
    }

    public static ConstantNode ofIdent(String ident) {
        // idents can only be lower-, upper- and title-case characters (as well as digits and underscores) and can
        // therefore not escape the string context and do not need any weird escape sequences. therefore, we can just
        // embed the string as is.
        return new ConstantNode("LuaObject.of(\"%s\")".formatted(ident));
    }

    public static ConstantNode ofB64(String literalString) {
        // literal strings can contain any number of weird characters like backslash, double quote or null characters.
        // therefore we effectively treat it as a list of raw UTF8 bytes and encode it with base 64 to be sure.
        return new ConstantNode("LuaObject.ofB64(\"%s\")".formatted(Base64.getEncoder().encodeToString(literalString.getBytes(StandardCharsets.UTF_8))));
    }

    public static ConstantNode ofPlain(String codeRepr) {
        return new ConstantNode(codeRepr);
    }
}
