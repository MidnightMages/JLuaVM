package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.runtime.errors.AbstractLuaError;
import dev.asdf00.jluavm.runtime.errors.LuaTypeError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Random;
import java.util.Stack;

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
        for (;;) {
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
        error(new LuaTypeError("Expected argument #%s to be of type '%s', but it was of type '%s'!".formatted(argumentIndex+1, expectedType, actualObject.getTypeAsString())));
    }

    private void setupCall(LuaFunction externalTarget, LuaObject... args) {
        // setup new stack frame for call
        LuaObject[] nuStackFrame = new LuaObject[externalTarget.getMaxLocalsSize()];
        int srcIdx = 0;
        int dstIdx = 0;
        var vaDest = externalTarget.hasParamsArg();
        var targetArgCnt = externalTarget.getArgCount();
        while (srcIdx < targetArgCnt) {
            var isLastDest = dstIdx == targetArgCnt-1;
            if (isLastDest && vaDest) { // dest is varargs, so just take all non-last-ones and also the last one which might be an array and combine and put them in there
                if (args.length == 0){
                    nuStackFrame[dstIdx] = LuaObject.of(Singletons.EMPTY_LUA_OBJ_ARRAY);
                    break;
                }

                var lastElem = args[args.length-1];
                var lastIsArray = lastElem.isArray();
                var lastArray = lastIsArray ? lastElem.asArray() : null;
                var VA = new LuaObject[args.length-srcIdx-1 + (lastIsArray ? lastArray.length : 1)];
                int VA_i = 0;
                for (int i = srcIdx; i < args.length-1; i++) { // skip last one as that might be a vararg (=multi) return
                    VA[VA_i++] = args[i];
                }
                if (!lastIsArray) {
                    VA[VA_i] = args[args.length-1];
                } else {
                    System.arraycopy(lastArray, 0, VA, VA_i, lastArray.length);
                }
                nuStackFrame[dstIdx] = LuaObject.of(VA);
                break;
            } else { // dest is definitely not varargs
                var s =  args[srcIdx++];
                nuStackFrame[dstIdx++] = s.isArray() ? s.asArray()[0] : s; // if func returned multiple at this pos, take the first one
            }
        }
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
