package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Stack;

public abstract sealed class AbstractCallStackFrame permits FunctionCallFrame, InternalCallFrame {
    // effectively finals
    public final LuaObject[] locals;
    public final int startLocals;
    public int localCnt;  // late init
    public final Stack<LuaObject> closables;

    // operational fields
    public int resume;
    public LuaObject[] expressionStack;
    public LuaObject[] rvals;

    public AbstractCallStackFrame(LuaObject[] locals, int startLocals) {
        this.locals = locals;
        this.startLocals = startLocals;
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
