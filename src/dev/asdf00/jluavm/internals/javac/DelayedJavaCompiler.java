package dev.asdf00.jluavm.internals.javac;

import dev.asdf00.jluavm.exceptions.DelayedJavaCompilationException;
import dev.asdf00.jluavm.exceptions.loading.InternalLuaLoadingError;
import dev.asdf00.jluavm.runtime.utils.RTUtils;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * This class offers support for compiling and loading Java code generated at runtime.
 * <br/><br/>
 * This class is largely taken from the Java Object Oriented Reflection library (jOOR) which itself is distributed under the Apache
 * License Version 2.0 with the source code available at <a href="https://github.com/jOOQ/jOOR">jOOR on Github</a>.
 */
public class DelayedJavaCompiler {
    private static final LinkedHashSet<String> extraCompilationJarPaths = new LinkedHashSet<>(); // additioanl jars that are needed for compiling JLuaVM java code
    private static final LinkedHashSet<Class<?>> alreadyResolvedClasses = new LinkedHashSet<>();

    public static void includeContainingJarDuringCompilation(Class<?> clazz) {
        if (alreadyResolvedClasses.add(clazz)) {
            var uri = clazz.getProtectionDomain().getCodeSource().getLocation();
            extraCompilationJarPaths.add(new File(uri.getPath()).getName().split("\\.")[0]);
        }
    }

    private static final Object compilationSignaller = new Object();
    private static final Semaphore currentlyCompiling_sem = new Semaphore(1);
    private static final HashSet<String> currentlyCompiling = new HashSet<>(); // java source code

    public static Class<?> compileAndLoad(ByteArrayClassLoader target, String className, String content) throws DelayedJavaCompilationException {
        return compileAndLoad(target, new CompilationWorkItem[]{new CompilationWorkItem(className, content)})[0];
    }

