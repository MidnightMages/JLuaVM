package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.Stack;

public class Coroutine {
    public final LuaFunction rootFunc;

    public Stack<FunctionCallFrame> luaCallStack;

    public boolean rootFail;
    public LuaObject[] rootReturned;

    public State state;

    public Coroutine(LuaFunction rootFunc, Stack<FunctionCallFrame> luaCallStack, boolean rootFail, LuaObject[] rootReturned, State state) {
        this.rootFunc = rootFunc;
        this.luaCallStack = luaCallStack;
        this.rootFail = rootFail;
        this.rootReturned = rootReturned;
        this.state = state;
    }

    public enum State {
        RUNNING,
        // TODO
    }
}
