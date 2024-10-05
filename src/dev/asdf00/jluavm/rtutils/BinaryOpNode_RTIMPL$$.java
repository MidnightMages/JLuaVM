package dev.asdf00.jluavm.rtutils;
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class BinaryOpNode_RTIMPL$$ {
    public static LuaVariable$ IL___COERCEToNum(LuaVariable$ a){
        return a; // TODO return a LuaNumber$ if coercion is possible, otherwise return the argument a
    }

    public static LuaVariable$ IL___COERCEToStr(LuaVariable$ a){
        return a; // TODO return a LuaString$ if coercion is possible, otherwise return the argument a
    }

    public static LuaVariable$ IL__concat(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToStr(x);
        y = IL___COERCEToStr(y);
        if (!x.isString() || !y.isString()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s concat %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaString$;
        assert y instanceof LuaString$;
        return ((LuaString$) x).concat((LuaString$) y);
    }

    public static LuaVariable$ IL__add(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s add %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).add((LuaNumber$) y);
    }

    public static LuaVariable$ IL__sub(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s sub %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).sub((LuaNumber$) y);
    }

    public static LuaVariable$ IL__mul(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s mul %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).mul((LuaNumber$) y);
    }

    public static LuaVariable$ IL__div(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s div %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).div((LuaNumber$) y);
    }

    public static LuaVariable$ IL__idiv(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s idiv %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).idiv((LuaNumber$) y);
    }

    public static LuaVariable$ IL__mod(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s mod %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).mod((LuaNumber$) y);
    }

    public static LuaVariable$ IL__pow(LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y).getMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            throw new LuaTypeError("attempted to perform operation '%s pow %s'".formatted(x.getType().fancyName, y.getType().fancyName));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).pow((LuaNumber$) y);
    }
}