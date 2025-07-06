package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;
import dev.asdf00.jluavm.utils.ByteArrayReader;

import java.util.List;
import java.util.Map;
import java.util.Stack;

import static dev.asdf00.jluavm.runtime.utils.StateDeserializer.maybeNull;

public abstract sealed class AbstractCallStackFrame permits FunctionCallFrame, InternalCallFrame {
    // effectively finals
    public final LuaObject[] locals;
    public final int startLocals;
    public int localCnt;  // late init
    public int curInlinedLocalCnt;  // late init
    public final Stack<LuaObject> closables;

    // operational fields
    public int resume;
    public LuaObject[] expressionStack;
    public LuaObject[] rvals;

    protected AbstractCallStackFrame(DataContainer container) {
        locals = container.locals();
        startLocals = container.startLocals();
        localCnt = container.localCnt();
        curInlinedLocalCnt = container.curInlinedLocalCnt();
        closables = container.closables();
        resume = container.resume();
        expressionStack = container.expressionStack();
        rvals = container.rvals();
    }

    public AbstractCallStackFrame(LuaObject[] locals, int startLocals) {
        this.locals = locals;
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

    protected void serialize(List<byte[]> serialData, Map<LuaObject, Integer> mappedObjs, ByteArrayBuilder bb) {
        bb.append(LuaObject.of(locals).serialize(serialData, mappedObjs))
                .append(startLocals)
                .append(localCnt)
                .append(curInlinedLocalCnt)
                .append(resume)
                .append(expressionStack == null
                        ? -1
                        : LuaObject.of(expressionStack).serialize(serialData, mappedObjs))
                .append(rvals == null
                        ? -1
                        : LuaObject.of(rvals).serialize(serialData, mappedObjs));

        int size = closables.size();
        bb.append(size);
        for (int i = 0; i < size; i++) {
            bb.append(closables.get(i).serialize(serialData, mappedObjs));
        }
    }

    protected static DataContainer abstractDeserialize(LuaObject[] objs, ByteArrayReader rdr) {
        LuaObject[] locals = objs[rdr.readInt()].asArray();
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
        return new DataContainer(locals, startLocals, localCnt, curInlinedLocalCnt, closables, resume, expressionStack, rvals);
    }

    protected record DataContainer(
            LuaObject[] locals,
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
