package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.api.functions.ApiFunctionRegistry;
import dev.asdf00.jluavm.runtime.types.AbstractGeneratedLuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaJavaApiFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.runtime.utils.Singletons;
import dev.asdf00.jluavm.utils.ByteArrayBuilder;
import dev.asdf00.jluavm.utils.Quadruple;
import dev.asdf00.jluavm.utils.Triple;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class LuaVM_RT extends LuaVM {
    // it was brought to me in a dream that this is the optimal number, for sure
    public static final int ERROR_LOOP_GRACE_CNT = 256;
    public static final int MAX_LUA_STACK_SIZE = 1024 * 1024 * 1024;

    public static final int STATE_SERIALIZATION_VERSION = 0;

    public LuaVM_RT() {
        luaCallStack = new Stack<>();
        curFuncFrame = null;
    }

    public LuaVM_RT(Map<String, ApiFunctionRegistry> registries, LuaFunction rootFunc) {
        super(registries);
        this.rootFunc = rootFunc;

        luaCallStack = new Stack<>();
        curFuncFrame = null;
    }

    /**
     * Create LuaVM from serialized state
     * @param registries
     * @param state
     */
    public LuaVM_RT(Map<String, ApiFunctionRegistry> registries, Quadruple<Coroutine, Coroutine, Boolean, Boolean> state) {
        this(registries, state.w().rootFunc);
        rootCoroutine = state.w();
        currentCoroutine = state.x();
        isErroring = state.y();
        requestedStop = state.z();

        luaCallStack = currentCoroutine.luaCallStack;
        curFuncFrame = luaCallStack.peek();
    }

    @Override
    public VmResult runWithArgs(LuaObject... rootArgs) {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("can not run already running VM");
        }
        if (rootCoroutine != null || requestedStop) {
            throw new IllegalStateException("can not do fresh start on non-clear state");
        }
        if (rootFunc == null) {
            return new VmResult(VmRunState.EXECUTION_ERROR, new LuaObject[]{LuaObject.of("Invalid root function")});
        }
        rootCoroutine = Coroutine.create(rootFunc);
        rootCoroutine.isYieldable = false;
        rootCoroutine.luaCallStack.peek().getTopFrame().locals[0] = LuaObject.of(rootArgs);
        setCoroutine(rootCoroutine);
        execLoop();
        isRunning.set(false);
        if (requestedStop) {
            return new VmResult(VmRunState.PAUSED, Singletons.EMPTY_LUA_OBJ_ARRAY);
        } else {
            var co = rootCoroutine;
            rootCoroutine = null;
            return new VmResult(co.rootFail ? VmRunState.EXECUTION_ERROR : VmRunState.SUCCESS, co.rootReturned);
        }
    }

    @Override
    public VmResult runContinue() {
        if (!isRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("can not run already running VM");
        }
        if (rootCoroutine == null || !requestedStop) {
            throw new IllegalStateException("can not continue on clear state");
        }
        requestedStop = false;
        execLoop();
        isRunning.set(false);
        if (requestedStop) {
            return new VmResult(VmRunState.PAUSED, Singletons.EMPTY_LUA_OBJ_ARRAY);
        } else {
            var co = rootCoroutine;
            rootCoroutine = null;
            return new VmResult(co.rootFail ? VmRunState.EXECUTION_ERROR : VmRunState.SUCCESS, co.rootReturned);
        }
    }

    @Override
    public byte[] serialize() {
        ArrayList<byte[]> serialData = new ArrayList<>();
        HashMap<LuaObject, Integer> mappedObjs = new HashMap<>();
        int curCoIdx = currentCoroutine.selfLuaObject.serialize(serialData, mappedObjs);
        var bb = new ByteArrayBuilder(serialData.stream().mapToInt(a -> a.length + 4).sum() + 4 * 4);
        bb.append(STATE_SERIALIZATION_VERSION)
                .append(mappedObjs.get(rootCoroutine.selfLuaObject))
                .append(curCoIdx)
                .append(isErroring)
                .append(requestedStop)
                .append(serialData.size());
        for (int i = 0; i < serialData.size(); i++) {
            var a = serialData.get(i);
            bb.append(a.length).appendAll(a);
        }
        return bb.toArray();
    }

    // =================================================================================================================
    // main runtime methods
    // =================================================================================================================

    // magic state
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public Random lMathRandom = new Random();

    private boolean isErroring;

    private Coroutine rootCoroutine;
    private Coroutine currentCoroutine;

    private Stack<FunctionCallFrame> luaCallStack;
    private FunctionCallFrame curFuncFrame;

    private void execLoop() {
        for (; ; ) {
            isErroring = false;
            if (curFuncFrame == null || requestedStop) {
                // luavm exit
                break;
            }
            curFuncFrame.getTopFrame().execute(this);
            safepoint();
        }
    }

    private void safepoint() {
        // do nothing for now
    }

    // =================================================================================================================
    // access helper methods
    // =================================================================================================================

    public String printStacktrace(int skip) {
        var sb = new StringBuilder();
        sb.append("stack traceback:");
        for (int i = luaCallStack.size() - 1 - skip; i >= 0; i--) {
            sb.append("\n\t");
            var frame = luaCallStack.get(i);
            if (frame.lFunc instanceof AbstractGeneratedLuaFunction glf) {
                sb.append(glf.compilationUnit).append(':').append(frame.lastLine);
            } else {
                sb.append(frame.lFunc.getCompilationUnit());
            }
            sb.append(": in ");
            if (i > 0) {
                // get message from upper layer in callstack
                String msg = luaCallStack.get(i - 1).lastName;
                if (msg == null || msg.isEmpty()) {
                    msg = "function ";
                }
                sb.append(msg);
                // if the upper layer message calls for a function name, we append the current one
                if (msg.equals("function ")) {
                    // append the current function name
                    if (frame.lFunc instanceof AbstractGeneratedLuaFunction glf) {
                        sb.append("<").append(glf.compilationUnit).append(":").append(glf.lineNum).append(">");
                    } else {
                        // this case works, because LuaFunction is sealed
                        var jlf = (LuaJavaApiFunction) frame.lFunc;
                        sb.append("'").append(jlf.registry.getSerialName(jlf)).append("'");
                    }
                }
            } else {
                sb.append("main chunk");
            }
        }
        return sb.toString();
    }

    // =================================================================================================================
    // setup methods
    // =================================================================================================================

    public LuaObject[] registerExpressionStack(int size) {
        var eStack = size == 0 ? Singletons.EMPTY_LUA_OBJ_ARRAY : new LuaObject[size];
        curFuncFrame.getTopFrame().expressionStack = eStack;
        return eStack;
    }

    // to let the vm know how many local variables are used in this scope and need to be cleaned up afterward
    public void registerLocals(int count) {
        curFuncFrame.getTopFrame().localCnt = count;
    }

    public void incrementInlinedLocals(int count) {
        curFuncFrame.getTopFrame().curInlinedLocalCnt += count;
    }

    public void addClosable(LuaObject obj) {
        curFuncFrame.getTopFrame().closables.push(obj);
    }

    public LuaObject getNextClosable() {
        return curFuncFrame.getNextClosable();
    }

    /**
     * @return the table of the environment of the caller
     */
    public LuaObject getCallerEnv() {
        return luaCallStack.size() > 1 ? luaCallStack.get(luaCallStack.size() - 2).lFunc._ENV.unbox() : null;
    }

    public void setProtected(LuaFunction msgHandler) {
        curFuncFrame.asProtected(msgHandler);
    }

    public boolean isFailed() {
        return curFuncFrame.failCnt > 0;
    }

    public boolean isErroring() {
        return isErroring;
    }

    /**
     * Installs the given coroutine as the currently running coroutine and sets its state to RUNNING.
     */
    public void setCoroutine(Coroutine coroutine) {
        currentCoroutine = coroutine;
        luaCallStack = coroutine.luaCallStack;
        curFuncFrame = luaCallStack.peek();
        coroutine.state = Coroutine.State.RUNNING;
    }

    public Coroutine getRootCoroutine() {
        return rootCoroutine;
    }

    public Coroutine getCurrentCoroutine() {
        return currentCoroutine;
    }

    // =================================================================================================================
    // lua vm call magic setup methods (MUST be followed by return, and return must be preceded by exactly one of these, or throw internal lua error)
    // =================================================================================================================

    public void setLastTrace(int line) {
        curFuncFrame.lastLine = line;
    }

    public void setLastTrace(String name) {
        curFuncFrame.lastName = name;
    }

    public void setLastTrace(String name, int line) {
        curFuncFrame.lastName = name;
        curFuncFrame.lastLine = line;
    }

    public void error(LuaObject errMsg) {
        // setup vm for error
        isErroring = true;
        int frmIdx;
        for (frmIdx = luaCallStack.size() - 1; frmIdx >= 0 && !luaCallStack.get(frmIdx).isProtected; frmIdx--) {
            // set all stack frames from the current one until the first protected frame to be un-resumable
            luaCallStack.get(frmIdx).isResumable = false;
        }
        if (frmIdx < 0) {
            // root failure of current coroutine
            // TODO gather stack trace
            currentCoroutine.rootFail = true;
            currentCoroutine.rootReturned = new LuaObject[]{errMsg};
            luaCallStack.clear();
            currentCoroutine.state = Coroutine.State.DEAD;
            if (currentCoroutine.yieldTo != null) {
                // there is a resuming coroutine
                Coroutine stoppingCo = currentCoroutine;
                setCoroutine(currentCoroutine.yieldTo);
            } else {
                assert currentCoroutine == rootCoroutine : "non-root coroutine without resuming coroutine";
                curFuncFrame = null;
            }
        } else {
            var frame = luaCallStack.get(frmIdx);
            frame.failCnt++;
            if (frame.failCnt > ERROR_LOOP_GRACE_CNT) {
                // failed too often in XPCALL message handler, we break the loop by not calling the handler again
                returnValue(LuaObject.of("error in error handling"));
            } else if (frame.msgHandler != null) {
                /**
                 * By setting all frames between the current frame and the XPCALL to not be resumable, the return values
                 * produced by the message handler that is called here are implicitly passed all the way through to the
                 * XPCALL thereby allowing the XPCALL to access the error message.
                 */
                setupCall(frame.msgHandler, errMsg);
            } else {
                // return through all frames to the top resumable frame which in this case should be a PCALL
                returnValue(errMsg);
            }
        }
    }

    public static void packArgsInto(LuaObject[] nuStackFrame, LuaFunction externalTarget, LuaObject... args) {
        int srcArgIdx = 0;
        int srcArgElemIdx = 0;
        int dstIdx = 0;
        var vaDest = externalTarget.hasParamsArg();
        var targetArgCnt = externalTarget.getArgCount();
        LuaObject[] VADestArray = null;

        while (dstIdx < externalTarget.getArgCount()) {
            boolean isLastDest = dstIdx == targetArgCnt - 1;
            if (isLastDest && vaDest) {
                if (srcArgIdx >= args.length) {
                    nuStackFrame[dstIdx] = LuaObject.of(Singletons.EMPTY_LUA_OBJ_ARRAY);
                    break;
                }
                boolean countingPhase = false;
                int VaLen = 0;
                int VaDstIdx = 0;
                do {
                    countingPhase ^= true; // counting phase first, then collection phase
                    if (!countingPhase)
                        VADestArray = new LuaObject[VaLen];

                    for (int i = srcArgIdx; i < args.length; i++) {
                        var srcElem = args[i];
                        if (!srcElem.isArray()) {
                            if (countingPhase) {
                                VaLen++;
                            } else {
                                VADestArray[VaDstIdx++] = srcElem;
                            }
                        } else {
                            var srcElemArray = srcElem.asArray();
                            var lenToTake = srcElemArray.length - srcArgElemIdx;
                            if (countingPhase) {
                                VaLen += lenToTake;
                            } else {
                                System.arraycopy(srcElemArray, srcArgElemIdx, VADestArray, VaDstIdx, lenToTake);
                            }
                        }
                    }
                } while (countingPhase);
                nuStackFrame[dstIdx] = LuaObject.of(VADestArray);
                break;
            }

            if (srcArgIdx < args.length) {
                var srcObj = args[srcArgIdx];
                if (srcObj.isArray()) { // flatten
                    var srcObjArr = srcObj.asArray();
                    if (srcArgElemIdx >= srcObjArr.length) { // src array is empty
                        srcArgElemIdx = 0;
                        srcArgIdx++;
                        continue;
                    }
                    nuStackFrame[dstIdx++] = srcObjArr[srcArgElemIdx++];
                } else {
                    srcArgIdx++;
                    nuStackFrame[dstIdx++] = srcObj;
                }
            } else {
                nuStackFrame[dstIdx++] = LuaObject.NIL;
            }
        }

        assert ((Supplier<Boolean>) () -> {
            var allArgs = Arrays.stream(args).flatMap(x -> x.isArray() ? Arrays.stream(x.asArray()) : Arrays.stream(new LuaObject[]{x})).toArray(LuaObject[]::new);
            if (allArgs.length < externalTarget.getArgCount() - (externalTarget.hasParamsArg() ? 1 : 0)) {
                var prevAllArgs = allArgs;
                allArgs = new LuaObject[externalTarget.getArgCount() - (externalTarget.hasParamsArg() ? 1 : 0)];
                Arrays.fill(allArgs, LuaObject.nil());
                System.arraycopy(prevAllArgs, 0, allArgs, 0, prevAllArgs.length);
            }

            var expectedNuStack = new LuaObject[nuStackFrame.length];
            System.arraycopy(allArgs, 0, expectedNuStack, 0, externalTarget.getArgCount() - (externalTarget.hasParamsArg() ? 1 : 0));
            for (int i = 0; i < expectedNuStack.length; i++) {
                if (externalTarget.hasParamsArg() && i == externalTarget.getArgCount() - 1)
                    continue; // skip param arg
                assert expectedNuStack[i] == nuStackFrame[i];
            }
            if (externalTarget.hasParamsArg()) {
                var expectedParamsArray = Arrays.stream(allArgs).skip(externalTarget.getArgCount() - 1).toArray(LuaObject[]::new);
                var actualParamsArray = nuStackFrame[externalTarget.getArgCount() - 1].asArray();
                assert expectedParamsArray.length == actualParamsArray.length;
                for (int j = 0; j < expectedParamsArray.length; j++) {
                    assert expectedParamsArray[j] == actualParamsArray[j];
                }
            }
            return true;
        }).get();
    }

    private void setupCall(LuaFunction externalTarget, LuaObject... args) {
        if (luaCallStack.size() >= MAX_LUA_STACK_SIZE) {
            currentCoroutine.rootFail = true;
            currentCoroutine.rootReturned = new LuaObject[]{LuaObject.of("stack overflow error")};
            luaCallStack.clear();
            curFuncFrame = null;
        }
        // setup new stack frame for call
        LuaObject[] nuStackFrame = new LuaObject[externalTarget.getMaxLocalsSize()];
        packArgsInto(nuStackFrame, externalTarget, args);
        curFuncFrame = luaCallStack.push(new FunctionCallFrame(nuStackFrame, externalTarget));
    }

    public void callExternal(int resume, LuaFunction externalTarget) {
        callExternal(resume, externalTarget, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void callExternal(int resume, LuaFunction externalTarget, LuaObject... args) {
        // set resume point for current function
        curFuncFrame.getTopFrame().resume = resume;
        // setup new stack frame for call
        setupCall(externalTarget, args);
    }

    public void tailCall(LuaFunction externalTarget) {
        tailCall(externalTarget, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void tailCall(LuaFunction externalTarget, LuaObject... args) {
        if (curFuncFrame.lFunc != externalTarget) {
            // this is sadly not a tail call
            curFuncFrame.isResumable = false;
            setupCall(externalTarget, args);
            return;
        }
        // do tailcall
        curFuncFrame.reset();
        LuaObject[] nuStackFrame = curFuncFrame.locals;
        for (int i = 0, j = 0; i < externalTarget.getArgCount(); j++) {
            if (args[j].isArray()) {
                LuaObject[] inner = args[j].asArray();
                for (int k = 0; k < inner.length && i < externalTarget.getArgCount(); i++, k++) {
                    nuStackFrame[i] = inner[k];
                }
            } else {
                nuStackFrame[i] = args[j];
                i++;
            }
        }
    }

    public void returnValue() {
        returnValue(Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void returnValue(LuaObject... values) {
        assert Arrays.stream(values).noneMatch(Objects::isNull) : "null in return vals";
        // function exit
        do {
            luaCallStack.pop();
        } while (!luaCallStack.isEmpty() && !luaCallStack.peek().isResumable);
        if (luaCallStack.isEmpty()) {
            // root function of coroutine returned
            currentCoroutine.rootReturned = values;
            currentCoroutine.state = Coroutine.State.DEAD;
            if (currentCoroutine.yieldTo != null) {
                // there is a resuming coroutine
                Coroutine stoppingCo = currentCoroutine;
                setCoroutine(currentCoroutine.yieldTo);
            } else {
                assert currentCoroutine == rootCoroutine : "non-root coroutine without resuming coroutine";
                curFuncFrame = null;
            }
        } else {
            curFuncFrame = luaCallStack.peek();
            curFuncFrame.getTopFrame().rvals = values;
        }
    }

    public void callInternal(int resume, LFunc localTarget, String targetName) {
        callInternal(resume, localTarget, targetName, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void callInternal(int resume, LFunc localTarget, String targetName, LuaObject... args) {
        // we trust that the caller knows what they are doing and that the args are already in the correct format
        curFuncFrame.getTopFrame().resume = resume;
        curFuncFrame.enterScope(localTarget, targetName, args);
    }

    public void internalReturn() {
        internalReturn(Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void internalReturn(LuaObject... values) {
        assert Arrays.stream(values).noneMatch(Objects::isNull) : "null in internal return vals";
        curFuncFrame.exitScope(values);
    }

    public void internalContinue() {
        curFuncFrame.getTopFrame().reset();
    }

    public void internalBreak(int scopeCnt) {
        for (int i = 0; i < scopeCnt; i++) {
            curFuncFrame.exitScope(Singletons.EMPTY_LUA_OBJ_ARRAY);
        }
    }

    public void internalGoto(int scopeCnt, int resume) {
        for (int i = 0; i < scopeCnt; i++) {
            curFuncFrame.exitScope(Singletons.EMPTY_LUA_OBJ_ARRAY);
        }
        curFuncFrame.getTopFrame().resume = resume;
    }
}
