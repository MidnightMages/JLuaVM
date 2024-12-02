package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;

import java.util.Arrays;
import java.util.Stack;

public final class FunctionCallFrame extends AbstractCallStackFrame {
    public final LuaFunction lFunc;
    private final Stack<InternalCallFrame> scopes;
    public int failCnt;
    public boolean isResumable;
    public boolean isProtected;
    public LuaFunction msgHandler;

    public FunctionCallFrame(LuaObject[] locals, LuaFunction lFunc) {
        super(locals, 0);
        this.lFunc = lFunc;
        this.scopes = new Stack<>();
        isResumable = true;
        failCnt = 0;
        isProtected = false;
        msgHandler = null;
    }

    public AbstractCallStackFrame getTopFrame() {
        return scopes.isEmpty() ? this : scopes.peek();
    }

    public void enterScope(LFunc localTarget, LuaObject[] args) {
        var cTop = getTopFrame();
        scopes.push(new InternalCallFrame(locals, cTop.startLocals + cTop.localCnt, localTarget, args));
    }

    public void exitScope(LuaObject[] rvals) {
        assert !scopes.isEmpty();
        var scp = scopes.pop();
        if (!scp.closables.isEmpty()) {
            throw new InternalLuaRuntimeError("not all closable values were closed");
        }
        Arrays.fill(locals, scp.startLocals, scp.startLocals + scp.localCnt, null);
        getTopFrame().rvals = rvals;
    }

    public void asProtected(LuaFunction msgHandler) {
        isProtected = true;
        this.msgHandler = msgHandler;
    }

    @Override
    public void execute(LuaVM_RT vm) {
        lFunc.invoke(vm, locals, resume, expressionStack, rvals);
        rvals = null;
    }

    @Override
    public void reset() {
        if (!closables.isEmpty()) {
            throw new InternalLuaRuntimeError("not all closable values were closed");
        }
        init();
        Arrays.fill(locals, null);
    }
}
