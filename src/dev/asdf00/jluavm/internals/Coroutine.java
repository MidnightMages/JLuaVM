package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;
import dev.asdf00.jluavm.utils.ByteArrayReader;
import dev.asdf00.jluavm.utils.Triple;
import dev.asdf00.jluavm.utils.Tuple;

import java.util.*;

import static dev.asdf00.jluavm.runtime.utils.StateDeserializer.maybeNull;

public final class Coroutine {
    public enum State {
        CREATED("suspended", true),
        RUNNING("running", false),
        SUSPENDED("suspended", true),
        BLOCKED("normal", false),
        DEAD("dead", false),
        PREEMPTED_RESUMABLE("preempted_resumable", true),
        PREEMPTED_BLOCKED("preempted_blocked", false);

        public final String luaName;
        public final boolean resumable;

        State(String luaName, boolean resumable) {
            this.luaName = luaName;
            this.resumable = resumable;
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

    public Coroutine resumePreempted;
    public long preemptAt = -1;

    private Coroutine(LuaFunction rootFunc, Stack<FunctionCallFrame> luaCallStack, boolean rootFail, LuaObject[] rootReturned, State state) {
        this.rootFunc = rootFunc;
        this.luaCallStack = luaCallStack;
        this.rootFail = rootFail;
        this.rootReturned = rootReturned;
        this.state = state;
        isYieldable = true;
        yieldTo = null;
        selfLuaObject = LuaObject.of(this);
        resumePreempted = null;
    }

    public static Coroutine create(LuaFunction rootFunc) {
        // ensure that the rootFunc has a lua object attached
        LuaObject.of(rootFunc);
        // create coroutine
        var r = new Coroutine(Objects.requireNonNull(rootFunc), new Stack<>(), false, null, State.CREATED);
        r.luaCallStack.push(new FunctionCallFrame(new LuaObject[rootFunc.getMaxLocalsSize()], rootFunc));
        return r;
    }

    public void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb, Object additionalData) {
        bb.append(LuaObject.of(rootFunc).serialize(serialData, mappedObjs, additionalData))
                .append(rootFail)
                .append(rootReturned == null
                        ? -1 // null
                        : LuaObject.of(rootReturned).serialize(serialData, mappedObjs, additionalData))
                .append((byte) state.ordinal())
                .append(isYieldable)
                .append(yieldTo == null
                        ? -1 // null
                        : yieldTo.selfLuaObject.serialize(serialData, mappedObjs, additionalData))
                .append(resumePreempted == null
                        ? -1 // null
                        : resumePreempted.selfLuaObject.serialize(serialData, mappedObjs, additionalData))
                .append(preemptAt);

        for (int i = 0; i < luaCallStack.size(); i++) {
            var functionFrameBytes = luaCallStack.get(i).serialize(serialData, mappedObjs, additionalData);
            bb.append(functionFrameBytes.length).appendAll(functionFrameBytes);
        }
    }

    public static Triple<Coroutine, LuaObject, LuaObject> deserialize(LuaObject[] objs, LuaObject self, ByteArrayReader rdr) {
        LuaFunction func = objs[rdr.readInt()].getFunc();
        boolean fail = rdr.readBool();
        LuaObject[] returned = maybeNull(objs, rdr.readInt(), LuaObject::asArray);
        State state = State.values()[rdr.readByte()];
        boolean isYieldable = rdr.readBool();
        // this coroutine might not exist yet, we return the corresponding lua object in a tuple to resolve later
        LuaObject yieldTo = maybeNull(objs, rdr.readInt());
        LuaObject resumePreempted = maybeNull(objs, rdr.readInt());
        long preemptAt = rdr.readLong();
        // still to read: stack

        Coroutine co = new Coroutine(func, new Stack<>(), fail, returned, state);
        co.isYieldable = isYieldable;
        co.selfLuaObject = self;
        co.preemptAt = preemptAt;

        // construct stack
        while (rdr.remaining() > 0) {
            // still a frame to read
            var fRead = rdr.slice(rdr.readInt());
            co.luaCallStack.push(FunctionCallFrame.deserialize(objs, fRead));
        }

        return new Triple<>(co, yieldTo, resumePreempted);
    }
}
