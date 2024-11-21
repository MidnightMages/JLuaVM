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

    public FunctionCallFrame(LuaObject[] locals, LuaFunction lFunc) {
        super(locals);
        this.lFunc = lFunc;
        this.scopes = new Stack<>();
    }

    public AbstractCallStackFrame getTopFrame() {
        return scopes.isEmpty() ? this : scopes.peek();
    }

    public void enterScope(LFunc localTarget, LuaObject[] args) {
        scopes.push(new InternalCallFrame(locals, localTarget, args));
    }

    public void exitScope(LuaObject[] rvals) {
        assert !scopes.isEmpty();
        var scp = scopes.pop();
        if (!scp.closables.isEmpty()) {
            throw new InternalLuaRuntimeError("not all closable values were closed");
        }
        getTopFrame().rvals = rvals;
    }

    @Override
    public void execute(LuaVM_RT vm) {
        lFunc.invoke(vm, locals, resume, expressionStack, rvals);
    }

    public void reset() {
        if (!closables.isEmpty()) {
            throw new InternalLuaRuntimeError("not all closable values were closed");
        }
        init();
        Arrays.fill(locals, null);
    }
}
