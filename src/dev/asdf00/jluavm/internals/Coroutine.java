package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;
import dev.asdf00.jluavm.utils.ByteArrayReader;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

import static dev.asdf00.jluavm.runtime.utils.StateDeserializer.maybeNull;

public final class Coroutine {
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
        // ensure that the rootFunc has a lua object attached
        LuaObject.of(rootFunc);
        // create coroutine
        var r = new Coroutine(Objects.requireNonNull(rootFunc), new Stack<>(), false, null, State.CREATED);
        r.luaCallStack.push(new FunctionCallFrame(new LuaObject[rootFunc.getMaxLocalsSize()], rootFunc));
        return r;
    }

    public void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb) {
        bb.append(LuaObject.of(rootFunc).serialize(serialData, mappedObjs))
                .append(rootFail)
                .append(rootReturned == null
                        ? -1 // null
                        : LuaObject.of(rootReturned).serialize(serialData, mappedObjs))
                .append((byte) state.ordinal())
                .append(isYieldable)
                .append(yieldTo == null
                        ? -1 // null
                        : yieldTo.selfLuaObject.serialize(serialData, mappedObjs));

        for (int i = 0; i < luaCallStack.size(); i++) {
            var functionFrameBytes = luaCallStack.get(i).serialize(serialData, mappedObjs);
            bb.append(functionFrameBytes.length).appendAll(functionFrameBytes);
        }
    }

    public static Tuple<Coroutine, LuaObject> deserialize(LuaObject[] objs, LuaObject self, ByteArrayReader rdr) {
        LuaFunction func = objs[rdr.readInt()].getFunc();
        boolean fail = rdr.readBool();
        LuaObject[] returned = maybeNull(objs, rdr.readInt(), LuaObject::asArray);
        State state = State.values()[rdr.readByte()];
        boolean isYieldable = rdr.readBool();
        // this coroutine might not exist yet, we return the corresponding lua object in a tuple to resolve later
        LuaObject yieldTo = maybeNull(objs, rdr.readInt());
        // still to read: stack

        Coroutine co = new Coroutine(func, new Stack<>(), fail, returned, state);
        co.isYieldable = isYieldable;
        co.selfLuaObject = self;

        // construct stack
        while (rdr.remaining() > 0) {
            // still a frame to read
            var fRead = rdr.slice(rdr.readInt());
            co.luaCallStack.push(FunctionCallFrame.deserialize(objs, fRead));
        }

        return new Tuple<>(co, yieldTo);
    }
}
