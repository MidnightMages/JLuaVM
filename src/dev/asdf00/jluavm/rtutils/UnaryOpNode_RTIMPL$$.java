package dev.asdf00.jluavm.rtutils;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.typesOLD.*;
import dev.asdf00.jluavm.types.*;
import dev.asdf00.jluavm.exceptions.runtime.*;

public class UnaryOpNode_RTIMPL$$ {
    public static LuaVariableOLD IL___COERCEToNum(LuaVariableOLD a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToNum(a);
    }
    public static LuaVariableOLD IL___COERCEToBw(LuaVariableOLD a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToBw(a);
    }
    public static LuaVariableOLD IL___COERCEToStr(LuaVariableOLD a){
        return BinaryOpNode_RTIMPL$$.IL___COERCEToStr(a);
    }
    public static LuaBooleanOLD IL___builtin_IS_TRUTHY(LuaVariableOLD x) {
        return (x.isNil() || x.isBoolean() && !((LuaBooleanOLD)x).getValue()) ? LuaBooleanOLD.FALSE : LuaBooleanOLD.TRUE;
    }

    public static LuaVariableOLD IL___builtin_not(LuaVM_RT vm, LuaVariableOLD x) {
        return IL___builtin_IS_TRUTHY(x).negated();
    }

    public static LuaVariableOLD IL__len(LuaVM_RT vm, LuaVariableOLD x) {
        if (x.isString()){
            return new LuaNumberOLD(((LuaStringOLD)x).getLength());
        } else if (x.isTable()) {
            var tbl = ((LuaTableOLD) x);
            var f = tbl._luaGetMtFunc("__len");
            return f != null ? f.Invoke(x)[0] : tbl.getLength();
        } else {
            vm.yeet(new LuaTypeError$("attempted to perform operation 'len %s'".formatted(x.getType().fancyName)));
            throw new RuntimeException("should not be reached");
        }
    }

    public static LuaVariableOLD IL__unm(LuaVM_RT vm, LuaVariableOLD x) {
        x = IL___COERCEToNum(x);
        if (!x.isNumber()) { // if the arg isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation 'unm %s'".formatted(x.getType().fancyName)));            
        }
        assert x instanceof LuaNumberOLD;
        return ((LuaNumberOLD) x).unm();
    }

    public static LuaVariableOLD IL__bnot(LuaVM_RT vm, LuaVariableOLD x) {
        x = IL___COERCEToBw(x);
        if (!x.isNumberBw()) { // if the arg isnt of the required type after coercion, look for a metatable
            if (x.isTable()){
                var f = ((LuaTableOLD) x)._luaGetMtFunc("concat");
                if (f != null) {
                    return f.Invoke(x)[0]; // metamethods can only return one value
                }
            }
            vm.yeet(new LuaTypeError$("attempted to perform operation 'bnot %s'".formatted(x.getType().fancyName)));            
        }
        assert x instanceof LuaNumberBwOLD;
        return ((LuaNumberBwOLD) x).bnot();
    }
}