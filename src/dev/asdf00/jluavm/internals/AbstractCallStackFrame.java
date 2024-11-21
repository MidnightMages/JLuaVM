package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Stack;

public abstract sealed class AbstractCallStackFrame permits FunctionCallFrame, InternalCallFrame {
    public final LuaObject[] locals;
    public int resume;
    public LuaObject[] expressionStack;
    public LuaObject[] rvals;
    public int localCnt;
    public final Stack<LuaObject> closables;

    public AbstractCallStackFrame(LuaObject[] locals) {
        this.locals = locals;
        this.closables = new Stack<>();
        init();
    }

    protected final void init() {
        resume = -1;
        expressionStack = null;
        rvals = null;
        localCnt = 0;
        assert closables.isEmpty();
    }

    public abstract void execute(LuaVM_RT vm);

    public abstract void reset();
}
