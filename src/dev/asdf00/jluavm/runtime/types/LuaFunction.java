package dev.asdf00.jluavm.runtime.types;

import dev.asdf00.jluavm.internals.LuaVM_RT;
import dev.asdf00.jluavm.runtime.utils.LuaReturnValue;

public abstract class LuaFunction implements ILuaVariable {
    public final ILuaVariable[] closures;
    public ILuaVariable _ENV;

    public LuaFunction(ILuaVariable[] closures) {
        this.closures = closures;
    }

    public abstract LuaReturnValue invoke(LuaVM_RT vm, ILuaVariable[] stackFrame, int resume, ILuaVariable[] expressionStack, LuaArray returned);
}
