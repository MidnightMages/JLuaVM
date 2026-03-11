package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;
import dev.asdf00.jluavm.utils.ByteArrayReader;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;

import static dev.asdf00.jluavm.runtime.utils.StateDeserializer.maybeNull;

public abstract sealed class AbstractCallStackFrame permits FunctionCallFrame, InternalCallFrame {
    // effectively finals
    public final int startLocals;
    public int localCnt;  // late init
    public int curInlinedLocalCnt;  // late init
    public final Stack<LuaObject> closables;

    // operational fields
    public int resume;
    public LuaObject[] expressionStack;
    public LuaObject[] rvals;

    protected AbstractCallStackFrame(DataContainer container) {
        startLocals = container.startLocals();
        localCnt = container.localCnt();
        curInlinedLocalCnt = container.curInlinedLocalCnt();
        closables = container.closables();
        resume = container.resume();
        expressionStack = container.expressionStack();
        rvals = container.rvals();
    }

    public AbstractCallStackFrame(int startLocals) {
        this.startLocals = startLocals;
        this.closables = new Stack<>();
        init();
    }

    protected final void init() {
        resume = -1;
        expressionStack = null;
        rvals = null;
        localCnt = 0;
        curInlinedLocalCnt = 0;
        assert closables.isEmpty();
    }

    protected void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb, Object additionalData) {
        bb.append(startLocals)
                .append(localCnt)
                .append(curInlinedLocalCnt)
                .append(resume)
                .append(expressionStack == null
                        ? -1
                        : LuaObject.of(expressionStack).serialize(serialData, mappedObjs, additionalData))
                .append(rvals == null
                        ? -1
                        : LuaObject.of(rvals).serialize(serialData, mappedObjs, additionalData));

        int size = closables.size();
        bb.append(size);
        for (int i = 0; i < size; i++) {
            bb.append(closables.get(i).serialize(serialData, mappedObjs, additionalData));
        }
    }

    protected static DataContainer abstractDeserialize(LuaObject[] objs, ByteArrayReader rdr) {
        int startLocals = rdr.readInt();
        int localCnt = rdr.readInt();
        int curInlinedLocalCnt = rdr.readInt();
        int resume = rdr.readInt();
        LuaObject[] expressionStack = maybeNull(objs, rdr.readInt(), LuaObject::asArray);
        LuaObject[] rvals = maybeNull(objs, rdr.readInt(), LuaObject::asArray);
        int closeCnt = rdr.readInt();
        Stack<LuaObject> closables = new Stack<>();
        for (int i = 0; i < closeCnt; i++) {
            closables.push(objs[rdr.readInt()]);
        }
        return new DataContainer(startLocals, localCnt, curInlinedLocalCnt, closables, resume, expressionStack, rvals);
    }

    protected record DataContainer(
            int startLocals,
            int localCnt,
            int curInlinedLocalCnt,
            Stack<LuaObject> closables,
            int resume,
            LuaObject[] expressionStack,
            LuaObject[] rvals
    ) {
    }

    public abstract void execute(LuaVM_RT vm);

    public abstract void reset();
}
