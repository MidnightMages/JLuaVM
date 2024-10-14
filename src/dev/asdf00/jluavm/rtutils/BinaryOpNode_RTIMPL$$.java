package dev.asdf00.jluavm.rtutils;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.typesOLD.*;
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class BinaryOpNode_RTIMPL$$ {
    public static LuaVariableOLD IL___COERCEToNum(LuaVariableOLD a){
        return a; // TODO return a LuaNumber$ if coercion is possible, otherwise return the argument a
    }
    public static LuaVariableOLD IL___COERCEToBw(LuaVariableOLD a){
        return a; // TODO return a LuaNumberBw$ if coercion is possible, otherwise return the argument a
    }
    public static LuaVariableOLD IL___COERCEToStr(LuaVariableOLD a){
        return a; // TODO return a LuaString$ if coercion is possible, otherwise return the argument a
    }

    public static LuaVariableOLD IL___builtin_or(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(x).getValue() ? x : y;
    }

    public static LuaVariableOLD IL___builtin_and(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(x).getValue() ? y : x;
    }

    public static LuaVariableOLD IL__lt(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        if (x.getType() == y.getType()){
            if (x.isString())
                return LuaBooleanOLD.fromState(((LuaStringOLD) x).lt(((LuaStringOLD) y)));
            if (x.isNumber()) { // TODO make work for NumberBw
                return LuaBooleanOLD.fromState(((LuaNumberOLD) x).lt(((LuaNumberOLD) y)));
            }
        }
        var mtf = x.isTable() ? ((LuaTableOLD) x)._luaGetMtFunc("__lt") : null;
        if(mtf == null)
            mtf = y.isTable() ? ((LuaTableOLD) y)._luaGetMtFunc("__lt") : null;
        if(mtf == null)
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s lt %s' and could not find any metatable".formatted(x.getType().fancyName, y.getType().fancyName)));

        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x,y)[0]);
    }

    public static LuaVariableOLD IL__le(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        if (x.getType() == y.getType()){
            if (x.isString())
                return LuaBooleanOLD.fromState(((LuaStringOLD) x).le(((LuaStringOLD) y)));
            if (x.isNumber()) { // TODO make work for NumberBw
                return LuaBooleanOLD.fromState(((LuaNumberOLD) x).le(((LuaNumberOLD) y)));
            }
        }
        var mtf = x.isTable() ? ((LuaTableOLD) x)._luaGetMtFunc("__le") : null;
        if(mtf == null)
            mtf = y.isTable() ? ((LuaTableOLD) y)._luaGetMtFunc("__le") : null;
        if(mtf == null)
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s le %s' and could not find any metatable".formatted(x.getType().fancyName, y.getType().fancyName)));

        return UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x,y)[0]);
    }

    public static LuaVariableOLD IL__eq(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        if (x.getType() == y.getType()) {
            if (x == y)
                return LuaBooleanOLD.TRUE;
            if (x.isString()) { // y is also a string
                return LuaBooleanOLD.fromState(((LuaStringOLD) x).strEquals((LuaStringOLD) y));
            } else if (x.isNumber()) {
                return LuaBooleanOLD.fromState(((LuaNumberOLD) x).numEquals((LuaNumberOLD) y));
            } else if (x.isNumberBw()) {
                return LuaBooleanOLD.fromState(((LuaNumberBwOLD) x).numBwEquals((LuaNumberBwOLD) y));
            } else if (x.isTable()) {
                var mtf = ((LuaTableOLD) x)._luaGetMtFunc("__eq");
                if (mtf == null)
                    mtf = ((LuaTableOLD) y)._luaGetMtFunc("__eq");
                return mtf == null ? LuaBooleanOLD.FALSE : UnaryOpNode_RTIMPL$$.IL___builtin_IS_TRUTHY(mtf.Invoke(x, y)[0]);
            }
            // remaining types are ref compares and would be handled by the ref equals check above
        }
        return LuaBooleanOLD.FALSE;
    }

    public static LuaVariableOLD IL__bor(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s bor %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBwOLD;
        assert y instanceof LuaNumberBwOLD;
        return ((LuaNumberBwOLD) x).bor((LuaNumberBwOLD) y);
    }

    public static LuaVariableOLD IL__bxor(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s bxor %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBwOLD;
        assert y instanceof LuaNumberBwOLD;
        return ((LuaNumberBwOLD) x).bxor((LuaNumberBwOLD) y);
    }

    public static LuaVariableOLD IL__band(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s band %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBwOLD;
        assert y instanceof LuaNumberBwOLD;
        return ((LuaNumberBwOLD) x).band((LuaNumberBwOLD) y);
    }

    public static LuaVariableOLD IL__shl(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s shl %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBwOLD;
        assert y instanceof LuaNumberBwOLD;
        return ((LuaNumberBwOLD) x).shl((LuaNumberBwOLD) y);
    }

    public static LuaVariableOLD IL__shr(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToBw(x);
        y = IL___COERCEToBw(y);
        if (!x.isNumberBw() || !y.isNumberBw()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s shr %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBwOLD;
        assert y instanceof LuaNumberBwOLD;
        return ((LuaNumberBwOLD) x).shr((LuaNumberBwOLD) y);
    }

    public static LuaVariableOLD IL__concat(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToStr(x);
        y = IL___COERCEToStr(y);
        if (!x.isString() || !y.isString()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s concat %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaStringOLD;
        assert y instanceof LuaStringOLD;
        return ((LuaStringOLD) x).concat((LuaStringOLD) y);
    }

    public static LuaVariableOLD IL__add(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s add %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        assert y instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).add((LuaNumberOLD) y);
    }

    public static LuaVariableOLD IL__sub(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s sub %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        assert y instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).sub((LuaNumberOLD) y);
    }

    public static LuaVariableOLD IL__mul(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s mul %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        assert y instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).mul((LuaNumberOLD) y);
    }

    public static LuaVariableOLD IL__div(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s div %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        assert y instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).div((LuaNumberOLD) y);
    }

    public static LuaVariableOLD IL__idiv(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s idiv %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        assert y instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).idiv((LuaNumberOLD) y);
    }

    public static LuaVariableOLD IL__mod(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s mod %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        assert y instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).mod((LuaNumberOLD) y);
    }

    public static LuaVariableOLD IL__pow(LuaVM_RT vm, LuaVariableOLD x, LuaVariableOLD y) {
        x = IL___COERCEToNum(x);
        y = IL___COERCEToNum(y);
        if (!x.isNumber() || !y.isNumber()) { // if any of the args isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            if (y.isTable()){
                var f = ((LuaTableOLD) y)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x, y)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation '%s pow %s'".formatted(x.getType().fancyName, y.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        assert y instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).pow((LuaNumberOLD) y);
    }
}