package dev.asdf00.jluavm.rtutils;
import dev.asdf00.jluavm.types.*;

public class BinaryOpNode_RTIMPL$$ {
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