    public static Class<?>[] compileAndLoad(ByteArrayClassLoader target, CompilationWorkItem[] itemsToCompile) throws DelayedJavaCompilationException {
        final Class<?>[] rv = new Class[itemsToCompile.length];
        final byte[][] alreadyCachedItems = new byte[itemsToCompile.length][]; // cache items we already managed to grab
        final ArrayList<Integer> itemsWeWillBeCompiling = new ArrayList<>(); // ids of things we need to compile; access value by indexing itemsToCompile[...]
        final ArrayList<Integer> itemIdsThatNeedToBeCompiledBeforeItsOurTurn = new ArrayList<>(); // things we need to wait for to finish
        try {
            currentlyCompiling_sem.acquire();
            // check what we need to compile and mark it as compiling
            for (int i = 0; i < itemsToCompile.length; i++) {
                var cacheItem = PersistentJavaCompilationCache.isCacheActive() ? PersistentJavaCompilationCache.getFromCacheOrNull(itemsToCompile[i].javaSourceCode) : null;
                if (cacheItem == null) { // need to compile
                    // only add to currentlyCompiling if the PersistentJavaCompilationCache is enabled
                    var alreadyCompiling = PersistentJavaCompilationCache.isCacheActive() && !currentlyCompiling.add(itemsToCompile[i].javaSourceCode);
                    if (!alreadyCompiling) { // we are compiling this one, so no need to wait
                        itemsWeWillBeCompiling.add(i);
                    } else { // someone else is compiling this, so we need to wait for that to finish before starting our compilation
                        itemIdsThatNeedToBeCompiledBeforeItsOurTurn.add(i);
                    }
                } else { // already compiled --> use cache
                    alreadyCachedItems[i] = cacheItem;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            currentlyCompiling_sem.release();
        }


        while (!itemIdsThatNeedToBeCompiledBeforeItsOurTurn.isEmpty()) {
            try {
                synchronized (compilationSignaller) {
                    compilationSignaller.wait(100);
                    try {
                        currentlyCompiling_sem.acquire();
                        for (int i = itemIdsThatNeedToBeCompiledBeforeItsOurTurn.size() - 1; i >= 0; i--) {
                            // if the compilation has finished, grab the result and make sure it exists
                            var j = itemIdsThatNeedToBeCompiledBeforeItsOurTurn.get(i);
                            var srcCode = itemsToCompile[j].javaSourceCode;
                            if (!currentlyCompiling.contains(srcCode)) {
                                itemIdsThatNeedToBeCompiledBeforeItsOurTurn.remove(i); // at this point compilation either failed and will fail again, or succeeded
                                var justNowCompiledItem = PersistentJavaCompilationCache.getFromCacheOrNull(srcCode);
                                if (justNowCompiledItem == null)
                                    throw new DelayedJavaCompilationException("We would try to attempt to compile something that already failed earlier and thus will fail again.");

                                alreadyCachedItems[j] = justNowCompiledItem;
                            }
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        currentlyCompiling_sem.release();
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // load cached classes
        if (PersistentJavaCompilationCache.isCacheActive())
            for (int i = 0; i < itemsToCompile.length; i++) {
                var bytes = alreadyCachedItems[i];
                if (bytes != null) {
                    String cName = itemsToCompile[i].className;
                    target.addClassData(cName, bytes);
                    assert rv[i] == null;
                    try {
                        rv[i] = target.loadClass(cName);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        // ==========================================================================================
        // PERFORM ACTUAL COMPILATION
        //
        if (!itemsWeWillBeCompiling.isEmpty()) { // if theres nothing to compile then we can skip this whole block and are done
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null)
                throw new DelayedJavaCompilationException("No compiler was provided by ToolProvider.getSystemJavaCompiler(). Make sure the jdk.compiler module is available.");

            String javaClasspath = System.getProperty("java.class.path");
//            String mp = System.getProperty("jdk.module.path");

            var newClasspathEntries = new ArrayList<String>();
            // this is needed for compilation to work as otherwise some already
            // compiled classes will not be able to be referenced when compiling java code that we emit during lua compilation
            var virtualJarPath = DelayedJavaCompiler.class.getProtectionDomain().getCodeSource().getLocation();
            try {
                URI virtualJarUri = virtualJarPath.toURI();

                var jarName = new File(virtualJarUri.getPath()).getName().split("\\.")[0];
                for (var entry : javaClasspath.split(";")) {
                    if (entry.contains(jarName) || extraCompilationJarPaths.stream().anyMatch(entry::contains)) {
                        newClasspathEntries.add(entry);
                    }
                }
                // scheme will be 'union' in some cases, so if it is an actual disk file path,
                // we add it to the classpath to support running it in such environments.
                if (virtualJarUri.getScheme().equals("union")) {
                    var jarDiskPath = virtualJarUri
                            .getPath()
                            .replaceAll("(#|%23)\\d+!/$", "")  // remove the trailing '#NUMBER!/'
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
                        newClasspathEntries.add(normalizedJarDiskPathString);
                    } else if (normalizedJarDiskPathString.endsWith("/resources/main")) { // needed for in-IDE runs
                        newClasspathEntries.add(normalizedJarDiskPathString.replace("resources/main", "classes/java/main"));
                    }
                }
            } catch (URISyntaxException e) {
                throw new InternalLuaLoadingError(e);
            }

            List<String> options = new ArrayList<>();
            options.add("-classpath");
            var compilationClasspath = String.join(";", newClasspathEntries);
            options.add(compilationClasspath);

            ClassFileManager fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null));
            StringWriter out = new StringWriter();
            Supplier<JavaCompiler.CompilationTask> taskProvider = () ->
                    compiler.getTask(out, fileManager, null, options, null,
                            itemsWeWillBeCompiling.stream().map(i -> itemsToCompile[i]).map(x -> new CharSequenceJavaFileObject(x.className, x.javaSourceCode)).toList());

            taskProvider.get().call();

            if (fileManager.isEmpty()) {
                throw new DelayedJavaCompilationException("JIC Compilation error: " + out);
            }

            // load compiled classes
            var compilationResult = fileManager.classes();
            target.addClassData(compilationResult); // is threadsafe

            try {  // tell other threads that we are done compiling stuff
                currentlyCompiling_sem.acquire();
                for (int nowCompiledItemId : itemsWeWillBeCompiling) {
                    var srcCode = itemsToCompile[nowCompiledItemId].javaSourceCode;

                    var className = itemsToCompile[nowCompiledItemId].className;
                    var classBytes = compilationResult.get(className);
                    if (classBytes == null)
                        throw new RuntimeException("a class we compiled somehow wasnt returned?");
                    assert rv[nowCompiledItemId] == null;
                    try {
                        rv[nowCompiledItemId] = fileManager.loadAndReturnMainClass(className, target);
                    } catch (ClassNotFoundException e) {
                        throw new DelayedJavaCompilationException("Error while compiling " + className, e);
                    }

                    if (PersistentJavaCompilationCache.isCacheActive()) {
                        PersistentJavaCompilationCache.addToCache(srcCode, classBytes);
                        var ok = currentlyCompiling.remove(srcCode);
                        assert ok;
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                currentlyCompiling_sem.release();
                synchronized (compilationSignaller) {
                    compilationSignaller.notifyAll();
                }
            }
        }

        if (RTUtils.indexOf(rv, null) != -1)
            throw new DelayedJavaCompilationException("At least one returned class entry was null??");
        return rv;
    }

    public static class CompilationWorkItem {
        public String className;
        public String javaSourceCode;

        public CompilationWorkItem(String className, String javaSourceCode) {
            this.className = className;
            this.javaSourceCode = javaSourceCode;
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
        public synchronized JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            JavaFileObject result = new JavaFileObject(className, kind);
            fileObjectMap.put(className, result);
            return result;
        }

        public synchronized boolean isEmpty() {
            return fileObjectMap.isEmpty();
        }

        public synchronized LinkedHashMap<String, byte[]> classes() {
            if (classes == null) {
                classes = new LinkedHashMap<>();
                for (Map.Entry<String, JavaFileObject> entry : fileObjectMap.entrySet()) {
                    classes.put(entry.getKey(), entry.getValue().getBytes());
                }
            }
            return classes;
        }

        public synchronized Class<?> loadAndReturnMainClass(String mainClassName, ClassLoader ldr) throws ClassNotFoundException {
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
