package dev.asdf00.jluavm.parsing.ir.values;

import dev.asdf00.jluavm.parsing.ir.Node;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public class ConstantNode extends Node {
    private final String codeRepr;

    public ConstantNode(String codeRepr) {
        this.codeRepr = codeRepr;
    }

    public static ConstantNode ofB64(String transformToB64) {
        return new ConstantNode("literalStringB64$(\"%s\")".formatted(Base64.getEncoder().encode(transformToB64.getBytes(StandardCharsets.UTF_8))));
    }

    public static ConstantNode ofString(String str) {
        return new ConstantNode("LuaString$.of(\"%s\")".formatted(str));
    }

    public static ConstantNode ofBool(boolean bool) {
        return new ConstantNode("LuaBoolean$.fromState(%s)".formatted(bool ? "true" : "false"));
    }

    public static ConstantNode ofDouble(double val) {
        return new ConstantNode(String.format(Locale.US, "LuaNumber$.of(%f)", val));
    }

    public static ConstantNode ofLong(long val) {
        return new ConstantNode("LuaNumberBw$.of(%d)".formatted(val));
    }

    public static ConstantNode nil() {
        return new ConstantNode("LuaNil$.singleton");
    }

    @Override
    public String generate() {
        return codeRepr;
    }
}
