package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.runtime.errors.AbstractLuaError;
import dev.asdf00.jluavm.runtime.errors.LuaTypeError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Arrays;
import java.util.Random;
import java.util.Stack;
import java.util.function.Supplier;

@SuppressWarnings("unsused")
public class LuaVM_RT extends LuaVM {

    public LuaVM_RT() {
        luaCallStack = new Stack<>();
        curFuncFrame = null;
        returnVals = null;
    }

    @Override
    public VmResult run() {
        if (rootFunc == null) {
            return new VmResult(VmRunState.EXECUTION_ERROR, new LuaObject[]{LuaObject.of("Invalid root function")});
        }
        curFuncFrame = luaCallStack.push(new FunctionCallFrame(new LuaObject[rootFunc.getMaxLocalsSize()], rootFunc));
        execLoop();
        return new VmResult(VmRunState.SUCCESS, returnVals);
    }

    // =================================================================================================================
    // main runtime methods
    // =================================================================================================================

    // magic state
    public Random lMathRandom = new Random();

    Stack<FunctionCallFrame> luaCallStack;
    FunctionCallFrame curFuncFrame;
    private LuaObject[] returnVals;

    private void execLoop() {
        for (; ; ) {
            if (curFuncFrame != null) {
                curFuncFrame.getTopFrame().execute(this);
            } else {
                break;
            }
        }
    }

    // =================================================================================================================
    // scope setup methods
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

    public void addClosable(LuaObject obj) {
        curFuncFrame.getTopFrame().closables.push(obj);
    }

    public LuaObject getNextClosable() {
        return curFuncFrame.getTopFrame().closables.pop();
    }

    // =================================================================================================================
    // lua vm call magic setup methods (MUST be followed by return, and return must be preceded by exactly one of these, or throw internal lua error)
    // =================================================================================================================

    public void error(AbstractLuaError err) {
        throw new UnsupportedOperationException("errors not supported yet");
    }

    public void errorArgType(int argumentIndex, String expectedType, LuaObject actualObject) {
        error(new LuaTypeError("Expected argument #%s to be of type '%s', but it was of type '%s'!".formatted(argumentIndex + 1, expectedType, actualObject.getTypeAsString())));
    }

    private void setupCall(LuaFunction externalTarget, LuaObject... args) {
        // setup new stack frame for call
        LuaObject[] nuStackFrame = new LuaObject[externalTarget.getMaxLocalsSize()];
        int srcArgIdx = 0;
        int srcArgElemIdx = 0;
        int dstIdx = 0;
        var vaDest = externalTarget.hasParamsArg();
        var targetArgCnt = externalTarget.getArgCount();
        LuaObject[] VADestArray = null;

        while (dstIdx < externalTarget.getArgCount()) {
            var isLastDest = dstIdx == targetArgCnt - 1;
            if (isLastDest && vaDest) {
                if (srcArgIdx >= args.length) {
                    nuStackFrame[0] = LuaObject.of(Singletons.EMPTY_LUA_OBJ_ARRAY);
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
                var srcObj = args[srcArgIdx++];
                if (srcObj.isArray()) { // flatten
                    var srcObjArr = srcObj.asArray();
                    if (srcArgElemIdx >= srcObjArr.length) { // src array is empty
                        srcArgElemIdx = 0;
                        srcArgIdx++;
                        continue;
                    }
                    nuStackFrame[dstIdx++] = srcObjArr[srcArgElemIdx++];
                } else {
                    nuStackFrame[dstIdx++] = srcObj;
                }
            } else {
                nuStackFrame[dstIdx++] = LuaObject.NIL;
            }
        }

        assert ((Supplier<Boolean>) () -> {
            var allArgs = Arrays.stream(args).flatMap(x -> x.isArray() ? Arrays.stream(x.asArray()) : Arrays.stream(new LuaObject[]{x})).toArray(LuaObject[]::new);

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
            // this is sadly not a tail call, replace current stack frame with new call,
            // thereby forwarding the return value
            luaCallStack.pop();
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
        assert Arrays.stream(values).filter(v -> v == null).count() == 0 : "null in return vals";
        // function exit
        luaCallStack.pop();
        if (luaCallStack.isEmpty()) {
            // root function returned
            returnVals = values;
            curFuncFrame = null;
        } else {
            curFuncFrame = luaCallStack.peek();
            curFuncFrame.getTopFrame().rvals = values;
        }
    }

    public void callInternal(int resume, LFunc localTarget) {
        callInternal(resume, localTarget, Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void callInternal(int resume, LFunc localTarget, LuaObject... args) {
        // we trust that the caller knows what they are doing and that the args are already in the correct format
        curFuncFrame.getTopFrame().resume = resume;
        curFuncFrame.enterScope(localTarget, args);
    }

    public void internalReturn() {
        internalReturn(Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void internalReturn(LuaObject... values) {
        assert Arrays.stream(values).filter(v -> v == null).count() == 0 : "null in internal return vals";
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
