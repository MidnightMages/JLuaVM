package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.runtime.errors.AbstractLuaError;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.runtime.utils.LFunc;
import dev.asdf00.jluavm.runtime.utils.Singletons;

import java.util.Stack;

public class LuaVM_RT extends LuaVM {

    public LuaVM_RT() {

    }

    // =================================================================================================================
    // main runtime methods
    // =================================================================================================================

    public LuaFunction rootFunction;
    public Stack<LuaStackFrame> luaStack;
    private LuaStackFrame curFrame;

    private enum MagicState {
        ERROR,
        CALL_EXTERNAL,
        TAIL_CALL,
        CALL_INTERNAL,
        RETURNING,
        INTERNAL_RETURNING,
        GOTO,
        BREAK,
    }
    // magic state
    private MagicState callState;
    private LuaFunction nextExternalCall;
    private LFunc nextInternalCall;
    private LuaObject[] arguments;
    private LuaObject[] returnVals;
    private AbstractLuaError currentError;

    private void runInternal() {
        for (;;) {
            switch (callState) {
                case ERROR -> {
                    // TODO
                }
                case CALL_EXTERNAL -> {
                    // create new locals
                    var locals = new LuaObject[nextExternalCall.getMaxLocalsSize()];
                    // setup args
                    setupArgsInLocals(locals);
                    // create new LuaStackFrame
                    curFrame = luaStack.push(new LuaStackFrame(locals, nextExternalCall));
                    // call lua function
                    nextExternalCall.invoke(this, curFrame.locals, -1, null, null);
                }
                case TAIL_CALL -> {
                    if (nextExternalCall != curFrame.lFunction) {
                        // no tail call possible but this function is not resumable beyond this point and just passes
                        // through the return values of the called function
                        curFrame.resumable = false;
                        // create new locals
                        var locals = new LuaObject[nextExternalCall.getMaxLocalsSize()];
                        // setup args
                        setupArgsInLocals(locals);
                        // create new LuaStackFrame
                        curFrame = luaStack.push(new LuaStackFrame(locals, nextExternalCall));
                        // call lua function
                        nextExternalCall.invoke(this, curFrame.locals, -1, null, null);
                    } else {
                        // tail callable, prepare stack frame
                        curFrame.clear();
                        // setup args
                        setupArgsInLocals(curFrame.locals);
                        // call lua function
                        nextExternalCall.invoke(this, curFrame.locals, -1, null, null);
                    }
                }
                case CALL_INTERNAL -> {
                    curFrame.pushJFrame(nextInternalCall);
                    nextInternalCall.invoke(this, curFrame.locals, arguments, -1, null, null);
                }
                case RETURNING -> {
                    luaStack.pop();
                    if (luaStack.isEmpty()) {
                        // outermost lua function has exited
                        return;
                    }
                    curFrame = luaStack.peek();
                    if (curFrame.internalCallStack.isEmpty()) {
                        // resume lua function
                        curFrame.lFunction.invoke(this, curFrame.locals, curFrame.resume, curFrame.eStack, returnVals);
                    } else {
                        // resume internal java function
                        LuaStackFrame.JStackFrame jFrame = curFrame.internalCallStack.peek();
                        jFrame.jMethod.invoke(this, curFrame.locals, null, jFrame.resume, jFrame.eStack, returnVals);
                    }
                }
                case INTERNAL_RETURNING -> {
                    curFrame.internalCallStack.pop();
                    if (curFrame.internalCallStack.isEmpty()) {
                        // resume lua function
                        curFrame.lFunction.invoke(this, curFrame.locals, curFrame.resume, curFrame.eStack, returnVals);
                    } else {
                        // resume internal java function
                        LuaStackFrame.JStackFrame jFrame = curFrame.internalCallStack.peek();
                        jFrame.jMethod.invoke(this, curFrame.locals, null, jFrame.resume, jFrame.eStack, returnVals);
                    }
                }
            }
        }
    }

    private void setupArgsInLocals(LuaObject[] locals) {
        int argCnt = nextExternalCall.getArgCount();
        for (int i = 0; i < argCnt; i++) {
            if (i >= arguments.length) {
                // append nil
                locals[i] = LuaObject.nil();
                continue;
            }
            locals[i] = arguments[i];
        }
        if (nextExternalCall.hasParamsArg()) {
            // pack all remaining arguments into params
            var packedParams = new LuaObject[Math.max(0, arguments.length - argCnt)];
            for (int i = argCnt; i < arguments.length; i++) {
                packedParams[i - argCnt] = arguments[i];
            }
            locals[argCnt] = LuaObject.of(packedParams);
        }
    }

    // =================================================================================================================
    // scope setup methods
    // =================================================================================================================

    public LuaObject[] registerExpressionStack(int size) {
        var neStack = size == 0 ? Singletons.EMPTY_LUA_OBJ_ARRAY : new LuaObject[size];
        if (curFrame.internalCallStack.isEmpty()) {
            // lua function setup
            curFrame.eStack = neStack;
        } else {
            curFrame.internalCallStack.peek().eStack = neStack;
        }
        return neStack;
    }

    public void addClosable(LuaObject obj) {
        curFrame.addClosable(obj);
    }

    public LuaObject getNextClosable() {
        return curFrame.getNextClosable();
    }

    // =================================================================================================================
    // lua vm call magic setup methods
    // =================================================================================================================

    public void error(AbstractLuaError err) {
        // TODO: set error
    }

    public void callExternal(int resume, LuaFunction externalTarget, LuaObject... args) {
        // TODO: flatten LuaArray into args array
    }

    public void tailCall(LuaFunction externalTarget, LuaObject... args) {
        // TODO: even if a tailcall is not possible, this function does not expect to be resumed but just to pass
        //  through the returned values of the inner function.
    }

    public void callInternal(int resume, LFunc localTarget, LuaObject... args) {

    }

    public void internalReturn() {
        internalReturn(Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void internalReturn(LuaObject... values) {

    }

    public void returnValue() {
        returnValue(Singletons.EMPTY_LUA_OBJ_ARRAY);
    }

    public void returnValue(LuaObject... values) {

    }
}
