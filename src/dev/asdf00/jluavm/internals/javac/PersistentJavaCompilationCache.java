package dev.asdf00.jluavm.internals.javac;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

public class PersistentJavaCompilationCache {
    public static final String cacheFileExtension = ".jlvmCCache";
    public static final int compilationCacheSize = 1000; // amount of javasource->class mappings to keep
    public static final int filenameLength = 10; // length of cache filenames, without extension.
    private static Path compilationCacheFolder = null;
    private static final LinkedHashMap<String, byte[]> backing = new LinkedHashMap<>();

    public static boolean isCacheActive() {
        return compilationCacheFolder != null;
    }

    public static void deactivateCache() {
        compilationCacheFolder = null;
        backing.clear();
    }

    private static final Object enableCacheLockObj = new Object();

    public static void enableCache(Path compilationDirectory) {
        synchronized (enableCacheLockObj) {
            if (isCacheActive())
                throw new RuntimeException("Cache has already been set up!");

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

    public static byte[] getFromCacheOrNull(String javaSourceCode) {
        return backing.get(javaSourceCode);
    }

    public static void addToCache(String javaSourceCode, byte[] valueToCache) {
        backing.put(javaSourceCode, valueToCache);

        // do not store files >1MB, that way we can guarantee a maximum cache size via compilationCacheSize
        if (valueToCache.length < 1_000_000) {
            var serialized = new CacheEntry(javaSourceCode, valueToCache).serialize();
            var fileName = Base64.getEncoder()
                                   .encodeToString(getSha().digest(javaSourceCode.getBytes(StandardCharsets.UTF_8)))
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

        // TODO remove oldest files if we are running out of cache space (i.e. are storing too many files already)
    }
}
