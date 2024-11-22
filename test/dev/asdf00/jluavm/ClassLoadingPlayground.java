package dev.asdf00.jluavm;

import org.joor.Reflect;
import org.joor.ReflectException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClassLoadingPlayground {
    private static final String VERY_SIMPLE_CLASS = """
            package dev.asdf00.jluavm.lualoaded;
                import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
                import dev.asdf00.jluavm.exceptions.LuaRuntimeError;
                import dev.asdf00.jluavm.internals.LuaVM_RT;
                import dev.asdf00.jluavm.runtime.errors.*;
                import dev.asdf00.jluavm.runtime.types.*;
                import dev.asdf00.jluavm.runtime.utils.*;
                import java.lang.reflect.Constructor;
                public final class %s extends LuaFunction {
                    public static Constructor<? extends LuaFunction>[] innerFunctions;
                    public %s(LuaObject[] _ENV, LuaObject[] closures) {
                       super(_ENV, closures);
                    }
                    @Override
                    public int getMaxLocalsSize() {
                        return 0;
                    }
                    @Override
                    public int getArgCount() {
                        return 0;
                    }
                    @Override
                    public boolean hasParamsArg() {
                        return false;
                    }
                    @Override
                    public void invoke(LuaVM_RT vm, LuaObject[] stackFrame, int resume, LuaObject[] expressionStack, LuaObject[] returned) {
                        System.out.println("called %s#invoke");
                    }
                }""";

    private static Class<?> compileSimpleClass(String className) throws ReflectException {
        return Reflect.compile("dev.asdf00.jluavm.lualoaded." + className, VERY_SIMPLE_CLASS.formatted(className, className, className)).type();
    }

    @Test
    public void loadClass() {
        Class<?> clazz0 = compileSimpleClass("MyClass2");
        Class<?> clazz1 = compileSimpleClass("MyClass1");
        assertNotEquals(clazz0.getClassLoader(), clazz1.getClassLoader());
    }

    @Test
    public void javacFailure() {
        ReflectException ex = null;
        try {
            Reflect.compile("dev.asdf00.jluavm.lualoaded.Fail0", "00asdf").type();
        } catch (ReflectException e) {
            ex = e;
        }
        assertNotNull(ex);
    }
}
