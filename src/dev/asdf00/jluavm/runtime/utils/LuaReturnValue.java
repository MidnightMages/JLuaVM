package dev.asdf00.jluavm.runtime.utils;

import dev.asdf00.jluavm.runtime.errors.AbstractLuaError;
import dev.asdf00.jluavm.runtime.types.ILuaVariable;
import dev.asdf00.jluavm.runtime.types.LuaFunction;

public class LuaReturnValue {
    // if a function returns normally, retVals will be filled
    public ILuaVariable[] retVals;

    // if a function return exceptionally, error will be filled
    public AbstractLuaError error;

    // if a function is intended to be resumed (e.g. yield or call), resume and expressionStack will be filled
    public int resume;
    public ILuaVariable[] expressionStack;

    // if a function is to be called,
    public LFunc localTarget;
    public LuaFunction externalTarget;
    public ILuaVariable[] args;
    public int returnValuePosition;
    public boolean isTailCall;

    public static LuaReturnValue callExternal(int resume, ILuaVariable[] expressionStack, LuaFunction externalTarget, ILuaVariable[] args) {
        return null;
    }

    public static LuaReturnValue callInternal(int resume, ILuaVariable[] expressionStack, LFunc localTarget, ILuaVariable[] args) {
        return null;
    }

    public static LuaReturnValue error(AbstractLuaError err) {
        return null;
    }

    public static LuaReturnValue returnValue(ILuaVariable... retVals) {
        return null;
    }
}
