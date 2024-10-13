package dev.asdf00.jluavm.rtutils;

import dev.asdf00.jluavm.internals.LuaVM_RT$;
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class BinaryOpNode_RTIMPL$$ {
    public static LuaVariable$ IL___COERCEToNum(LuaVariable$ a){
        return a; // TODO return a LuaNumber$ if coercion is possible, otherwise return the argument a
    }
    public static LuaVariable$ IL___COERCEToBw(LuaVariable$ a){
        return a; // TODO return a LuaNumberBw$ if coercion is possible, otherwise return the argument a
    }
    public static LuaVariable$ IL___COERCEToStr(LuaVariable$ a){
        return a; // TODO return a LuaString$ if coercion is possible, otherwise return the argument a
    }

    public static LuaVariable$ IL___builtin_or(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(x).getValue() ? x : y;
    }

    public static LuaVariable$ IL___builtin_and(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(x).getValue() ? y : x;
    }

    public static LuaVariable$ IL__lt(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        if (x.getType() == y.getType()){
            if (x.isString())
                return LuaBoolean$.fromState(((LuaString$) x).lt(((LuaString$) y)));
            if (x.isNumber()) { // TODO make work for NumberBw
                return LuaBoolean$.fromState(((LuaNumber$) x).lt(((LuaNumber$) y)));
            }
        }
        var mtf = x.isTable() ? ((LuaTable$) x)._luaGetMtFunc("__lt") : null;
        if(mtf == null)
            mtf = y.isTable() ? ((LuaTable$) y)._luaGetMtFunc("__lt") : null;
        if(mtf == null)
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s lt %s' and could not find any metatable".formatted(x.getType().fancyName, y.getType().fancyName)));

        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x,y)[0]);
    }

    public static LuaVariable$ IL__le(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        if (x.getType() == y.getType()){
            if (x.isString())
                return LuaBoolean$.fromState(((LuaString$) x).le(((LuaString$) y)));
            if (x.isNumber()) { // TODO make work for NumberBw
                return LuaBoolean$.fromState(((LuaNumber$) x).le(((LuaNumber$) y)));
            }
        }
        var mtf = x.isTable() ? ((LuaTable$) x)._luaGetMtFunc("__le") : null;
        if(mtf == null)
            mtf = y.isTable() ? ((LuaTable$) y)._luaGetMtFunc("__le") : null;
        if(mtf == null)
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s le %s' and could not find any metatable".formatted(x.getType().fancyName, y.getType().fancyName)));

        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x,y)[0]);
    }

    public static LuaVariable$ IL__eq(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        if (x.getType() == y.getType()) {
            if (x == y)
                return LuaBoolean$.TRUE;
            if (x.isString()) { // y is also a string
                return LuaBoolean$.fromState(((LuaString$) x).strEquals((LuaString$) y));
            } else if (x.isNumber()) {
                return LuaBoolean$.fromState(((LuaNumber$) x).numEquals((LuaNumber$) y));
            } else if (x.isNumberBw()) {
                return LuaBoolean$.fromState(((LuaNumberBw$) x).numBwEquals((LuaNumberBw$) y));
            } else if (x.isTable()) {
                var mtf = ((LuaTable$) x)._luaGetMtFunc("__eq");
                if (mtf == null)
                    mtf = ((LuaTable$) y)._luaGetMtFunc("__eq");
                return mtf == null ? LuaBoolean$.FALSE : UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x, y)[0]);
            }
            // remaining types are ref compares and would be handled by the ref equals check above
        }
        return LuaBoolean$.FALSE;
    }

    public static LuaVariable$ IL__bor(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s bor %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBw$;
        assert y instanceof LuaNumberBw$;
        return ((LuaNumberBw$) x).bor((LuaNumberBw$) y);
    }

    public static LuaVariable$ IL__bxor(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s bxor %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBw$;
        assert y instanceof LuaNumberBw$;
        return ((LuaNumberBw$) x).bxor((LuaNumberBw$) y);
    }

    public static LuaVariable$ IL__band(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s band %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBw$;
        assert y instanceof LuaNumberBw$;
        return ((LuaNumberBw$) x).band((LuaNumberBw$) y);
    }

    public static LuaVariable$ IL__shl(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s shl %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBw$;
        assert y instanceof LuaNumberBw$;
        return ((LuaNumberBw$) x).shl((LuaNumberBw$) y);
    }

    public static LuaVariable$ IL__shr(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s shr %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBw$;
        assert y instanceof LuaNumberBw$;
        return ((LuaNumberBw$) x).shr((LuaNumberBw$) y);
    }

    public static LuaVariable$ IL__concat(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToStr(x);
        y = IL___COERCEToStr(y);
        if (!x.isString() || !y.isString()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s concat %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaString$;
        assert y instanceof LuaString$;
        return ((LuaString$) x).concat((LuaString$) y);
    }

    public static LuaVariable$ IL__add(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s add %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).add((LuaNumber$) y);
    }

    public static LuaVariable$ IL__sub(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s sub %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).sub((LuaNumber$) y);
    }

    public static LuaVariable$ IL__mul(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s mul %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).mul((LuaNumber$) y);
    }

    public static LuaVariable$ IL__div(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s div %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).div((LuaNumber$) y);
    }

    public static LuaVariable$ IL__idiv(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s idiv %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).idiv((LuaNumber$) y);
    }

    public static LuaVariable$ IL__mod(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s mod %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).mod((LuaNumber$) y);
    }

    public static LuaVariable$ IL__pow(LuaVM_RT$ vm, LuaVariable$ x, LuaVariable$ y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTable$) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s pow %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        assert y instanceof LuaNumber$;
        return ((LuaNumber$) x).pow((LuaNumber$) y);
    }
}