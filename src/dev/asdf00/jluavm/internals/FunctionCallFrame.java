package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    Stack<InternalCallFrame> getScopes() {
        return scopes;
    }

    public void enterScope(LFunc localTarget, String targetName, LuaObject[] args) {
        var cTop = getTopFrame();
        scopes.push(new InternalCallFrame(locals, cTop.startLocals + cTop.localCnt + cTop.curInlinedLocalCnt, localTarget, targetName, args));
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

    public LuaObject getNextClosable() {
        var scopes = getScopes();
        for (int i = scopes.size() - 1; i >= 0; i--) {
            var scope = scopes.get(i);

            if (!scope.closables.empty())
                return scope.closables.pop();
        }

        if (!closables.empty()) {
            return closables.pop();
        }

        throw new InternalLuaRuntimeError("Closable stack is empty!");
    }

    public byte[] serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs) {
        var bb = new ByteArrayBuilder();
        bb.append(LuaObject.of(lFunc).serialize(serialData, mappedObjs))
                .append(failCnt)
                .append(isResumable)
                .append(isProtected);
        if (msgHandler == null) {
            bb.append(false);
        } else {
            bb.append(true).append(LuaObject.of(lFunc).serialize(serialData, mappedObjs));
        }

        // serialize inner scopes
        for (int i = 0; i < scopes.size(); i++) {
            var innerBytes = scopes.get(i).serialize(serialData, mappedObjs);
            bb.append(innerBytes.length).appendAll(innerBytes);
        }

        return bb.toArray();
    }
}
