package dev.asdf00.jluavm.exceptions;

import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;

public class DelayedJavaCompilationException extends InternalLuaLoadingError {
    public DelayedJavaCompilationException(String msg) {
        super(msg);
    }

    public DelayedJavaCompilationException(String msg, Throwable e) {
        super(msg, e);
    }
}
