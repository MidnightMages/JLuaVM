package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;

public final class InternalCallFrame extends AbstractCallStackFrame {
    private final LFunc callable;
    public LuaObject[] arguments;

    public InternalCallFrame(LuaObject[] locals, LFunc callable, LuaObject[] arguments) {
        super(locals);
        this.callable = callable;
        this.arguments = arguments;
    }

    @Override
    public void execute(LuaVM_RT vm) {
        callable.invoke(vm, locals, arguments, resume, expressionStack, rvals);
    }
}
