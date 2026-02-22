package dev.asdf00.jluavm.internals.javac;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ByteArrayClassLoader extends ClassLoader {
    protected final LinkedHashMap<String, byte[]> classes;

    public ByteArrayClassLoader(ClassLoader parentLdr) {
        super(parentLdr);
        this.classes = new LinkedHashMap<>();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        return bytes == null ? super.findClass(name) : defineClass(name, bytes, 0, bytes.length);
    }

    public void addClassData(Map<String, byte[]> classes) {
        this.classes.putAll(classes);
    }

    public void addClassData(String className, byte[] classData) {
        this.classes.put(className, classData);
    }
}
