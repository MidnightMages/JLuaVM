package dev.asdf00.jluavm.runtime.errors;

@SuppressWarnings("FieldCanBeLocal")
public class LuaUserError extends AbstractLuaError {
    private final String message;

    public LuaUserError(String message) {
        this.message = message;
    }
}
