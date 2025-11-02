package dev.asdf00.jluavm.api.userdata;

import dev.asdf00.jluavm.runtime.types.LuaObject;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.asdf00.jluavm.runtime.utils.UDTranslators.*;

public abstract sealed class LuaProperty {
    public static LuaProperty ofByte(ByteSupplier get, ByteConsumer set) {
        return new LByte(get, set);
    }

    public static LuaProperty ofShort(ShortSupplier get, ShortConsumer set) {
        return new LShort(get, set);
    }

    public static LuaProperty ofInt(IntSupplier get, IntConsumer set) {
        return new LInt(get, set);
    }

    public static LuaProperty ofLong(LongSupplier get, LongConsumer set) {
        return new LLong(get, set);
    }

    public static LuaProperty ofFloat(FloatSupplier get, FloatConsumer set) {
        return new LFloat(get, set);
    }

    public static LuaProperty ofDouble(DoubleSupplier get, DoubleConsumer set) {
        return new LDouble(get, set);
    }

    public static LuaProperty ofBoolean(BooleanSupplier get, BooleanConsumer set) {
        return new LBoolean(get, set);
    }

    public static LuaProperty ofChar(CharSupplier get, CharConsumer set) {
        return new LChar(get, set);
    }

    public static LuaProperty ofString(Supplier<String> get, Consumer<String> set) {
        return new LString(get, set);
    }

    public static LuaProperty ofObject(Supplier<LuaObject> get, Consumer<LuaObject> set) {
        return new LObject(get, set);
    }

    public static <T extends LuaUserData> LuaProperty ofUserData(Class<T> type, Supplier<T> get, Consumer<T> set) {
        return new LUserData<>(type, get, set);
    }

    public abstract LuaObject get();
    public abstract void set(LuaObject value);

    @FunctionalInterface
    public interface ByteConsumer {
        void accept(byte v);
    }

    @FunctionalInterface
    public interface ShortConsumer {
        void accept(short v);
    }

    @FunctionalInterface
    public interface IntConsumer {
        void accept(int v);
    }

    @FunctionalInterface
    public interface LongConsumer {
        void accept(long v);
    }

    @FunctionalInterface
    public interface FloatConsumer {
        void accept(float v);
    }

    @FunctionalInterface
    public interface DoubleConsumer {
        void accept(double v);
    }

    @FunctionalInterface
    public interface BooleanConsumer {
        void accept(boolean v);
    }

    @FunctionalInterface
    public interface CharConsumer {
        void accept(char v);
    }

    @FunctionalInterface
    public interface ByteSupplier {
        byte get();
    }

    @FunctionalInterface
    public interface ShortSupplier {
        short get();
    }

    @FunctionalInterface
    public interface IntSupplier {
        int get();
    }

    @FunctionalInterface
    public interface LongSupplier {
        long get();
    }

    @FunctionalInterface
    public interface FloatSupplier {
        float get();
    }

    @FunctionalInterface
    public interface DoubleSupplier {
        double get();
    }

    @FunctionalInterface
    public interface BooleanSupplier {
        boolean get();
    }

    @FunctionalInterface
    public interface CharSupplier {
        char get();
    }

    private static final class LByte extends LuaProperty {
        private final ByteSupplier getter;
        private final ByteConsumer setter;

        public LByte(ByteSupplier getter, ByteConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2b(value));
        }
    }

    private static final class LShort extends LuaProperty {
        private final ShortSupplier getter;
        private final ShortConsumer setter;

        public LShort(ShortSupplier getter, ShortConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2s(value));
        }
    }

    private static final class LInt extends LuaProperty {
        private final IntSupplier getter;
        private final IntConsumer setter;

        public LInt(IntSupplier getter, IntConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2i(value));
        }
    }

    private static final class LLong extends LuaProperty {
        private final LongSupplier getter;
        private final LongConsumer setter;

        public LLong(LongSupplier getter, LongConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2l(value));
        }
    }

    private static final class LFloat extends LuaProperty {
        private final FloatSupplier getter;
        private final FloatConsumer setter;

        public LFloat(FloatSupplier getter, FloatConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2f(value));
        }
    }

    private static final class LDouble extends LuaProperty {
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;

        public LDouble(DoubleSupplier getter, DoubleConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2d(value));
        }
    }

    private static final class LBoolean extends LuaProperty {
        private final BooleanSupplier getter;
        private final BooleanConsumer setter;

        public LBoolean(BooleanSupplier getter, BooleanConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2z(value));
        }
    }

    private static final class LChar extends LuaProperty {
        private final CharSupplier getter;
        private final CharConsumer setter;

        public LChar(CharSupplier getter, CharConsumer setter) {
            this.getter = getter;
            this.setter = setter;
        }


        @Override
        public LuaObject get() {
            return LuaObject.of(getter.get());
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2c(value));
        }
    }

    private static final class LString extends LuaProperty {
        private final Supplier<String> getter;
        private final Consumer<String> setter;

        public LString(Supplier<String> getter, Consumer<String> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public LuaObject get() {
            String val = getter.get();
            return val == null ? LuaObject.nil() : LuaObject.of(val);
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2st(value));
        }
    }

    private static final class LObject extends LuaProperty {
        private final Supplier<LuaObject> getter;
        private final Consumer<LuaObject> setter;

        public LObject(Supplier<LuaObject> getter, Consumer<LuaObject> setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public LuaObject get() {
            LuaObject obj = getter.get();
            return obj == null ? LuaObject.nil() : obj;
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(value);
        }
    }

    private static final class LUserData<T extends LuaUserData> extends LuaProperty {
        private final Class<T> type;
        private final Supplier<T> getter;
        private final Consumer<T> setter;

        public LUserData(Class<T> type, Supplier<T> getter, Consumer<T> setter) {
            this.type = type;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public LuaObject get() {
            T obj = getter.get();
            return obj == null ? LuaObject.nil() : LuaObject.of(obj);
        }

        @Override
        public void set(LuaObject value) {
            setter.accept(lo2ud(type, value));
        }
    }
}
