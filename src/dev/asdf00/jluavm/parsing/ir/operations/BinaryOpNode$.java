package dev.asdf00.jluavm.parsing.ir.operations;

import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.types.LuaNumber$;
import dev.asdf00.jluavm.types.LuaTable$;
import dev.asdf00.jluavm.types.LuaVariable$;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class BinaryOpNode$ extends Node {
    protected final Node x;
    protected final Node y;
    private final TokenType tokenType;

    public BinaryOpNode$(Node x, Node y, TokenType tokenType) {
        this.x = x;
        this.y = y;
        this.tokenType = tokenType;
    }

    protected static final Map<TokenType, String> definedOperations = Arrays.stream(new TokenType[]
            {
                    TokenType.DIV,
            }).collect(Collectors.toMap(t -> t, t -> t.metatableFuncName));

    @Override
    public String generate() {
        return P("BinaryOpNode$.IL__%s(%s, %s)".formatted(tokenType, x.generate(), y.generate()));
    }

    private static String getXYBinaryFuncSnippet(String opName, String direct){
        StringBuilder rv = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            var argName = i == 0 ? "x" : "y";
            rv.append("""
                    if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
                        var tbl = ((LuaTable$) %s);
                        var f = tbl.getMtFunc("%s");
                        if (f != null) {
                            return f.Invoke(x, y)[0]; // metamethods can only return one value
                        }
                    }
                    
                    """.formatted(argName, opName).replaceAll("//.*?\n", ""));
        }

        rv.append("return ").append(direct);
        return rv.toString();
    }

    public final static InjectableFunc[] ILToInject = new InjectableFunc[]{
            new InjectableFunc("div", "((LuaNumber$) x).divide((LuaNumber$) y);"),
    };

    private static InjectableFunc I(String shortOpName, String direct){
        String opName = "__"+shortOpName;
        String name = "BinOp$ILGEN"+opName+"$";
        String body = getXYBinaryFuncSnippet(opName, direct);
        return new InjectableFunc(name, "public static LuaVariable$ %s(LuaVariable$ x, LuaVariable$ y){\n%s\n}".formatted(name, body));
    }
//
//    public static LuaVariable$ IL__arithm(LuaVariable$ x, LuaVariable$ y, BiFunction<LuaNumber$, LuaNumber$, LuaVariable$> direct) {
//        for (int i = 0; i < 2; i++) {
//            var xy = i == 0 ? x : y;
//            if (!xy.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
//                var tbl = ((LuaTable$) xy);
//                var f = tbl.getMtFunc("__div");
//                if (f != null) {
//                    return f.Invoke(x, y)[0]; // metamethods can only return one value
//                }
//            }
//        }
//
//        assert x instanceof LuaNumber$;
//        assert y instanceof LuaNumber$;
//        return direct.apply((LuaNumber$) x, (LuaNumber$) y);
//    }
//
//    public static LuaVariable$ IL__div(LuaVariable$ x, LuaVariable$ y) {
//
//        assert x instanceof LuaNumber$;
//        assert y instanceof LuaNumber$;
//        return ((LuaNumber$) x).divide((LuaNumber$) y);
//    }
//
//    public record OperationInfo(String handlerName, BiFunction<>){};
}
