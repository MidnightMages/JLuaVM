package dev.asdf00.jluavm.internals.javac;

public class LuaFunctionClassLoader extends ByteArrayClassLoader {
    public LuaFunctionClassLoader(ClassLoader parentLdr) {
        super(parentLdr);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // TODO: add some sandboxing to defend against potential exploits in code generation
        return super.findClass(name);
    }
}
