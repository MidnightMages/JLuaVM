package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.LuaVM;
import dev.asdf00.jluavm.exceptions.LuaRuntimeError$;
import dev.asdf00.jluavm.exceptions.runtime.LuaMetaTableError$;
import dev.asdf00.jluavm.exceptions.runtime.LuaTypeError$;
import dev.asdf00.jluavm.types.LuaNil$;
import dev.asdf00.jluavm.types.LuaTable$;
import dev.asdf00.jluavm.types.LuaVariable$;

import java.util.Stack;

public class LuaVM_RT$ extends LuaVM {
    /**
     * Every (x)pcall pushes a new substack. An assignment of a closable variable pushes to the current substack,
     * every closing action pops from the substack
     */
    public final Stack<Stack<LuaVariable$>> closeOnErrorStackStack;

    public LuaVM_RT$() {
        closeOnErrorStackStack = new Stack<>();
        closeOnErrorStackStack.push(new Stack<>());
    }

    /**
     * This method is only supposed to be called directly via {@link LuaVM_RT$#yeet(LuaRuntimeError$)},
     * all other calls should come from {@link LuaVM_RT$#close(LuaVariable$)}.
     */
    protected void close(LuaVariable$ value, LuaRuntimeError$ error) {
        if (!value.isTable()) {
            yeet(new LuaTypeError$("%s is not a closable type".formatted(value.getType())));
        }
        var tbl = (LuaTable$) value;
        var mtf = tbl._luaGetMtFunc("__close");
        if (mtf == null) {
            yeet(new LuaMetaTableError$("metamethod '__close' not found"));
        }
        mtf.invoke(this, value, error == null ? LuaNil$.singleton : error.getErrorString());
    }

    /**
     * This method is supposed to be called ONCE for each variable needed to be closed normally.
     */
    public void close(LuaVariable$ value) {
        close(value, null);
    }

    /**
     * This method is supposed to be called ONCE on assigning a closable variable.
     */
    public void checkClosability(LuaVariable$ value) {
        if (!value.isTable()) {
            yeet(new LuaTypeError$("%s is not a table type".formatted(value.getType())));
        }
        var tbl = (LuaTable$) value;
        var mtf = tbl._luaGetMtFunc("__close");
        if (mtf == null) {
            yeet(new LuaMetaTableError$("metamethod '__close' not found"));
        }
        closeOnErrorStackStack.peek().push(value);
    }

    /**
     * Exceptions should only be thrown via a LuaVM_RT$#yeet method to ensure closable variables are closed properly.
     * This method is also available via static access (see {@link LuaVM_RT$#yeet(LuaVM_RT$, LuaRuntimeError$)})
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
     * This method is also available via dynamic access (see {@link LuaVM_RT$#yeet(LuaRuntimeError$)})
     */
    public static RuntimeException yeet(LuaVM_RT$ vmHandle, LuaRuntimeError$ error) {
        return vmHandle.yeet(error);
    }

    protected static void close(LuaVM_RT$ vmHandle, LuaVariable$ value, LuaRuntimeError$ error) {
        vmHandle.close(value, error);
    }
}
