package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;

import java.util.Arrays;
import java.util.Stack;

public final class LuaStackFrame {
    public final LuaObject[] locals;
    public final LuaFunction lFunction;
    public LuaObject[] eStack;
    public int resume;
    public boolean resumable;
    public final Stack<LuaObject> toClose;
    public final Stack<JStackFrame> internalCallStack;

    public LuaStackFrame(LuaObject[] locals, LuaFunction lFunction) {
        this.locals = locals;
        this.lFunction = lFunction;
        this.resume = -1;
        resumable = true;
        toClose = new Stack<>();
        internalCallStack = new Stack<>();
    }

    public static class JStackFrame {
        public final LFunc jMethod;
        public LuaObject[] eStack;
        public int resume;
        public final Stack<LuaObject> toClose;

        public JStackFrame(LFunc jMethod) {
            this.jMethod = jMethod;
            resume = -1;
            toClose = new Stack<>();
        }
    }

    public void clear() {
        Arrays.setAll(locals, null);
        eStack = null;
        resume = -1;
        resumable = true;
        if (!toClose.isEmpty()) {
            throw new InternalLuaRuntimeError("%s values have not been closed on stack frame clear".formatted(toClose.size()));
        }
        internalCallStack.clear();
    }

    public void addClosable(LuaObject obj) {
        if (internalCallStack.isEmpty()) {
            toClose.push(obj);
        } else {
            internalCallStack.peek().toClose.push(obj);
        }
    }

    public LuaObject getNextClosable() {
        if (internalCallStack.isEmpty()) {
            return toClose.pop();
        } else {
            return internalCallStack.peek().toClose.pop();
        }
    }

    public void pushJFrame(LFunc jMethod) {
        internalCallStack.push(new JStackFrame(jMethod));
    }
}
