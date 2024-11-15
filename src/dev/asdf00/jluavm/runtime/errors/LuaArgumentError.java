package dev.asdf00.jluavm.runtime.errors;

public class LuaArgumentError extends AbstractLuaError {
    public String message;

    public LuaArgumentError(int argIndex, String funcName, String reason) {
        this.message = "bad argument #%s to '%s' (%s)".formatted(argIndex + 1, funcName, reason);
    }

    @Deprecated
    public LuaArgumentError() {
        message = "undefined";
    }
}
