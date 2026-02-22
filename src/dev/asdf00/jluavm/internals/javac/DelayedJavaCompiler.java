package dev.asdf00.jluavm.internals.javac;

import dev.asdf00.jluavm.exceptions.DelayedJavaCompilationException;
import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * This class offers support for compiling and loading Java code generated at runtime.
 * <br/><br/>
 * This class is largely taken from the Java Object Oriented Reflection library (jOOR) which itself is distributed under the Apache
 * License Version 2.0 with the source code available at <a href="https://github.com/jOOQ/jOOR">jOOR on Github</a>.
 */
public class DelayedJavaCompiler {

    private static final ExecutorService compilationPool = new ThreadPoolExecutor(1, 4,
            2, TimeUnit.MINUTES, new LinkedBlockingQueue<>());

    public static class AsyncCompilationResult {
        private final Supplier<JavaCompiler.CompilationTask> task;
        private final String outputClassName;
        private final ClassFileManager fileManager;
        private final StringWriter compilationOutput;
        private final String javaSourceCode;

        private AsyncCompilationResult(Supplier<JavaCompiler.CompilationTask> task, String outputClassName, ClassFileManager fileManager, StringWriter compilationOutput, String javaSourceCode) {
            this.task = task;
            this.outputClassName = outputClassName;
            this.fileManager = fileManager;
            this.compilationOutput = compilationOutput;
            this.javaSourceCode = javaSourceCode;
        }
    }

    public static AsyncCompilationResult compileAndLoadAsync(String className, String javaSourceCode) throws DelayedJavaCompilationException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new DelayedJavaCompilationException("No compiler was provided by ToolProvider.getSystemJavaCompiler(). Make sure the jdk.compiler module is available.");

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

        // this is needed for compilation to work in some production environments  as otherwise some already
        // compiled classes will not be able to be referenced when compiling java code that we emit during lua compilation
        var virtualJarPath = DelayedJavaCompiler.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            URI virtualJarUri = virtualJarPath.toURI();

            // scheme will be 'union' in some cases, so if it is an actual disk file path,
            // we add it to the classpath to support running it in such environments.
            if (virtualJarUri.getScheme().equals("union")) {
                var jarDiskPath = virtualJarUri
                        .getPath()
                        .replaceAll("#\\d+!/$", "")  // remove the trailing '#NUMBER!/'
                        .trim();

                // trim leading / on windows, e.g. /C:/something/something.jar
                Path normalizedJarDiskPath;
                try {
                    normalizedJarDiskPath = Paths.get(jarDiskPath);
                } catch (InvalidPathException ignored) {
                    normalizedJarDiskPath = Paths.get(jarDiskPath.substring(1));
                }

                // if it is a .jar path, add it to the classpath
                String normalizedJarDiskPathString = normalizedJarDiskPath.toString().replace('\\', '/');
                if (normalizedJarDiskPathString.endsWith(".jar")) {
                    classpath.append(";").append(normalizedJarDiskPathString);
                }
            }
        } catch (URISyntaxException e) {
            throw new InternalLuaLoadingError(e);
        }

        options.add("-classpath");
        options.add(classpath.toString());

        ClassFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
        StringWriter out = new StringWriter();
        Supplier<JavaCompiler.CompilationTask> taskProvider = () ->
                compiler.getTask(out, fileManager, null, options, null, List.of(new CharSequenceJavaFileObject(className, javaSourceCode)));
        return new AsyncCompilationResult(taskProvider, className, fileManager, out, javaSourceCode);
    }

    public static Class<?> finishCompilation(AsyncCompilationResult res, ByteArrayClassLoader target) throws DelayedJavaCompilationException {
        var fileManager = res.fileManager;
        var compilationOutput = res.compilationOutput;
        var className = res.outputClassName;

        try {
            var cacheResult = PersistentJavaCompilationCache.getFromCacheOrNull(res.javaSourceCode);
            if (cacheResult != null) {
                target.addClassData(className, cacheResult);
                return target.loadClass(className);
            } else {
                var task = res.task.get();
                task.call();
                if (fileManager.isEmpty()) {
                    throw new DelayedJavaCompilationException("JIC Compilation error: " + compilationOutput);
                }
                var compilationResult = fileManager.classes();
                target.addClassData(compilationResult);

                // check cache assumptions
                if (compilationResult.size() != 1)
                    throw new RuntimeException("Expected there to be exactly one source file, but there were %s.".formatted(compilationResult.size()));

                if (!compilationResult.keySet().iterator().next().equals(className))
                    throw new RuntimeException("Class name to be added to cache differed unexpectedly");

                // save to cache
                var valueToCache = compilationResult.get(className);
                PersistentJavaCompilationCache.addToCache(res.javaSourceCode, valueToCache);
            }
            return fileManager.loadAndReturnMainClass(className, target);
        } catch (DelayedJavaCompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new DelayedJavaCompilationException("Error while compiling " + className, e);
        }
    }

    public static class CompilationWorkItem {
        private final ByteArrayClassLoader target;
        private final String className;
        private final String content;

        public CompilationWorkItem(ByteArrayClassLoader target, String className, String content) {
            this.target = target;
            this.className = className;
            this.content = content;
        }
    }

    public static Class<?> compileAndLoad(ByteArrayClassLoader target, String className, String content) throws DelayedJavaCompilationException {
        var res = compileAndLoadAsync(className, content);
        return finishCompilation(res, target);
    }

    public static Class<?>[] compileAndLoad(CompilationWorkItem[] itemsToCompile) throws DelayedJavaCompilationException {
        var tasks = new AsyncCompilationResult[itemsToCompile.length];
        var largestSize = -1;
        var largestIndex = -1;
        for (int i = 0; i < itemsToCompile.length; i++) {
            var e = itemsToCompile[i];
            tasks[i] = compileAndLoadAsync(e.className, e.content);
            var currentSize = e.content.length();
            if (currentSize > largestSize) {
                largestSize = currentSize;
                largestIndex = i;
            }
        }

        final Object monitor = new Object();
        AtomicReference<Exception> lastException = new AtomicReference<>();
        AtomicInteger remainingTasks = new AtomicInteger(itemsToCompile.length - 1); // start at -1 because we will manually complete one task
        var queueResult = new Class<?>[itemsToCompile.length];
        for (int i = 0; i < tasks.length; i++) {
            if (i != largestIndex) {
                final int i_copy = i;
                compilationPool.submit(() -> {
                    Class<?> taskRes;
                    try {
                        taskRes = finishCompilation(tasks[i_copy], itemsToCompile[i_copy].target);
                        synchronized (queueResult) {
                            queueResult[i_copy] = taskRes;
                        }
                    } catch (Exception ex) {
                        lastException.set(ex);
                        synchronized (monitor) {
                            monitor.notifyAll();
                        }
                    }
                    if (remainingTasks.decrementAndGet() == 0) {
                        synchronized (monitor) {
                            monitor.notifyAll();
                        }
                    }

                });
            }
        }

        var syncRes = finishCompilation(tasks[largestIndex], itemsToCompile[largestIndex].target);
        synchronized (monitor) {
            try {
                while (remainingTasks.get() != 0) {
                    monitor.wait(500);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        var exceptionCopy = lastException.get();
        if (exceptionCopy != null)
            throw new RuntimeException(exceptionCopy);

        queueResult[largestIndex] = syncRes;

        return queueResult;
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
