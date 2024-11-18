package dev.asdf00.jluavm.exceptions.loading;

public class InternalJavaCompilerError extends InternalLuaLoadingError {
    public final String javaCode;

    public InternalJavaCompilerError(String msg, String javaCode) {
        super(msg);
        this.javaCode = javaCode;
    }
}
