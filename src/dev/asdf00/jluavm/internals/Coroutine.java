package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Objects;
import java.util.Stack;

public final class Coroutine {
    public final LuaFunction rootFunc;

    public final Stack<FunctionCallFrame> luaCallStack;

    public boolean rootFail;
    public LuaObject[] rootReturned;

    public State state;
    public boolean isYieldable;
    public Coroutine yieldTo;
    public LuaObject selfLuaObject;

    private Coroutine(LuaFunction rootFunc, Stack<FunctionCallFrame> luaCallStack, boolean rootFail, LuaObject[] rootReturned, State state) {
        this.rootFunc = rootFunc;
        this.luaCallStack = luaCallStack;
        this.rootFail = rootFail;
        this.rootReturned = rootReturned;
        this.state = state;
        isYieldable = true;
        yieldTo = null;

        selfLuaObject = LuaObject.of(this);
    }

    public static Coroutine create(LuaFunction rootFunc) {
        var r = new Coroutine(Objects.requireNonNull(rootFunc), new Stack<>(), false, null, State.CREATED);
        r.luaCallStack.push(new FunctionCallFrame(new LuaObject[rootFunc.getMaxLocalsSize()], rootFunc));
        return r;
    }

    public enum State {
        CREATED("suspended"),
        RUNNING("running"),
        SUSPENDED("suspended"),
        BLOCKED("normal"),
        DEAD("dead");

        public final String luaName;

        State(String luaName) {
            this.luaName = luaName;
        }
    }
}
