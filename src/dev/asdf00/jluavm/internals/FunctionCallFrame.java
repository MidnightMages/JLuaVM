package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;
import dev.asdf00.jluavm.utils.ByteArrayReader;

import java.util.*;

public final class FunctionCallFrame extends AbstractCallStackFrame {
    public final LuaObject[] locals;
    public final LuaFunction lFunc;
    private final Stack<InternalCallFrame> scopes;
    public int failCnt;
    public boolean isResumable;
    public boolean isProtected;
    public LuaFunction msgHandler;

    public int lastLine = -1;
    public String lastName = "?";
    public boolean tailCalled = false;

    private FunctionCallFrame(DataContainer container, LuaObject[] locals, LuaFunction lFunc, Stack<InternalCallFrame> scopes,
                              int failCnt, boolean isResumable, boolean isProtected, LuaFunction msgHandler) {
        super(container);
        this.locals = locals;
        this.lFunc = lFunc;
        this.scopes = scopes;
        this.failCnt = failCnt;
        this.isResumable = isResumable;
        this.isProtected = isProtected;
        this.msgHandler = msgHandler;
    }

    public FunctionCallFrame(LuaObject[] locals, LuaFunction lFunc) {
        super(0);
        this.locals = locals;
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

    public void enterScope(int localsStart, LFunc localTarget, String targetName, LuaObject[] args) {
        scopes.push(new InternalCallFrame(this, localsStart, localTarget, targetName, args));
    }

    public void exitScope(LuaObject[] rvals) {
        assert !scopes.isEmpty();
        var scp = scopes.pop();
        if (!scp.closables.isEmpty()) {
            throw new InternalLuaRuntimeError("not all closable values were closed");
        }
        if (scp.startLocals >= 0) {
            Arrays.fill(locals, scp.startLocals, scp.startLocals + scp.localCnt, null);
        }
        getTopFrame().rvals = rvals;
    }

    public void asProtected(LuaFunction msgHandler) {
        isProtected = true;
        this.msgHandler = msgHandler;
    }

    @Override
    public void execute(LuaVM_RT vm) {
        try {
            lFunc.invoke(vm, locals, resume, expressionStack, rvals);
        } catch (LuaJavaError e) {
            vm.error(LuaObject.of(e.getMessage() + "\nJava Stacktrace:\n  " +
                    String.join("\n  ", Arrays.stream(e.getStackTrace())
                            .limit(e.getStackTrace().length - new Exception().getStackTrace().length)
                            .map(ste -> "at %s.%s(%s:%d)".formatted(
                                    ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber()))
                            .toList())));
        }
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

    byte[] serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, Object additionalData) {
        var bb = new ByteArrayBuilder();
        serialize(serialData, mappedObjs, bb, additionalData);
        bb.append(LuaObject.of(locals).serialize(serialData, mappedObjs, additionalData));
        bb.append(LuaObject.of(lFunc).serialize(serialData, mappedObjs, additionalData))
                .append(failCnt)
                .append(isResumable)
                .append(isProtected);
        if (msgHandler == null) {
            bb.append(-1);
        } else {
            bb.append(LuaObject.of(msgHandler).serialize(serialData, mappedObjs, additionalData));
        }

        bb.append(lastLine)
                .append(LuaObject.of(lastName).serialize(serialData, mappedObjs, additionalData))
                .append(tailCalled);

        // serialize inner scopes
        for (int i = 0; i < scopes.size(); i++) {
            var innerBytes = scopes.get(i).serialize(serialData, mappedObjs, additionalData);
            bb.append(innerBytes.length).appendAll(innerBytes);
        }

        return bb.toArray();
    }

    static FunctionCallFrame deserialize(LuaObject[] objs, ByteArrayReader rdr) {
        var superData = abstractDeserialize(objs, rdr);
        LuaObject[] locals = objs[rdr.readInt()].asArray();
        LuaFunction func = objs[rdr.readInt()].getFunc();
        int failCnt = rdr.readInt();
        boolean isResumable = rdr.readBool();
        boolean isProtected = rdr.readBool();
        int msghIdx = rdr.readInt();
        LuaFunction msgHandler = msghIdx >= 0 ? objs[msghIdx].getFunc() : null;

        int lastLine = rdr.readInt();
        String lastName = objs[rdr.readInt()].getString();
        boolean tailCalled = rdr.readBool();

        Stack<InternalCallFrame> scopes = new Stack<>();
        var nu = new FunctionCallFrame(superData, locals, func, scopes, failCnt, isResumable, isProtected, msgHandler);
        nu.lastLine = lastLine;
        nu.lastName = lastName;
        nu.tailCalled = tailCalled;

        while (rdr.remaining() > 0) {
            // still internal scopes to deserialize
            scopes.push(InternalCallFrame.deserialize(nu, func, objs, rdr.slice(rdr.readInt())));
        }
        return nu;
    }
}
