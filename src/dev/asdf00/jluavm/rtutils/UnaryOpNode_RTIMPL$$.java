package dev.asdf00.jluavm.rtutils;

import dev.asdf00.jluavm.internals.LuaVM_RT$;
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class UnaryOpNode_RTIMPL$$ {
    public static LuaVariable$ IL___COERCEToNum(LuaVariable$ a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToNum(a);
    }
    public static LuaVariable$ IL___COERCEToBw(LuaVariable$ a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToBw(a);
    }
    public static LuaVariable$ IL___COERCEToStr(LuaVariable$ a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToStr(a);
    }
    public static LuaBoolean$ IL___builtin_IS_TRUTHY(LuaVariable$ x) {
        return (x.isNil() || x.isBoolean() && !((LuaBoolean$)x).getValue()) ? LuaBoolean$.FALSE : LuaBoolean$.TRUE;
    }

    public static LuaVariable$ IL___builtin_not(LuaVM_RT$ vm, LuaVariable$ x) {
        return IL___builtin_IS_TRUTHY(x).negated();
    }

    public static LuaVariable$ IL__len(LuaVM_RT$ vm, LuaVariable$ x) {
        if (x.isString()){
            return new LuaNumber$(((LuaString$)x).getLength());
        } else if (x.isTable()) {
            var tbl = ((LuaTable$) x);
            var f = tbl._luaGetMtFunc("__len");
            return f != null ? f.Invoke(x)[0] : tbl.getLength();
        } else {
            vm.yeet(new LuaTypeError$("attempted to perform operation 'len %s'".formatted(x.getType().fancyName)));
            throw new RuntimeException("should not be reached");
        }
    }

    public static LuaVariable$ IL__unm(LuaVM_RT$ vm, LuaVariable$ x) {
        x = IL___COERCEToNum(x);
        if (!x.isNumber()) { // if the arg isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation 'unm %s'".formatted(x.getType().fancyName)));            
        }
        assert x instanceof LuaNumber$;
        return ((LuaNumber$) x).unm();
    }

    public static LuaVariable$ IL__bnot(LuaVM_RT$ vm, LuaVariable$ x) {
        x = IL___COERCEToBw(x);
        if (!x.isNumberBw()) { // if the arg isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTable$) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation 'bnot %s'".formatted(x.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBw$;
        return ((LuaNumberBw$) x).bnot();
    }
}