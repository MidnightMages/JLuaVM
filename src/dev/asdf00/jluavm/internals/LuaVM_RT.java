package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.exceptions.LuaRuntimeError$;
import dev.asdf00.jluavm.exceptions.runtime.LuaMetaTableError$;
import dev.asdf00.jluavm.exceptions.runtime.LuaTypeError$;
import dev.asdf00.jluavm.runtime.errors.AbstractLuaError;
import dev.asdf00.jluavm.runtime.types.ILuaVariable;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import dev.asdf00.jluavm.runtime.typesOLD.LuaNilOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaTableOLD;
import dev.asdf00.jluavm.runtime.typesOLD.LuaVariableOLD;
import dev.asdf00.jluavm.runtime.utils.LFunc;

import java.util.Stack;

public class LuaVM_RT extends LuaVM {
    /**
     * Every (x)pcall pushes a new substack. An assignment of a closable variable pushes to the current substack,
     * every closing action pops from the substack
     */
    public final Stack<Stack<LuaVariableOLD>> closeOnErrorStackStack;

    public LuaVM_RT() {
        closeOnErrorStackStack = new Stack<>();
        closeOnErrorStackStack.push(new Stack<>());
    }




    public ILuaVariable[] registerExpressionStack(int size) {
        // TODO: save expression stack for current java call
        return new ILuaVariable[size];
    }

    // =================================================================================================================
    // lua vm call magic setup methods
    // =================================================================================================================

    public void error(AbstractLuaError err) {
        // TODO: set error
    }

    public void callExternal(int resume, LuaFunction externalTarget, ILuaVariable... args) {
        // TODO: flatten LuaArray into args array
    }

    public void tailCall(int resume, LuaFunction externalTarget, ILuaVariable... args) {
        boolean isTailcall = false;
        if (!isTailcall) {
            callExternal(resume, externalTarget, args);
            return;
        }
        // TODO: flatten LuaArray into args array
        // setup tail call
    }

    public void callInternal(int resume, LFunc localTarget, ILuaVariable... args) {

    }

    public void returnValue(ILuaVariable... values) {

    }









    /**
     * This method is only supposed to be called directly via {@link LuaVM_RT#yeet(LuaRuntimeError$)},
     * all other calls should come from {@link LuaVM_RT#close(LuaVariableOLD)}.
     */
    protected void close(LuaVariableOLD value, LuaRuntimeError$ error) {
        if (!value.isTable()) {
            yeet(new LuaTypeError$("%s is not a closable type".formatted(value.getType())));
        }
        var tbl = (LuaTableOLD) value;
        var mtf = tbl._luaGetMtFunc("__close");
        if (mtf == null) {
            yeet(new LuaMetaTableError$("metamethod '__close' not found"));
        }
        mtf.invoke(this, value, error == null ? LuaNilOLD.singleton : error.getErrorString());
    }

    /**
     * This method is supposed to be called ONCE for each variable needed to be closed normally.
     */
    public void close(LuaVariableOLD value) {
        close(value, null);
    }

    /**
     * This method is supposed to be called ONCE on assigning a closable variable.
     */
    public void checkClosability(LuaVariableOLD value) {
        if (!value.isTable()) {
            yeet(new LuaTypeError$("%s is not a table type".formatted(value.getType())));
        }
        var tbl = (LuaTableOLD) value;
        var mtf = tbl._luaGetMtFunc("__close");
        if (mtf == null) {
            yeet(new LuaMetaTableError$("metamethod '__close' not found"));
        }
        closeOnErrorStackStack.peek().push(value);
    }

    /**
     * Exceptions should only be thrown via a LuaVM_RT$#yeet method to ensure closable variables are closed properly.
     * This method is also available via static access (see {@link LuaVM_RT#yeet(LuaVM_RT, LuaRuntimeError$)})
     */
    public RuntimeException yeet(LuaRuntimeError$ error) {
        var curCloseList = closeOnErrorStackStack.peek();
        while (!curCloseList.isEmpty()) {
            close(curCloseList.pop(), error);
        }
        throw error;
    }

    // =================================================================================================================
    //         STATIC ACCESS        STATIC ACCESS        STATIC ACCESS        STATIC ACCESS        STATIC ACCESS
    // =================================================================================================================

    /**
     * Exceptions should only be thrown via a LuaVM_RT$#yeet method to ensure closable variables are closed properly.
     * This method is also available via dynamic access (see {@link LuaVM_RT#yeet(LuaRuntimeError$)})
     */
    public static RuntimeException yeet(LuaVM_RT vmHandle, LuaRuntimeError$ error) {
        return vmHandle.yeet(error);
    }

    protected static void close(LuaVM_RT vmHandle, LuaVariableOLD value, LuaRuntimeError$ error) {
        vmHandle.close(value, error);
    }
}
