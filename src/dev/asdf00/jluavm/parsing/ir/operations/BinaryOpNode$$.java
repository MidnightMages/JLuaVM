package dev.asdf00.jluavm.parsing.ir.operations;
import dev.asdf00.jluavm.parsing.container.TokenType;
import dev.asdf00.jluavm.parsing.ir.Node;
import dev.asdf00.jluavm.types.*;

import java.util.Objects;

public class BinaryOpNode$$ extends Node {
    protected final Node x;
    protected final Node y;
    private final TokenType tokenType;

    public BinaryOpNode$$(Node x, Node y, TokenType tokenType) {
        this.x = x;
        this.y = y;
        this.tokenType = tokenType;
    }

    @Override
    public String generate() {
        return P("BinaryOpNode$$.IL__%s(%s, %s)".formatted(Objects.requireNonNull(tokenType.metatableFuncName), x.generate(), y.generate()));
    }
    public static LuaVariable$ IL__add(LuaVariable$ x, LuaVariable$ y){
        if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("add");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        if (!y.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) y);
            var f = tbl.getMtFunc("add");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$)x).add((LuaNumber$)y);
    }
    public static LuaVariable$ IL__sub(LuaVariable$ x, LuaVariable$ y){
        if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("sub");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        if (!y.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) y);
            var f = tbl.getMtFunc("sub");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$)x).sub((LuaNumber$)y);
    }
    public static LuaVariable$ IL__mul(LuaVariable$ x, LuaVariable$ y){
        if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("mul");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        if (!y.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) y);
            var f = tbl.getMtFunc("mul");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$)x).mul((LuaNumber$)y);
    }
    public static LuaVariable$ IL__div(LuaVariable$ x, LuaVariable$ y){
        if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("div");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        if (!y.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) y);
            var f = tbl.getMtFunc("div");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$)x).div((LuaNumber$)y);
    }
    public static LuaVariable$ IL__idiv(LuaVariable$ x, LuaVariable$ y){
        if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("idiv");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        if (!y.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) y);
            var f = tbl.getMtFunc("idiv");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$)x).idiv((LuaNumber$)y);
    }
    public static LuaVariable$ IL__mod(LuaVariable$ x, LuaVariable$ y){
        if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("mod");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        if (!y.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) y);
            var f = tbl.getMtFunc("mod");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$)x).mod((LuaNumber$)y);
    }
    public static LuaVariable$ IL__pow(LuaVariable$ x, LuaVariable$ y){
        if (!x.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) x);
            var f = tbl.getMtFunc("pow");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        if (!y.isNumber()) { // if arg is not a number, it has to be a table, otherwise fail
            var tbl = ((LuaTable$) y);
            var f = tbl.getMtFunc("pow");
            if (f != null) {
                return f.Invoke(x, y)[0]; // metamethods can only return one value
            }
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$)x).pow((LuaNumber$)y);
    }
}