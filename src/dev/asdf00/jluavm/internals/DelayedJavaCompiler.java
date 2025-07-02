package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.exceptions.DelayedJavaCompilationException;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class offers support for compiling and loading Java code generated at runtime.
 * <br/><br/>
 * This class is largely taken from the Java Object Oriented Reflection library (jOOR) which itself is distributed under the Apache
 * License Version 2.0 with the source code available at <a href="https://github.com/jOOQ/jOOR">jOOR on Github</a>.
 */
public class DelayedJavaCompiler {
    public static Class<?> compileAndLoad(ClassLoader parentLdr, String className, String content) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new DelayedJavaCompilationException("No compiler was provided by ToolProvider.getSystemJavaCompiler(). Make sure the jdk.compiler module is available.");
        try {
            List<String> options = new ArrayList<>();
            StringBuilder classpath = new StringBuilder();
            String cp = System.getProperty("java.class.path");
            String mp = System.getProperty("jdk.module.path");
            if (cp != null && !"".equals(cp)) {
                classpath.append(cp);
            }
            if (mp != null && !"".equals(mp)) {
                classpath.append(mp);
            }
            options.add("-classpath");
            options.add(classpath.toString());

            ClassFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
            StringWriter out = new StringWriter();
            JavaCompiler.CompilationTask task = compiler.getTask(out, fileManager, null, options, null, List.of(new CharSequenceJavaFileObject(className, content)));
            task.call();

            if (fileManager.isEmpty()) {
                throw new DelayedJavaCompilationException("JIC Compilation error: " + out);
            }

            ByteArrayClassLoader c = new ByteArrayClassLoader(parentLdr, fileManager.classes());
            return fileManager.loadAndReturnMainClass(className, c);
        } catch (DelayedJavaCompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new DelayedJavaCompilationException("Error while compiling " + className, e);
        }
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private final LinkedHashMap<String, byte[]> classes;

        public ByteArrayClassLoader(ClassLoader parentLdr, LinkedHashMap<String, byte[]> classes) {
            super(parentLdr);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            return bytes == null ? super.findClass(name) : defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static final class JavaFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream os = new ByteArrayOutputStream();

        public JavaFileObject(String name, JavaFileObject.Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
        }

        public byte[] getBytes() {
            return os.toByteArray();
        }

        @Override
        public OutputStream openOutputStream() {
            return os;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return new String(os.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final LinkedHashMap<String, JavaFileObject> fileObjectMap;
        private LinkedHashMap<String, byte[]> classes;

        public ClassFileManager(StandardJavaFileManager standardManager) {
            super(standardManager);
            fileObjectMap = new LinkedHashMap<>();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            JavaFileObject result = new JavaFileObject(className, kind);
            fileObjectMap.put(className, result);
            return result;
        }

        public boolean isEmpty() {
            return fileObjectMap.isEmpty();
        }

        public LinkedHashMap<String, byte[]> classes() {
            if (classes == null) {
                classes = new LinkedHashMap<>();
                for (Map.Entry<String, JavaFileObject> entry : fileObjectMap.entrySet()) {
                    classes.put(entry.getKey(), entry.getValue().getBytes());
                }
            }
            return classes;
        }

        public Class<?> loadAndReturnMainClass(String mainClassName, ClassLoader ldr) throws Exception {
            Class<?> result = null;
            for (var clName : fileObjectMap.keySet()) {
                Class<?> c = ldr.loadClass(clName);
                if (mainClassName.equals(clName)) {
                    result = c;
                }
            }
            return result;
        }
    }

    private static final class CharSequenceJavaFileObject extends SimpleJavaFileObject {
        final CharSequence content;

        public CharSequenceJavaFileObject(String className, CharSequence content) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }
}
