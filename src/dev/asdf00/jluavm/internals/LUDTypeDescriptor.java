package dev.asdf00.jluavm.internals;

import dev.asdf00.jluavm.api.functions.AtomicLuaFunction;
import dev.asdf00.jluavm.api.lambdas.LLVaMultiFunction;
import dev.asdf00.jluavm.api.userdata.*;
import dev.asdf00.jluavm.exceptions.DelayedJavaCompilationException;
import dev.asdf00.jluavm.exceptions.InternalLuaRuntimeError;
import dev.asdf00.jluavm.exceptions.LuaJavaError;
import dev.asdf00.jluavm.exceptions.LuaUserDataApiBuildingException;
import dev.asdf00.jluavm.internals.javac.DelayedJavaCompiler;
import dev.asdf00.jluavm.internals.javac.LUDCompanionClassLoader;
import dev.asdf00.jluavm.runtime.types.LuaObject;
import dev.asdf00.jluavm.utils.ByteArrayReader;
import dev.asdf00.jluavm.utils.Tuple;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LUDTypeDescriptor<T extends LuaUserData> {
    private static final LUDCompanionClassLoader descLdr = new LUDCompanionClassLoader(LUDTypeDescriptor.class.getClassLoader());

    public final Class<T> type;

    private final BiFunction<LuaObject[], ByteArrayReader, T> deserializer;
    private final Map<String, LuaObject> methods;
    private final Map<String, Function<T, LuaObject>> getters;
    private final Map<String, BiConsumer<T, LuaObject>> setters;

    /**
     * This array is a hash table for fallback meta methods. {@link LUDTypeDescriptor#getTypeMeta(String)} calculates a
     * UNIQUE hash key for each of the keys in {@link LUDTypeDescriptor#META_KEYS}. This hash is used to index this
     * table without checking explicit equivalency since the user is expected to only look up valid meta keys.
     * As per the accompanying unit test, the max index is 26, requiring this array to be of length 27.
     */
    private final LuaObject[] metaFunctions;

    private LUDTypeDescriptor(Class<T> type, BiFunction<LuaObject[], ByteArrayReader, T> deserializer,
                              Map<String, LuaObject> methods,
                              Map<String, Function<T, LuaObject>> getters,
                              Map<String, BiConsumer<T, LuaObject>> setters) {
        this.type = type;
        this.deserializer = deserializer;
        this.methods = methods;
        this.getters = getters;
        this.setters = setters;
        metaFunctions = new LuaObject[27];
    }

    public T deserialize(LuaObject[] objs, ByteArrayReader reader) {
        return deserializer.apply(objs, reader);
    }

    public LuaObject get(LuaUserData obj, LuaObject key) throws LuaJavaError {
        if (key.isString()) {
            String sKey = key.asString();
            if (methods.containsKey(sKey)) {
                return methods.get(sKey);
            }
            if (getters.containsKey(sKey)) {
                return getters.get(sKey).apply((T) obj);
            }
            if (setters.containsKey(sKey)) {
                // this is a WRITE-ONLY variable for LUA, therefore we error
                throw new LuaJavaError("Attempting to read from a WRITE-ONLY property '%s'".formatted(sKey));
            }
        }
        // this is either not a string, or was not found in the type description, therefore we trigger a general get
        return obj.luaGeneralGet(key);
    }

    public boolean set(LuaUserData obj, LuaObject key, LuaObject value) {
        if (key.isString()) {
            String sKey = key.asString();
            if (setters.containsKey(sKey)) {
                // found property we can write to
                setters.get(sKey).accept((T) obj, value);
                return true;
                // this is a WRITE-ONLY variable for LUA, therefore we error
            }
            if (getters.containsKey(sKey)) {
                // this is a READ-ONLY variable for LUA, therefore we error
                throw new LuaJavaError("Attempting to write to a READ-ONLY property '%s'".formatted(sKey));
            }
            if (methods.containsKey(sKey)) {
                // LUA can never write to methods defined by userdata
                throw new LuaJavaError("Attempting to write to a userdata method '%s'".formatted(sKey));
            }
        }
        // this is either not a string, or was not found in the type description, therefore we trigger a general set
        return obj.luaGeneralSet(key, value);
    }

    /**
     * This method ASSUMES metaKey to be a VALID key for a meta table as per Lua 5.4 (see
     * {@link LUDTypeDescriptor#META_KEYS}).
     */
    public LuaObject getTypeMeta(String metaKey) {
        assert Arrays.stream(META_KEYS).filter(k -> k.equals(metaKey)).findAny().isPresent() : metaKey + " is not a valid meta table key!";
        return metaFunctions[hashMetaKey(metaKey)];
    }

    public String[] getReadableKeys() {
        return Stream.concat(getters.keySet().stream(), methods.keySet().stream()).toArray(String[]::new);
    }

    public String[] getWritableKeys() {
        return setters.keySet().toArray(String[]::new);
    }

    public static final String[] META_KEYS = new String[]{
            "__add",
            "__sub",
            "__mul",
            "__div",
            "__mod",
            "__pow",
            "__unm",
            "__idiv",
            "__band",
            "__bor",
            "__bxor",
            "__bnot",
            "__shl",
            "__shr",
            "__concat",
            "__len",
            "__eq",
            "__lt",
            "__le",
            "__index",
            "__newindex",
            "__call",
            "__name",
            "__gc",
            "__close",
            "__mode",
    };

    /**
     * This hash function returns a UNIQUE 5-bit key for all META_KEYS.
     */
    private static int hashMetaKey(String key) {
        int hash = key.charAt(3) * 37572;
        hash += key.charAt(key.length() - 1);
        hash += key.charAt(key.length() - 3) * 57780;
        hash = (hash ^ (hash >>> 11)) + hash + 29;
        return hash & 0b1_1111;
    }

    // =================================================================================================================
    // DESCRIPTOR BUILDING STUFF
    // =================================================================================================================

    public static <T extends LuaUserData> LUDTypeDescriptor<T> buildDescriptor(Class<T> type) {
        var companion = buildCompanion(type);
        try {
            var rawFuncs = (Map<String, LLVaMultiFunction>) companion.getField("functions").get(null);

            // TODO use purpose build registry for that stuff
            Map<String, LuaObject> funcs = rawFuncs.entrySet().stream()
                    .map(e -> new Tuple<>(e.getKey(), AtomicLuaFunction.vaForManyResults(null, e.getValue())))
                    .collect(Collectors.toMap(t -> t.x(), t -> LuaObject.of(t.y())));

            return new LUDTypeDescriptor<>(type,
                    (BiFunction<LuaObject[], ByteArrayReader, T>) companion.getField("deserializer").get(null),
                    funcs,
                    (Map<String, Function<T, LuaObject>>) companion.getField("getters").get(null),
                    (Map<String, BiConsumer<T, LuaObject>>) companion.getField("setters").get(null));
        } catch (ReflectiveOperationException e) {
            throw new InternalLuaRuntimeError("error reading companion class", e);
        }
    }

    private static <T extends LuaUserData> Class<?> buildCompanion(Class<T> type) {
        if (!LuaUserData.class.isAssignableFrom(type)) {
            // we kill the VM and it is our fault for somehow allowing a non-userdata type to leak into the Lua state
            throw new InternalLuaRuntimeError(type.getName() + " is not a LuaUserData type");
        }
        if (!Modifier.isPublic(type.getModifiers())) {
            // we kill the VM and it is our fault for somehow allowing a non-userdata type to leak into the Lua state
            throw new InternalLuaRuntimeError(type.getName() + " must be public");
        }

        // collect methods
        var builder = new CompanionClassBuilder(type);
        for (Method m : type.getMethods()) {
            if (m.getAnnotation(LuaDeserializer.class) != null) {
                // we found the DESERIALIZER, now we do sanity checks
                if (!Modifier.isStatic(m.getModifiers())) {
                    throw new LuaUserDataApiBuildingException("deserializer for '%s' must be static".formatted(type.getName()));
                }
                var params = m.getParameterTypes();
                if (params.length != 2 || !params[0].equals(LuaObject[].class) || !params[1].isAssignableFrom(ByteArrayReader.class)) {
                    throw new LuaUserDataApiBuildingException("deserializer for '%s' must be have parameters LuaObject[] and ByteArrayReader".formatted(type.getName()));
                }
                if (!type.isAssignableFrom(m.getReturnType())) {
                    throw new LuaUserDataApiBuildingException("deserializer for '%s' must return a type compatible with itself".formatted(type.getName()));
                }
                if (builder.deserializer != null) {
                    throw new LuaUserDataApiBuildingException("declared duplicate deserializers in '%s'".formatted(type.getName()));
                }
                builder.deserializer = m.getName();
                continue;
            }

            if (m.getAnnotation(LuaCallable.class) != null) {
                // we found a CALLABLE, now we do sanity checks and build stuff
                if (Modifier.isStatic(m.getModifiers())) {
                    throw new LuaUserDataApiBuildingException(
                            "only dynamically bound methods are supported for LuaUserData, static methods like '%s' should be wrapped manually using the Function API"
                                    .formatted(type.getName()));
                }
                builder.addFunLambda(m.getName(), m.getReturnType(), m.getParameterTypes());
                continue;
            }

            var annMeta = m.getAnnotation(LuaMetaFunction.class);
            if (annMeta != null) {
                // we found a meta method
                // TODO
                throw new UnsupportedOperationException("fallback meta calls are not supported yet");
            }
        }

        // collect fields
        for (Field f : type.getFields()) {
            var ann = f.getAnnotation(LuaExposed.class);

            if (LuaProperty.class.equals(f.getType())) {
                if (ann == null) {
                    // this is a property without an annotation, throw an error
                    throw new LuaUserDataApiBuildingException("LuaProperty '%s' in '%s' has to be annotated with @LuaExposed!".formatted(f.getName(), type.getName()));
                }
                if (!Modifier.isFinal(f.getModifiers())) {
                    // this is a non-final property, throw an error
                    throw new LuaUserDataApiBuildingException("LuaProperty '%s' in '%s' has to be final!".formatted(f.getName(), type.getName()));
                }
                if (Modifier.isStatic(f.getModifiers())) {
                    throw new LuaUserDataApiBuildingException("LuaProperty '%s' in '%s' must not be static!".formatted(f.getName(), type.getName()));
                }
            }

            if (ann != null) {
                if (Modifier.isStatic(f.getModifiers())) {
                    throw new LuaUserDataApiBuildingException("LuaExposed field '%s' in '%s' must not be static!".formatted(f.getName(), type.getName()));
                }
                switch (ann.value()) {
                    case READ -> builder.addGetter(f.getName(), f.getType());
                    case WRITE -> builder.addSetter(f.getName(), f.getType());
                    case READWRITE -> {
                        builder.addGetter(f.getName(), f.getType());
                        builder.addSetter(f.getName(), f.getType());
                    }
                }
            }
        }

        var jic = builder.build();
        try {
            return DelayedJavaCompiler.compileAndLoad(descLdr, jic.x(), jic.y());
        } catch (DelayedJavaCompilationException e) {
            throw new InternalLuaRuntimeError("error compiling userdata companion for " + type.getName(), e);
        }
    }

    private static String resolveTrueClassName(Class<?> clazz) {
        var name = clazz.getSimpleName();
        Class<?> outer = clazz;
        while ((outer = outer.getEnclosingClass()) != null) {
            name = outer.getSimpleName() + "." + name;
        }
        return clazz.getPackageName() + "." + name;
    }

    private static final Map<Class<?>, String> CONVERTIBLE_TYPES = Map.of(
            LuaObject.class, "",
            String.class, "lo2st",
            Byte.class, "lo2b",
            Short.class, "lo2s",
            Integer.class, "lo2i",
            Long.class, "lo2l",
            Float.class, "lo2f",
            Double.class, "lo2d",
            Boolean.class, "lo2z",
            Character.class, "lo2c"
    );

    public static void verifyAsLuaConvertible(boolean isRet, boolean isLastP, Class<?> type) {
        if (void.class.equals(type)) {
            if (isRet) {
                return;
            } else {
                throw new IllegalArgumentException("Unconvertable LuaJavaApi type VOID");
            }
        }
        if (CONVERTIBLE_TYPES.containsKey(type)) {
            return;
        }
        if (LuaUserData.class.isAssignableFrom(type)) {
            return;
        }
        if (LuaObject[].class.equals(type)) {
            if (isLastP || isRet) {
                return;
            } else {
                throw new IllegalArgumentException("A LuaObject[] parameter is only valid as a vararg at the end of the parameter list");
            }
        }
        throw new IllegalArgumentException("Unconvertable LuaJavaApi type " + type.getName());
    }

    private static class CompanionClassBuilder {
        private final String typeName;
        private final Class<? extends LuaUserData> clazz;

        public String deserializer = null;
        private final List<String> functionLambdas = new ArrayList<>();
        private final List<String> getterLambdas = new ArrayList<>();
        private final List<String> setterLambdas = new ArrayList<>();
        private final Map<String, Object> metas = new LinkedHashMap<>();
        private final Set<String> readable = new HashSet<>();
        private final Set<String> writable = new HashSet<>();

        public CompanionClassBuilder(Class<? extends LuaUserData> clazz) {
            this.typeName = resolveTrueClassName(clazz);
            this.clazz = clazz;
        }

        public static Class<?> boxThatType(Class<?> type) {
            if (Object.class.isAssignableFrom(type)) {
                return type;
            } else if (byte.class.equals(type)) {
                return Byte.class;
            } else if (short.class.equals(type)) {
                return Short.class;
            } else if (int.class.equals(type)) {
                return Integer.class;
            } else if (long.class.equals(type)) {
                return Long.class;
            } else if (float.class.equals(type)) {
                return Float.class;
            } else if (double.class.equals(type)) {
                return Double.class;
            } else if (boolean.class.equals(type)) {
                return Boolean.class;
            } else if (char.class.equals(type)) {
                return Character.class;
            } else if (void.class.equals(type)) {
                return type;
            }
            throw new IllegalArgumentException("Failed to box possibly primitive " + type.getName());
        }

        // =============================================================================================================
        // function addition
        public void addFunLambda(String name, Class<?> rType, Class<?>... pTypes) {
            if (!readable.add(name)) {
                throw new LuaUserDataApiBuildingException("Duplicate LUA readable element '%s' in %s".formatted(name, typeName));
            }

            // verify we can actually generate the call
            try {
                verifyAsLuaConvertible(true, false, boxThatType(rType));
                for (int i = 0; i < pTypes.length - 1; i++) {
                    pTypes[i] = boxThatType(pTypes[i]);
                    verifyAsLuaConvertible(false, false, pTypes[i]);
                }
                if (pTypes.length > 0) {
                    int last = pTypes.length - 1;
                    pTypes[last] = boxThatType(pTypes[last]);
                    verifyAsLuaConvertible(false, true, pTypes[last]);
                }
            } catch (IllegalArgumentException e) {
                throw new LuaUserDataApiBuildingException("Error building userdata '" + typeName + "#" + name + "': " + e.getMessage());
            }
            var sb = new StringBuilder();
            sb.append('"').append(name).append("\", (vm, params) -> {\n");

            // do required args check (the object instance is also required)
            int requiredArgs = pTypes.length;
            if (isVarargs(pTypes)) {
                // the last argument type is the params arg which is allowed to be empty
                requiredArgs--;
                assert requiredArgs >= 0;
                sb.append("""
                        if (params.length < %d) throw new LuaJavaError("expected userdata instance + at least %d argument%s (you may use the LUA method syntax), got " + params.length);
                        """.formatted(requiredArgs + 1, requiredArgs, requiredArgs != 1 ? "s" : ""));
            } else {
                assert requiredArgs >= 0;
                if (requiredArgs == 0) {
                    sb.append("""
                            if (params.length != %d) throw new LuaJavaError("expected userdata instance as the only argument (you may use the LUA method syntax), got " + params.length);
                            """.formatted(requiredArgs + 1, requiredArgs));
                } else {
                    sb.append("""
                            if (params.length != %d) throw new LuaJavaError("expected userdata instance + %d argument%s (you may use the LUA method syntax), got " + params.length);
                            """.formatted(requiredArgs + 1, requiredArgs, requiredArgs > 1 ? "s" : ""));
                }
            }

            // do the call
            sb.append("""
                    %s dyn = lo2ud(%s.class, params[0]);
                    if (dyn == null) throw new LuaJavaError("userdata object required as first argument (you may use the LUA method syntax)");
                    """.formatted(typeName, typeName));
            if (void.class.equals(rType)) {
                // void return just returns an empty array
                makeCall(sb, name, pTypes).append(";\nreturn Singletons.EMPTY_LUA_OBJ_ARRAY;\n");
            } else if (LuaObject[].class.equals(rType)) {
                // multi-return is handled by the target
                sb.append("return ");
                makeCall(sb, name, pTypes).append(";\n");
            } else {
                // single returns are wrapped accordingly
                sb.append("return new LuaObject[]{");
                if (!LuaObject.class.equals(rType)) {
                    sb.append("LuaObject.of");
                }
                sb.append('(');
                makeCall(sb, name, pTypes).append(")};\n");
            }

            // closing brace for lambda
            sb.append('}');

            functionLambdas.add(sb.toString());
        }

        private StringBuilder makeCall(StringBuilder sb, String name, Class<?>... pTypes) {
            sb.append("dyn");
            sb.append('.').append(name).append('(');
            int limit = isVarargs(pTypes) ? pTypes.length - 1 : pTypes.length;
            int dynOffset = 1;
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                if (LuaUserData.class.isAssignableFrom(pTypes[i])) {
                    sb.append("lo2ud(").append(pTypes[i].getName()).append(".class, ");
                } else {
                    sb.append(CONVERTIBLE_TYPES.get(pTypes[i])).append('(');
                }
                sb.append("params[").append(i + dynOffset).append("])");
            }
            if (isVarargs(pTypes)) {
                if (limit > 0) {
                    sb.append(", ");
                }
                int varargsStart = pTypes.length;
                sb.append("Arrays.copyOfRange(params, ").append(varargsStart).append(", params.length)");
            }
            sb.append(')');
            return sb;
        }

        private static boolean isVarargs(Class<?>... pTypes) {
            return pTypes.length > 0 && LuaObject[].class.equals(pTypes[pTypes.length - 1]);
        }

        // =============================================================================================================
        // getter addition
        public void addGetter(String name, Class<?> fType) {
            if (!readable.add(name)) {
                throw new LuaUserDataApiBuildingException("Duplicate LUA readable element '%s' in %s".formatted(name, typeName));
            }
            fType = boxThatType(fType);

            var sb = new StringBuilder();
            sb.append('"').append(name).append("\", ud -> ");

            if (LuaProperty.class.equals(fType)) {
                sb.append("ud.").append(name).append(".get()");
            } else if (LuaObject.class.equals(fType)) {
                sb.append("ud.").append(name);
            } else {
                if (!LuaUserData.class.isAssignableFrom(fType)) {
                    verifyAsLuaConvertible(false, false, fType);
                }
                sb.append("LuaObject.of(ud.").append(name).append(')');
            }

            getterLambdas.add(sb.toString());
        }

        // =============================================================================================================
        // setter addition
        public void addSetter(String name, Class<?> fType) {
            if (!writable.add(name)) {
                throw new LuaUserDataApiBuildingException("Duplicate LUA writable element '%s' in %s".formatted(name, typeName));
            }
            fType = boxThatType(fType);

            var sb = new StringBuilder();
            sb.append('"').append(name).append("\", (ud, val) -> ");

            if (LuaProperty.class.equals(fType)) {
                sb.append("ud.").append(name).append(".set(val)");
            } else if (LuaObject.class.equals(fType)) {
                sb.append("ud.").append(name).append(" = val");
            } else {
                sb.append("ud.").append(name).append(" = ");
                if (LuaUserData.class.isAssignableFrom(fType)) {
                    sb.append("lo2ud(").append(fType.getName()).append(".class, val)");
                } else {
                    verifyAsLuaConvertible(false, false, fType);
                    sb.append(CONVERTIBLE_TYPES.get(fType)).append("(val)");
                }
            }

            setterLambdas.add(sb.toString());
        }

        public Tuple<String, String> build() {
            if (deserializer == null) {
                throw new LuaUserDataApiBuildingException("missing deserializer for " + typeName);
            }

            String nuClName = "LUDCompanion$" + typeName.replace("$", "$$").replace(".", "$_");

            return new Tuple<>(clazz.getPackageName() + "." + nuClName, """
                    package %s;
                    
                    import dev.asdf00.jluavm.api.lambdas.*;
                    import dev.asdf00.jluavm.api.userdata.LuaUserData;
                    import dev.asdf00.jluavm.exceptions.LuaJavaError;
                    import dev.asdf00.jluavm.runtime.types.LuaObject;
                    import dev.asdf00.jluavm.runtime.utils.Singletons;
                    import dev.asdf00.jluavm.utils.ByteArrayReader;
                    
                    import java.util.Arrays;
                    import java.util.Map;
                    import java.util.function.*;
                    
                    import static dev.asdf00.jluavm.runtime.utils.UDTranslators.*;
                    
                    public class %s {
                    public static final BiFunction<LuaObject[], ByteArrayReader, LuaUserData> deserializer = %s::%s;
                    
                    public static final Map<String, LLVaMultiFunction> functions = Map.of(
                    %s
                    );
                    
                    public static final Map<String, Function<%s, LuaObject>> getters = Map.of(
                    %s
                    );
                    
                    public static final Map<String, BiConsumer<%s, LuaObject>> setters = Map.of(
                    %s
                    );
                    
                    // TODO add meta fallbacks
                    }
                    """.formatted(
                    clazz.getPackageName(),
                    nuClName,
                    typeName, deserializer,
                    String.join(",\n", functionLambdas),
                    typeName, String.join(",\n", getterLambdas),
                    typeName, String.join(",\n", setterLambdas))
            );
        }
    }
}
