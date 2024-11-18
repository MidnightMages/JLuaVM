package dev.asdf00.jluavm.exceptions.loading;

public class InternalLuaLoadingError extends RuntimeException {
    public InternalLuaLoadingError(String msg) {
        super(msg);
    }

    public InternalLuaLoadingError(Throwable e) {
        super(e);
    }
}
