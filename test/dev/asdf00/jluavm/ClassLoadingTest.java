package dev.asdf00.jluavm;

import dev.asdf00.jluavm.exceptions.DelayedJavaCompilationException;
import dev.asdf00.jluavm.internals.javac.DelayedJavaCompiler;
import dev.asdf00.jluavm.internals.javac.LuaFunctionClassLoader;
import dev.asdf00.jluavm.runtime.types.LuaFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClassLoadingTest {
    private static final String VERY_SIMPLE_CLASS = """
            package dev.asdf00.jluavm.lualoaded;
            import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
            import dev.asdf00.jluavm.exceptions.LuaRuntimeError;
            import dev.asdf00.jluavm.internals.LuaVM_RT;
            import dev.asdf00.jluavm.runtime.types.*;
            import dev.asdf00.jluavm.runtime.utils.*;
            import java.lang.reflect.Constructor;
            public final class %s extends AbstractGeneratedLuaFunction {
                public static Constructor<? extends AbstractGeneratedLuaFunction>[] innerFunctions;
                public %s(LuaObject _ENV, LuaObject[] closures) {
                   super("classloadingtest.lua", -1, _ENV, closures);
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

    private static Class<?> compileSimpleClass(String className) {
        return DelayedJavaCompiler.compileAndLoad(new LuaFunctionClassLoader(LuaFunction.class.getClassLoader()),
                "dev.asdf00.jluavm.lualoaded." + className, VERY_SIMPLE_CLASS.formatted(className, className, className));
    }

    @Test
    public void loadClass() {
        Class<?> clazz0 = compileSimpleClass("MyClass2");
        Class<?> clazz1 = compileSimpleClass("MyClass1");
        assertNotEquals(clazz0.getClassLoader(), clazz1.getClassLoader());
    }

    @Test
    public void javacFailure() {
        assertThrows(DelayedJavaCompilationException.class, () -> DelayedJavaCompiler.compileAndLoad(new LuaFunctionClassLoader(LuaFunction.class.getClassLoader()),
                "dev.asdf00.jluavm.lualoaded.Fail0", "00asdf"));
    }

//    @Test
//    public void dumpStuff() {
//        LuaVM.builder().emptyEnv().rootFunc("").build().dumpJICFor("""
//                        if true then local a1 end
//                        local f -- crashes here
//                        f = function() return f end
//                        """,
//                Path.of("test"));
//    }
//
//    @Test
//    @SuppressWarnings("unchecked")
//    public void debugDumpedStuff() throws Exception {
//        var vm = LuaVM.builder().rootFunc("").build();
//        vm.withDumpedRoot(Files.readString(Path.of("test/dev/asdf00/jluavm/lualoaded/depts.txt")), GeneratedLuaFunc_1.class, GeneratedLuaFunc_2.class);
//        var cres = vm.run();
//    }
}
