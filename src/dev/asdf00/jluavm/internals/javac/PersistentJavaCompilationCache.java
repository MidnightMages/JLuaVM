package dev.asdf00.jluavm.internals.javac;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PersistentJavaCompilationCache {
    public static final String cacheFileExtension = ".jlvmCCache";
    public static final int filenameLength = 10; // length of cache filenames, without extension.
    private static Path compilationCacheFolder = null;
    public static int compilationCacheSize = 10; // amount of javasource->class mappings to keep
    private static final ConcurrentHashMap<String, byte[]> backing = new ConcurrentHashMap<>();

    public static boolean isCacheActive() {
        return compilationCacheFolder != null;
    }

    public static void deactivateCache() {
        compilationCacheFolder = null;
        backing.clear();
    }

    private static final Object enableCacheLockObj = new Object();

    public static void enableCache(Path compilationDirectory, int luaVmCache2MaxFiles) {
        synchronized (enableCacheLockObj) {
            if (isCacheActive())
                throw new RuntimeException("Cache has already been set up!");

            compilationCacheSize = luaVmCache2MaxFiles;

            // given path must be a directory
            if (!Files.isDirectory(compilationDirectory))
                throw new RuntimeException("Given cache path is not a directory");

            // make sure the directory only contains files, and that all files have the proper extension.
            // this is to avoid deleting important files later on.
            try (Stream<Path> stream = Files.walk(compilationDirectory)) {
                stream.forEach(path -> {
                    if (path.equals(compilationDirectory))
                        return;

                    if (!Files.isRegularFile(path)) {
                        throw new RuntimeException("Found non-file in cache folder.");
                    }

                    if (!path.toString().endsWith(cacheFileExtension)) {
                        throw new RuntimeException("Found file in cache folder with unexpected file extension: '%s'"
                                .formatted(path.getName(path.getNameCount() - 1)));
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            compilationCacheFolder = compilationDirectory;
            loadCache();
        }
    }

    private static MessageDigest getSha() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    private record CacheEntry(String key, byte[] value) {
        public static CacheEntry deserialize(byte[] bytes) {
            var buffer = ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
            var keyByteArray = new byte[buffer.getInt()];
            buffer.get(keyByteArray);
            var value = new byte[buffer.getInt()];
            buffer.get(value);
            if (buffer.hasRemaining()) {
                throw new RuntimeException("not all bytes were read!");
            }
            return new CacheEntry(new String(keyByteArray, StandardCharsets.UTF_8), value);
        }

        public byte[] serialize() {
            var keyBytes = key.getBytes(StandardCharsets.UTF_8);
            var rv = ByteBuffer
                    .allocate(4 + keyBytes.length + 4 + value.length)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(keyBytes.length)
                    .put(keyBytes)
                    .putInt(value.length)
                    .put(value).array();

            // TODO remove validation
            // validate cache
            if (!Arrays.equals(deserialize(rv).value, value)) {
                throw new RuntimeException("deserialization failed");
            }
            return rv;
        }
    }

    private static void loadCache() {
        try (Stream<Path> stream = Files.walk(compilationCacheFolder)) {
            stream.forEach(path -> {
                if (path.equals(compilationCacheFolder))
                    return;

                if (Files.isRegularFile(path) && path.toString().endsWith(cacheFileExtension)) {
                    try {
                        var cacheEntry = CacheEntry.deserialize(Files.readAllBytes(path));
                        backing.put(cacheEntry.key, cacheEntry.value);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensure(boolean ok) {
        if (!ok)
            throw new RuntimeException();
    }

//    private record CompiledRepresentation(byte[] sharedRawRepresentation, String luaSource) {
//        public static CompiledRepresentation FromBytecode(String javaSourceCode, byte[] compiledCode) {
//            var matcher = Pattern.compile("luaCode = \"\"\"(.*?)\"\"\"").matcher(javaSourceCode);
//            ensure(matcher.find());
//
//            var luaCode = matcher.group(1);
//            String placeholder = "$$$PLACEHOLDER1$$$";
//            ensure(!javaSourceCode.contains(placeholder));
//            ensure(javaSourceCode.contains(luaCode));
//            var luaFreeJavaCode = javaSourceCode.replace(luaCode, placeholder);
//            var luaCodeBytes = luaCode.getBytes(StandardCharsets.UTF_8);
//        }
//    }

    private static String normalizeSource(String javaSourceCode) {
        return Pattern.compile("luaCode = \"\"\".*?\"\"\"", Pattern.DOTALL).matcher(javaSourceCode).replaceAll("luaCode = \"\"\"REMOVED_BY_CACHE\"\"\"");
    }

    public static byte[] getFromCacheOrNull(String javaSourceCode) {
        return backing.get(normalizeSource(javaSourceCode));
    }

    public static void addToCache(String javaSourceCode, byte[] valueToCache) {
        var normSource = normalizeSource(javaSourceCode);
        backing.put(normSource, valueToCache);

        // do not store files >1MB, that way we can guarantee a maximum cache size via compilationCacheSize
        if (valueToCache.length < 1_000_000) {
            var serialized = new CacheEntry(normSource, valueToCache).serialize();
            var fileName = Base64.getEncoder()
                                   .encodeToString(getSha().digest(normSource.getBytes(StandardCharsets.UTF_8)))
                                   .substring(0, filenameLength)
                                   .replace('/', '_') + cacheFileExtension;

            var filePath = compilationCacheFolder.resolve(fileName);
            try {
                if (Files.exists(filePath))
                    Files.delete(filePath);

                Files.write(filePath, serialized);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        pruneCacheIfNeeded();
        // TODO remove oldest files if we are running out of cache space (i.e. are storing too many files already)
    }

    private static final AtomicBoolean alreadyPruning = new AtomicBoolean(false);

    private static void pruneCacheIfNeeded() {

        // check if some thread is already running this method; if so, skip execution
        // this way we do not block multiple threads for prune actions ever
        if (alreadyPruning.compareAndExchange(false, true)) return;
        try {
            // prune on-disk files
            ArrayList<Path> validCacheFiles = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(compilationCacheFolder)) {
                stream.forEach(path -> {
                    if (path.equals(compilationCacheFolder))
                        return;

                    if (Files.isRegularFile(path) && path.toString().endsWith(cacheFileExtension)) {
                        validCacheFiles.add(path);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // sort ASCENDING by age
            validCacheFiles.sort(Comparator.comparingLong(x -> {
                try {
                    return Files.getLastModifiedTime(x).toMillis();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));

            // so now just delete the oldest excess cache files
            var filesToDelete = validCacheFiles.size() - compilationCacheSize;
            if (filesToDelete > 0) {
                for (int i = 0; i < filesToDelete; i++) {
                    try {
                        Files.delete(validCacheFiles.get(i));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            // prune backing hashmap
            var hashmapItemsToRemove = backing.size() - compilationCacheSize;
            if (hashmapItemsToRemove > 0) {
                for (String backingKey : backing.keySet()) {
                    if (backing.remove(backingKey) != null) {
                        hashmapItemsToRemove--;
                        if (hashmapItemsToRemove <= 0)
                            break;
                    }
                }

                // if true, we somehow didnt find enough removable elements in the hashmap which should never happen unless we prune concurrently
                if (hashmapItemsToRemove > 0)
                    throw new RuntimeException("Somehow we were unable to prune the backing hashmap of cache2");
            }
        } finally {
            alreadyPruning.set(false);
        }
    }
}
